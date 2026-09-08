from __future__ import annotations

import shutil
import threading
from io import BytesIO
from pathlib import Path
from uuid import uuid4

from bson import ObjectId
from fastapi import HTTPException, UploadFile, status
from PIL import Image
from pymongo.database import Database

from app.config import Settings
from app.repositories import reconstruction_repository as repo
from app.services.image_storage import validate_image_bytes
from app.services.model3d import colmap_pipeline as pipeline
from app.services.model3d import colmap_service, states
from app.services.model3d.image_quality import assess_images
from app.utils import utc_now

# Product minimum: this is a best-effort preview feature, not professional photogrammetry.
# Three overlapping photos are allowed to ATTEMPT a reconstruction - COLMAP's own sparse
# quality gate (see MIN_USABLE_* below) decides whether that attempt actually produced
# anything usable, not this floor.
ABSOLUTE_MIN_IMAGES_FOR_SPARSE = 3

# Lightweight sanity gate replacing the old fixed registered-image-ratio requirement: a preview
# only needs *some* real geometry, not a complete/high-ratio scan. At least 2 posed images are
# required for any 3D structure to exist at all; the point-count floor rejects a degenerate
# near-empty cloud. Completeness beyond this is informational and left to the admin's
# Accept/Reject review, not enforced here.
MIN_USABLE_REGISTERED_IMAGES = 2
MIN_USABLE_SPARSE_POINTS = 10

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


def _write_source_image(path: Path, data: bytes, *, max_dimension: int) -> tuple[int, int]:
    """Writes a working copy of an image for COLMAP, never the administrator's original file.

    Two adjustments versus the original bytes, both driven by real hardware limits observed
    on an 8GB/4-core CPU-only test machine:

    - EXIF stripped entirely: real phone photos here carry EXIF orientation=0, which is not a
      valid value (1-8) and crashes COLMAP 4.2's EXIF-based gravity/pose-prior parsing during
      feature extraction. Pixel data is already stored in the visually correct orientation
      (portrait shots are literally portrait-dimensioned), so no rotation is lost by dropping it.
    - Downscaled to at most `max_dimension` on the longest side (aspect ratio preserved):
      feeding full-resolution (e.g. 4080x3060) originals into COLMAP's own decode+resize step
      still requires holding the full-resolution image in memory first, which is what exhausted
      RAM on that machine. Pre-downscaling the working copy avoids that peak.
    """
    with Image.open(BytesIO(data)) as image:
        original_format = image.format
        width, height = image.size
        longest_side = max(width, height)
        if max_dimension and longest_side > max_dimension:
            scale = max_dimension / float(longest_side)
            width, height = max(1, round(width * scale)), max(1, round(height * scale))
            image = image.resize((width, height), Image.LANCZOS)
        save_kwargs = {"quality": 95} if original_format == "JPEG" else {}
        image.save(path, format=original_format, **save_kwargs)
    return width, height


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
    draft_created_at = state.get("draft_created_at")
    return {
        **state,
        "created_at": created_at.isoformat() if hasattr(created_at, "isoformat") else created_at,
        "draft_created_at": draft_created_at.isoformat() if hasattr(draft_created_at, "isoformat") else draft_created_at,
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
        width, height = _write_source_image(source_dir / filename, data, max_dimension=settings.model_3d_max_input_dimension)

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
        width, height = _write_source_image(source_dir / filename, data, max_dimension=settings.model_3d_max_input_dimension)

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
            f"Minimum: {ABSOLUTE_MIN_IMAGES_FOR_SPARSE} overlapping photos "
            f"({len(images)} currently added).",
            "Additional angles may improve the preview - aim for {} or more where possible.".format(
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
                num_threads=settings.model_3d_cpu_threads,
                max_features=settings.model_3d_max_features,
                log_file=log_file,
            )
            pipeline.run_matching(
                bin_path,
                database_path=database_path,
                use_gpu=settings.colmap_use_gpu,
                num_threads=settings.model_3d_cpu_threads,
                log_file=log_file,
            )
            pipeline.run_mapper(bin_path, database_path=database_path, image_path=source_dir, sparse_path=sparse_path, log_file=log_file)

            sparse_model_path = sparse_path / "0"
            if not sparse_model_path.is_dir():
                guidance = [
                    "Unable to create a usable 3D preview from these photographs.",
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
    # Lightweight sanity gate, not a quality gate: this is a best-effort preview, so any sparse
    # reconstruction with enough real geometry to mesh is allowed through to admin review.
    # registered_image_ratio remains informational (shown to the admin) rather than enforced -
    # a 3-photo attempt that registers only 2 images can still reach a usable partial preview.
    ready = registered >= MIN_USABLE_REGISTERED_IMAGES and (stats.sparse_point_count or 0) >= MIN_USABLE_SPARSE_POINTS

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
    # A fresh build supersedes any unreviewed draft from a previous attempt; the previously
    # *accepted* (published) model above is untouched and stays visible to visitors throughout.
    repo.update_model_3d_state(
        database, artifact_id,
        {
            "status": states.QUEUED,
            "failure_message": None,
            "draft_version": None,
            "draft_path": None,
            "draft_sha256": None,
            "draft_size_bytes": None,
            "draft_created_at": None,
        },
    )

    starter = _worker_thread_starter or _default_worker_starter
    starter(database, settings, artifact_id, job["_id"])
    return job


def accept_draft(database: Database, settings: Settings, artifact: dict) -> None:
    """Publishes the pending draft model: visitor API exposes it from this point on."""
    artifact_id: ObjectId = artifact["_id"]
    state = repo.get_model_3d_state(artifact)
    if state["status"] != states.PENDING_REVIEW or not state.get("draft_path"):
        raise ReconstructionError(status_code=status.HTTP_409_CONFLICT, detail="No pending 3D preview to accept.")

    repo.update_model_3d_state(
        database, artifact_id,
        {
            "status": states.READY,
            "version": state["draft_version"],
            "path": state["draft_path"],
            "sha256": state["draft_sha256"],
            "size_bytes": state["draft_size_bytes"],
            "created_at": state["draft_created_at"],
            "draft_version": None,
            "draft_path": None,
            "draft_sha256": None,
            "draft_size_bytes": None,
            "draft_created_at": None,
            "failure_message": None,
        },
    )


def reject_draft(database: Database, settings: Settings, artifact: dict) -> None:
    """Discards the pending draft model. The visitor never saw it; any previously accepted
    model (if one exists) remains published and untouched."""
    artifact_id: ObjectId = artifact["_id"]
    state = repo.get_model_3d_state(artifact)
    if state["status"] != states.PENDING_REVIEW:
        raise ReconstructionError(status_code=status.HTTP_409_CONFLICT, detail="No pending 3D preview to reject.")

    draft_path = state.get("draft_path")
    if draft_path:
        full_path = settings.model_3d_root_path.parent.parent / draft_path
        if full_path.is_file():
            full_path.unlink()

    has_published_model = bool(state.get("path")) and int(state.get("version") or 0) > 0
    next_status = states.READY if has_published_model else states.NEEDS_IMAGES
    repo.update_model_3d_state(
        database, artifact_id,
        {
            "status": next_status,
            "draft_version": None,
            "draft_path": None,
            "draft_sha256": None,
            "draft_size_bytes": None,
            "draft_created_at": None,
            "failure_message": None,
        },
    )


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
