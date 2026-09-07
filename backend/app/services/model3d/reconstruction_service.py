from __future__ import annotations

import shutil
import threading
from pathlib import Path
from uuid import uuid4

from bson import ObjectId
from fastapi import HTTPException, UploadFile, status
from pymongo.database import Database

from app.config import Settings
from app.repositories import reconstruction_repository as repo
from app.services.image_storage import validate_image_bytes
from app.services.model3d import colmap_pipeline as pipeline
from app.services.model3d import colmap_service, states
from app.services.model3d.image_quality import assess_images
from app.utils import utc_now

# Below this, running COLMAP at all is not worth the machine time - the admin needs to add
# photos first. This is intentionally lower than MODEL_3D_MIN_SOURCE_IMAGES (a "nice to have"
# heuristic); it only prevents wasting a sparse reconstruction attempt on a hopeless input set.
ABSOLUTE_MIN_IMAGES_FOR_SPARSE = 5

ACTIVE_JOB_CONFLICT_MESSAGE = "A reconstruction job is already running. Wait for it to finish before starting another."


class ReconstructionError(HTTPException):
    pass


def _dirs(settings: Settings, artifact_id: str) -> dict[str, Path]:
    root = settings.reconstruction_root_path / artifact_id
    return {"root": root, "source": root / "source", "workspace": root / "workspace", "output": root / "output"}


def _ensure_source_dir(settings: Settings, artifact_id: str) -> Path:
    dirs = _dirs(settings, artifact_id)
    dirs["source"].mkdir(parents=True, exist_ok=True)
    return dirs["source"]


def serialize_image(document: dict) -> dict:
    return {
        "id": str(document["_id"]),
        "origin": document.get("origin"),
        "original_filename": document.get("original_filename"),
        "width": document.get("width"),
        "height": document.get("height"),
        "created_at": document.get("created_at").isoformat() if document.get("created_at") else None,
    }


def get_state(database: Database, artifact: dict) -> dict:
    artifact_id = artifact["_id"]
    state = repo.get_model_3d_state(artifact)
    images = repo.list_reconstruction_images(database, artifact_id)
    active_job = repo.find_active_job_for_artifact(database, artifact_id)
    created_at = state.get("created_at")
    return {
        **state,
        "created_at": created_at.isoformat() if hasattr(created_at, "isoformat") else created_at,
        "source_image_count": len(images),
        "images": [serialize_image(image) for image in images],
        "active_job_id": str(active_job["_id"]) if active_job else None,
    }


def add_images(
    database: Database,
    settings: Settings,
    artifact: dict,
    *,
    reuse_image_paths: list[str],
    uploads: list[UploadFile],
) -> list[dict]:
    artifact_id: ObjectId = artifact["_id"]
    artifact_id_str = str(artifact_id)
    if repo.find_active_job_for_artifact(database, artifact_id):
        raise ReconstructionError(status_code=status.HTTP_409_CONFLICT, detail=ACTIVE_JOB_CONFLICT_MESSAGE)

    source_dir = _ensure_source_dir(settings, artifact_id_str)
    created: list[dict] = []
    artifact_image_paths = set(artifact.get("image_paths") or [])

    for image_path in reuse_image_paths:
        if image_path not in artifact_image_paths:
            raise ReconstructionError(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail="Selected image does not belong to this artifact.",
            )
        # Mirrors image_storage.safe_delete_image(): resolve by filename against the configured
        # upload directory rather than treating the stored path as a literal filesystem path.
        filename = Path(image_path).name
        original_full_path = (settings.upload_path / filename).resolve()
        try:
            original_full_path.relative_to(settings.upload_path.resolve())
        except ValueError:
            raise ReconstructionError(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Invalid image path.")
        if not original_full_path.is_file():
            raise ReconstructionError(status_code=status.HTTP_404_NOT_FOUND, detail="Source image file was not found.")

        data = original_full_path.read_bytes()
        extension, digest = validate_image_bytes(data, settings)
        if repo.image_digest_exists(database, artifact_id, digest):
            continue

        filename = f"{uuid4().hex}{extension}"
        (source_dir / filename).write_bytes(data)
        from PIL import Image

        with Image.open(source_dir / filename) as opened:
            width, height = opened.size

        created.append(
            repo.add_reconstruction_image(
                database,
                artifact_id=artifact_id,
                relative_path=f"{artifact_id_str}/source/{filename}",
                origin="reused",
                original_filename=Path(image_path).name,
                width=width,
                height=height,
                digest=digest,
            )
        )

    for upload in uploads:
        data = upload.file.read()
        if not data:
            continue
        extension, digest = validate_image_bytes(data, settings, content_type=upload.content_type)
        if repo.image_digest_exists(database, artifact_id, digest):
            continue

        filename = f"{uuid4().hex}{extension}"
        (source_dir / filename).write_bytes(data)
        from PIL import Image

        with Image.open(source_dir / filename) as opened:
            width, height = opened.size

        created.append(
            repo.add_reconstruction_image(
                database,
                artifact_id=artifact_id,
                relative_path=f"{artifact_id_str}/source/{filename}",
                origin="uploaded",
                original_filename=upload.filename,
                width=width,
                height=height,
                digest=digest,
            )
        )

    if created:
        current = repo.get_model_3d_state(artifact)
        total = len(repo.list_reconstruction_images(database, artifact_id))
        if current["status"] == states.NONE:
            repo.update_model_3d_state(database, artifact_id, {"status": states.NEEDS_IMAGES, "source_image_count": total})
        else:
            repo.update_model_3d_state(database, artifact_id, {"source_image_count": total})

    return created


def remove_image(database: Database, settings: Settings, artifact_id: ObjectId, image_id: ObjectId) -> None:
    if repo.find_active_job_for_artifact(database, artifact_id):
        raise ReconstructionError(status_code=status.HTTP_409_CONFLICT, detail=ACTIVE_JOB_CONFLICT_MESSAGE)

    document = repo.delete_reconstruction_image(database, artifact_id, image_id)
    if document is None:
        raise ReconstructionError(status_code=status.HTTP_404_NOT_FOUND, detail="Reconstruction image was not found.")

    full_path = settings.reconstruction_root_path / document["relative_path"]
    if full_path.is_file():
        full_path.unlink()

    remaining = len(repo.list_reconstruction_images(database, artifact_id))
    from app.repositories import artifact_repository

    artifact = artifact_repository.get_artifact(database, artifact_id)
    if artifact is not None:
        current_status = repo.get_model_3d_state(artifact)["status"]
        if current_status in (states.NONE, states.NEEDS_IMAGES, states.READY_FOR_BUILD):
            next_status = states.NEEDS_IMAGES if remaining else states.NONE
            repo.update_model_3d_state(database, artifact_id, {"status": next_status, "source_image_count": remaining})
        else:
            repo.update_model_3d_state(database, artifact_id, {"source_image_count": remaining})


def delete_all(database: Database, settings: Settings, artifact_id: ObjectId) -> None:
    if repo.find_active_job_for_artifact(database, artifact_id):
        raise ReconstructionError(status_code=status.HTTP_409_CONFLICT, detail=ACTIVE_JOB_CONFLICT_MESSAGE)

    artifact_id_str = str(artifact_id)
    repo.delete_all_reconstruction_images(database, artifact_id)

    reconstruction_root = _dirs(settings, artifact_id_str)["root"]
    if reconstruction_root.exists():
        shutil.rmtree(reconstruction_root)

    model_dir = settings.model_3d_root_path / artifact_id_str
    if model_dir.exists():
        shutil.rmtree(model_dir)

    repo.update_model_3d_state(database, artifact_id, dict(repo.DEFAULT_MODEL_3D_STATE))


def run_preflight(database: Database, settings: Settings, artifact: dict) -> dict:
    artifact_id: ObjectId = artifact["_id"]
    artifact_id_str = str(artifact_id)
    if repo.find_active_job_for_artifact(database, artifact_id):
        raise ReconstructionError(status_code=status.HTTP_409_CONFLICT, detail=ACTIVE_JOB_CONFLICT_MESSAGE)

    images = repo.list_reconstruction_images(database, artifact_id)
    availability = colmap_service.detect_colmap(settings)

    if len(images) < ABSOLUTE_MIN_IMAGES_FOR_SPARSE:
        guidance = [
            f"Add at least {ABSOLUTE_MIN_IMAGES_FOR_SPARSE} photographs before running a check "
            f"({len(images)} currently added).",
            "Aim for {} or more overlapping photographs for a reliable reconstruction.".format(
                settings.model_3d_min_source_images
            ),
        ]
        repo.update_model_3d_state(
            database, artifact_id,
            {"status": states.NEEDS_IMAGES, "source_image_count": len(images), "guidance": guidance, "failure_message": None},
        )
        return get_state(database, artifact_repository_get(database, artifact_id))

    source_dir = _dirs(settings, artifact_id_str)["source"]
    quality = assess_images([source_dir / image["relative_path"].rsplit("/", 1)[-1] for image in images])

    if not availability.available or not availability.bin_path:
        guidance = ["COLMAP is not installed on this backend. Install COLMAP or set COLMAP_BIN to check reconstruction quality."]
        guidance += quality.guidance
        repo.update_model_3d_state(
            database, artifact_id,
            {"source_image_count": len(images), "guidance": guidance, "failure_message": None},
        )
        state = get_state(database, artifact_repository_get(database, artifact_id))
        state["colmap_available"] = False
        return state

    workspace = _dirs(settings, artifact_id_str)["workspace"] / "preflight"
    if workspace.exists():
        shutil.rmtree(workspace)
    workspace.mkdir(parents=True, exist_ok=True)
    log_path = workspace / "preflight.log"

    database_path = workspace / "database.db"
    sparse_path = workspace / "sparse"
    bin_path = availability.bin_path

    try:
        with log_path.open("w", encoding="utf-8") as log_file:
            pipeline.run_feature_extraction(
                bin_path,
                database_path=database_path,
                image_path=source_dir,
                use_gpu=settings.colmap_use_gpu,
                max_dimension=settings.model_3d_max_input_dimension,
                log_file=log_file,
            )
            pipeline.run_matching(bin_path, database_path=database_path, use_gpu=settings.colmap_use_gpu, log_file=log_file)
            pipeline.run_mapper(bin_path, database_path=database_path, image_path=source_dir, sparse_path=sparse_path, log_file=log_file)

            sparse_model_path = sparse_path / "0"
            if not sparse_model_path.is_dir():
                guidance = [
                    "COLMAP could not register enough photographs to build even a sparse model.",
                    *_default_sparse_guidance(),
                ]
                repo.update_model_3d_state(
                    database, artifact_id,
                    {
                        "status": states.NEEDS_IMAGES,
                        "source_image_count": len(images),
                        "registered_image_count": 0,
                        "registered_image_ratio": 0.0,
                        "sparse_point_count": 0,
                        "guidance": guidance + quality.guidance,
                        "failure_message": None,
                    },
                )
                state = get_state(database, artifact_repository_get(database, artifact_id))
                state["colmap_available"] = True
                return state

            stats = pipeline.analyze_sparse_model(bin_path, sparse_model_path=sparse_model_path, log_file=log_file)
    except pipeline.ColmapStageError as exc:
        repo.update_model_3d_state(
            database, artifact_id,
            {
                "source_image_count": len(images),
                "guidance": [f"COLMAP preflight failed during {exc.stage}."] + quality.guidance,
                "failure_message": None,
            },
        )
        state = get_state(database, artifact_repository_get(database, artifact_id))
        state["colmap_available"] = True
        return state

    registered = stats.registered_image_count or 0
    ratio = (registered / len(images)) if images else 0.0
    ready = ratio >= settings.model_3d_min_registered_ratio and bool(stats.sparse_point_count)

    guidance: list[str] = list(quality.guidance)
    if not ready:
        guidance = _default_sparse_guidance() + guidance

    repo.update_model_3d_state(
        database, artifact_id,
        {
            "status": states.READY_FOR_BUILD if ready else states.NEEDS_IMAGES,
            "source_image_count": len(images),
            "registered_image_count": registered,
            "registered_image_ratio": round(ratio, 4),
            "sparse_point_count": stats.sparse_point_count,
            "mean_reprojection_error": stats.mean_reprojection_error,
            "guidance": guidance,
            "failure_message": None,
        },
    )
    state = get_state(database, artifact_repository_get(database, artifact_id))
    state["colmap_available"] = True
    return state


def _default_sparse_guidance() -> list[str]:
    return [
        "Add more photographs with overlapping viewpoints between shots.",
        "Capture intermediate angles between existing photos.",
        "Keep the whole artifact visible in each photo.",
        "Add photographs covering any side that seems under-represented in the photo set.",
    ]


def artifact_repository_get(database: Database, artifact_id: ObjectId) -> dict:
    from app.repositories import artifact_repository

    artifact = artifact_repository.get_artifact(database, artifact_id)
    if artifact is None:
        raise ReconstructionError(status_code=status.HTTP_404_NOT_FOUND, detail="Artifact was not found.")
    return artifact


_worker_thread_starter = None


def _default_worker_starter(database: Database, settings: Settings, artifact_id: ObjectId, job_id: ObjectId) -> None:
    from app.services.model3d import reconstruction_worker

    thread = threading.Thread(
        target=reconstruction_worker.run,
        args=(database, settings, artifact_id, job_id),
        daemon=True,
        name=f"reconstruction-{artifact_id}",
    )
    thread.start()


def set_worker_starter(starter) -> None:
    """Test hook: replace the background-thread launcher with a synchronous call so tests can
    run the whole pipeline deterministically with COLMAP mocked, instead of racing a real
    thread."""
    global _worker_thread_starter
    _worker_thread_starter = starter


def start_build(database: Database, settings: Settings, artifact: dict) -> dict:
    artifact_id: ObjectId = artifact["_id"]
    if repo.find_active_job(database):
        raise ReconstructionError(status_code=status.HTTP_409_CONFLICT, detail=ACTIVE_JOB_CONFLICT_MESSAGE)

    state = repo.get_model_3d_state(artifact)
    if state["status"] not in states.BUILDABLE_STATES:
        raise ReconstructionError(
            status_code=status.HTTP_409_CONFLICT,
            detail="Run a successful preflight check before building a 3D model.",
        )

    images = repo.list_reconstruction_images(database, artifact_id)
    if len(images) < ABSOLUTE_MIN_IMAGES_FOR_SPARSE:
        raise ReconstructionError(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Not enough reconstruction photos to build a model.")

    target_version = int(state.get("version") or 0) + 1
    job = repo.create_job(database, artifact_id=artifact_id, target_version=target_version, source_image_count=len(images))
    repo.update_model_3d_state(database, artifact_id, {"status": states.QUEUED, "failure_message": None})

    starter = _worker_thread_starter or _default_worker_starter
    starter(database, settings, artifact_id, job["_id"])
    return job


def get_job_status(database: Database, artifact_id: ObjectId) -> dict | None:
    job = repo.get_latest_job_for_artifact(database, artifact_id)
    if job is None:
        return None
    return {
        "id": str(job["_id"]),
        "status": job["status"],
        "stage_message": job.get("stage_message"),
        "target_version": job.get("target_version"),
        "source_image_count": job.get("source_image_count"),
        "registered_image_count": job.get("registered_image_count"),
        "registered_image_ratio": job.get("registered_image_ratio"),
        "sparse_point_count": job.get("sparse_point_count"),
        "mean_reprojection_error": job.get("mean_reprojection_error"),
        "error": job.get("error"),
        "created_at": job["created_at"].isoformat() if job.get("created_at") else None,
        "updated_at": job["updated_at"].isoformat() if job.get("updated_at") else None,
        "started_at": job["started_at"].isoformat() if job.get("started_at") else None,
        "finished_at": job["finished_at"].isoformat() if job.get("finished_at") else None,
    }
