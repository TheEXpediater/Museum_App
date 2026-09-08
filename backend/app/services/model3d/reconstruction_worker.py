from __future__ import annotations

import logging
import shutil
from pathlib import Path

from bson import ObjectId
from pymongo.database import Database

from app.config import Settings
from app.repositories import reconstruction_repository as repo
from app.services.model3d import colmap_pipeline as pipeline
from app.services.model3d import colmap_service, states
from app.services.model3d.glb_converter import GlbConversionError, convert_to_glb
from app.services.model3d.reconstruction_service import MIN_USABLE_REGISTERED_IMAGES, MIN_USABLE_SPARSE_POINTS
from app.utils import utc_now

logger = logging.getLogger(__name__)

SPARSE_GUIDANCE = [
    "Add more photographs with overlapping viewpoints between shots.",
    "Capture intermediate angles between existing photos so COLMAP can match consecutive views.",
    "Keep the whole artifact visible in each photo; avoid tight crops that cut off edges.",
    "Avoid motion blur - use a steady grip or a faster shutter speed.",
    "Keep lighting consistent across the photo set and reduce strong reflections or glare.",
]


def _job_workspace_dirs(settings: Settings, artifact_id: str) -> dict[str, Path]:
    root = settings.reconstruction_root_path / artifact_id
    return {
        "root": root,
        "source": root / "source",
        "workspace": root / "workspace",
        "output": root / "output",
    }


def _set_progress(database: Database, job_id: ObjectId, artifact_id: ObjectId, *, status: str, stage_message: str, **fields) -> None:
    repo.update_job(database, job_id, {"status": status, "stage_message": stage_message, **fields})
    repo.update_model_3d_state(database, artifact_id, {"status": status, **fields})


def _has_published_model(database: Database, artifact_id: ObjectId) -> bool:
    from app.repositories import artifact_repository

    artifact = artifact_repository.get_artifact(database, artifact_id)
    if artifact is None:
        return False
    current = repo.get_model_3d_state(artifact)
    return bool(current.get("path")) and int(current.get("version") or 0) > 0


def _fail(database: Database, job_id: ObjectId, artifact_id: ObjectId, *, message: str, guidance: list[str] | None = None) -> None:
    repo.update_job(database, job_id, {"status": states.FAILED, "stage_message": message, "error": message, "finished_at": utc_now()})
    # A failed rebuild must not destroy (or even hide) an existing working model: if a
    # previously published GLB exists, keep the artifact-level status READY so visitors keep
    # seeing it, and only surface the failure/guidance for the admin UI.
    next_status = states.READY if _has_published_model(database, artifact_id) else states.FAILED
    repo.update_model_3d_state(
        database,
        artifact_id,
        {"status": next_status, "failure_message": message, "guidance": guidance or []},
    )


def run(database: Database, settings: Settings, artifact_id: ObjectId, job_id: ObjectId) -> None:
    """Runs the full reconstruction pipeline for one job. Intended to run on a background
    thread - one active reconstruction job at a time is enforced by the caller before this
    starts, so this function never has to coordinate with a sibling job.
    """
    artifact_id_str = str(artifact_id)
    dirs = _job_workspace_dirs(settings, artifact_id_str)
    job = repo.get_job(database, job_id)
    target_version = job["target_version"] if job else 1

    availability = colmap_service.detect_colmap(settings)
    if not availability.available or not availability.bin_path:
        _fail(database, job_id, artifact_id, message="COLMAP is not available on this backend.")
        return

    bin_path = availability.bin_path

    # Fresh workspace/output per build; source images (the admin's curated input set) are
    # preserved so a rebuild after adding guidance-driven photos does not require re-uploading.
    if dirs["workspace"].exists():
        shutil.rmtree(dirs["workspace"])
    if dirs["output"].exists():
        shutil.rmtree(dirs["output"])
    dirs["workspace"].mkdir(parents=True, exist_ok=True)
    dirs["output"].mkdir(parents=True, exist_ok=True)

    log_path = dirs["output"] / "build.log"
    repo.update_job(database, job_id, {"log_path": str(log_path.relative_to(settings.reconstruction_root_path)), "started_at": utc_now()})

    source_count = len(list(dirs["source"].glob("*")))

    try:
        with log_path.open("w", encoding="utf-8") as log_file:
            _set_progress(
                database, job_id, artifact_id,
                status=states.SPARSE_RECONSTRUCTION,
                stage_message="Running feature extraction and matching.",
            )
            database_path = dirs["workspace"] / "database.db"
            pipeline.run_feature_extraction(
                bin_path,
                database_path=database_path,
                image_path=dirs["source"],
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

            sparse_path = dirs["workspace"] / "sparse"
            pipeline.run_mapper(bin_path, database_path=database_path, image_path=dirs["source"], sparse_path=sparse_path, log_file=log_file)

            sparse_model_path = sparse_path / "0"
            if not sparse_model_path.is_dir():
                _fail(
                    database, job_id, artifact_id,
                    message="Unable to create a usable 3D preview from these photographs.",
                    guidance=SPARSE_GUIDANCE,
                )
                return

            stats = pipeline.analyze_sparse_model(bin_path, sparse_model_path=sparse_model_path, log_file=log_file)
            registered = stats.registered_image_count or 0
            ratio = (registered / source_count) if source_count else 0.0
            metrics = {
                "registered_image_count": registered,
                "registered_image_ratio": round(ratio, 4),
                "sparse_point_count": stats.sparse_point_count,
                "mean_reprojection_error": stats.mean_reprojection_error,
            }
            repo.update_job(database, job_id, metrics)

            # Lightweight sanity gate (see reconstruction_service.run_preflight for the same
            # gate applied earlier): a best-effort preview only needs some real geometry, not a
            # high registered-image ratio. ratio stays informational.
            usable = registered >= MIN_USABLE_REGISTERED_IMAGES and (stats.sparse_point_count or 0) >= MIN_USABLE_SPARSE_POINTS
            if not usable:
                repo.update_job(database, job_id, {"status": states.FAILED, "stage_message": "Sparse reconstruction did not produce usable geometry.", "error": "needs_more_images", "finished_at": utc_now()})
                # As in _fail(): do not hide an existing published model behind NEEDS_IMAGES just
                # because a rebuild attempt's sparse gate came up short.
                next_status = states.READY if _has_published_model(database, artifact_id) else states.NEEDS_IMAGES
                repo.update_model_3d_state(
                    database, artifact_id,
                    {
                        "status": next_status,
                        "failure_message": None,
                        "guidance": SPARSE_GUIDANCE,
                        **metrics,
                    },
                )
                return

            fused_ply = dirs["output"] / "fused.ply"
            used_dense_stereo = False
            if settings.colmap_use_gpu:
                _set_progress(database, job_id, artifact_id, status=states.DENSE_RECONSTRUCTION, stage_message="Running dense multi-view stereo.", **metrics)
                dense_path = dirs["workspace"] / "dense"
                try:
                    pipeline.run_image_undistorter(bin_path, image_path=dirs["source"], sparse_model_path=sparse_model_path, dense_path=dense_path, log_file=log_file)
                    pipeline.run_patch_match_stereo(bin_path, dense_path=dense_path, use_gpu=True, log_file=log_file)
                    pipeline.run_stereo_fusion(bin_path, dense_path=dense_path, fused_output=fused_ply, log_file=log_file)
                    used_dense_stereo = True
                except pipeline.ColmapStageError as exc:
                    log_file.write(f"\n[dense stereo unavailable, falling back to sparse mesh: {exc}]\n")

            _set_progress(database, job_id, artifact_id, status=states.MESHING, stage_message="Generating mesh.", **metrics)
            mesh_ply = dirs["output"] / "mesh.ply"
            if used_dense_stereo:
                pipeline.run_mesher(bin_path, input_ply=fused_ply, output_ply=mesh_ply, log_file=log_file)
            else:
                # CPU-only environments (the default for this system) cannot run COLMAP's
                # CUDA-only patch_match_stereo, so there is no fused/dense point cloud with
                # normals to hand to poisson_mesher. Mesh straight from the sparse
                # reconstruction with delaunay_mesher instead - a real, working
                # (lower-fidelity) preview beats hard failure.
                pipeline.run_sparse_mesher(bin_path, sparse_model_path=sparse_model_path, output_ply=mesh_ply, log_file=log_file)
            if not mesh_ply.is_file():
                _fail(database, job_id, artifact_id, message="Meshing did not produce an output file.", guidance=SPARSE_GUIDANCE)
                return

            _set_progress(database, job_id, artifact_id, status=states.TEXTURING, stage_message="Applying vertex colors from the reconstructed point cloud.", **metrics)
            # Colored meshing above already carries per-vertex color from the fused point
            # cloud - that is this pipeline's texturing step (see glb_converter for the
            # vertex-color GLB export path).

            _set_progress(database, job_id, artifact_id, status=states.CONVERTING, stage_message="Converting to a mobile-ready GLB.", **metrics)
            destination = settings.model_3d_root_path / artifact_id_str / f"model-v{target_version}.glb"
            try:
                result = convert_to_glb(
                    dirs["output"],
                    destination,
                    simplify_ratio=settings.model_3d_simplify_ratio,
                    max_glb_mb=settings.model_3d_max_glb_mb,
                )
            except GlbConversionError as exc:
                _fail(database, job_id, artifact_id, message=f"GLB conversion failed: {exc}", guidance=SPARSE_GUIDANCE)
                return

            # Stored relative to BACKEND_DIR (e.g. "uploads/models3d/<id>/model-v1.glb"), matching
            # how artifact image_paths are stored, so image_url_for_path() works unmodified.
            relative_glb_path = destination.relative_to(settings.model_3d_root_path.parent.parent).as_posix()
            guidance = [
                "3D preview generated from the supplied photographs. Areas without sufficient "
                "image coverage may appear incomplete.",
                *result.warnings,
            ]
            if not used_dense_stereo:
                guidance.append("Built from the sparse point cloud (CPU mode). Enable COLMAP_USE_GPU for higher-fidelity dense reconstruction.")

            # A successful build produces a DRAFT awaiting admin review, not an immediately
            # visible published model - the visitor-facing version/path/sha/size fields (and
            # any previously accepted model they point to) are left untouched here.
            repo.update_job(database, job_id, {"status": states.PENDING_REVIEW, "stage_message": "3D preview ready for review.", "finished_at": utc_now()})
            repo.update_model_3d_state(
                database, artifact_id,
                {
                    "status": states.PENDING_REVIEW,
                    "draft_version": target_version,
                    "draft_path": relative_glb_path,
                    "draft_sha256": result.sha256,
                    "draft_size_bytes": result.size_bytes,
                    "draft_created_at": utc_now(),
                    "failure_message": None,
                    "guidance": guidance,
                    **metrics,
                },
            )
    except pipeline.ColmapStageError as exc:
        logger.warning("Reconstruction job %s failed at stage %s: %s", job_id, exc.stage, exc)
        _fail(database, job_id, artifact_id, message=f"Reconstruction failed during {exc.stage}.")
    except Exception:
        logger.exception("Reconstruction job %s failed unexpectedly.", job_id)
        _fail(database, job_id, artifact_id, message="Reconstruction failed due to an unexpected error.")
