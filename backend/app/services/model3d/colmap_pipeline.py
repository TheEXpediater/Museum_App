from __future__ import annotations

import re
import subprocess
from dataclasses import dataclass
from pathlib import Path

from app.services.model3d import colmap_service

DEFAULT_STAGE_TIMEOUT_SECONDS = 60 * 30


class ColmapStageError(RuntimeError):
    def __init__(self, stage: str, message: str) -> None:
        super().__init__(message)
        self.stage = stage


@dataclass
class SparseStats:
    registered_image_count: int | None
    sparse_point_count: int | None
    mean_reprojection_error: float | None


def _subcommand_supports(bin_path: str, subcommand: str, option: str) -> bool:
    try:
        result = subprocess.run([bin_path, subcommand, "-h"], capture_output=True, text=True, timeout=15, check=False)
    except (OSError, subprocess.SubprocessError):
        return False
    return option in (result.stdout or "") + (result.stderr or "")


def _run_stage(
    bin_path: str,
    stage: str,
    args: list[str],
    *,
    log_file,
    timeout: int = DEFAULT_STAGE_TIMEOUT_SECONDS,
) -> None:
    command = [bin_path, stage, *args]
    log_file.write(f"\n=== {stage} ===\n")
    log_file.flush()
    try:
        process = subprocess.run(
            command,
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        log_file.write(f"[timeout after {timeout}s]\n")
        raise ColmapStageError(stage, f"{stage} timed out.") from exc

    log_file.write(process.stdout or "")
    log_file.write(process.stderr or "")
    log_file.flush()
    if process.returncode != 0:
        raise ColmapStageError(stage, f"{stage} exited with status {process.returncode}.")


def run_feature_extraction(bin_path: str, *, database_path: Path, image_path: Path, use_gpu: bool, max_dimension: int, log_file) -> None:
    args = [
        "--database_path", str(database_path),
        "--image_path", str(image_path),
        "--ImageReader.single_camera", "1",
    ]
    if _subcommand_supports(bin_path, "feature_extractor", "--SiftExtraction.max_image_size"):
        args += ["--SiftExtraction.max_image_size", str(max_dimension)]
    if not use_gpu:
        # COLMAP renamed this flag from --SiftExtraction.use_gpu to --FeatureExtraction.use_gpu
        # in COLMAP 4.x; support both so CPU-only mode works across installed versions.
        for flag in ("--FeatureExtraction.use_gpu", "--SiftExtraction.use_gpu"):
            if _subcommand_supports(bin_path, "feature_extractor", flag):
                args += [flag, "0"]
                break
    _run_stage(bin_path, "feature_extractor", args, log_file=log_file)


def run_matching(bin_path: str, *, database_path: Path, use_gpu: bool, log_file) -> None:
    args = ["--database_path", str(database_path)]
    if not use_gpu:
        # Same COLMAP 4.x rename as feature_extractor: --SiftMatching.use_gpu -> --FeatureMatching.use_gpu.
        for flag in ("--FeatureMatching.use_gpu", "--SiftMatching.use_gpu"):
            if _subcommand_supports(bin_path, "exhaustive_matcher", flag):
                args += [flag, "0"]
                break
    _run_stage(bin_path, "exhaustive_matcher", args, log_file=log_file)


def run_mapper(bin_path: str, *, database_path: Path, image_path: Path, sparse_path: Path, log_file) -> None:
    sparse_path.mkdir(parents=True, exist_ok=True)
    args = [
        "--database_path", str(database_path),
        "--image_path", str(image_path),
        "--output_path", str(sparse_path),
    ]
    _run_stage(bin_path, "mapper", args, log_file=log_file, timeout=DEFAULT_STAGE_TIMEOUT_SECONDS * 2)


def analyze_sparse_model(bin_path: str, *, sparse_model_path: Path, log_file) -> SparseStats:
    command = [bin_path, "model_analyzer", "--path", str(sparse_model_path)]
    log_file.write("\n=== model_analyzer ===\n")
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=60, check=False)
    except (OSError, subprocess.SubprocessError) as exc:
        raise ColmapStageError("model_analyzer", "Could not read sparse reconstruction statistics.") from exc

    output = (result.stdout or "") + (result.stderr or "")
    log_file.write(output)
    log_file.flush()

    def _extract(pattern: str, cast):
        match = re.search(pattern, output, re.MULTILINE)
        return cast(match.group(1)) if match else None

    return SparseStats(
        registered_image_count=_extract(r"Registered images:\s*(\d+)", int),
        sparse_point_count=_extract(r"Points:\s*(\d+)", int),
        mean_reprojection_error=_extract(r"Mean reprojection error:\s*([\d.]+)", float),
    )


def run_image_undistorter(bin_path: str, *, image_path: Path, sparse_model_path: Path, dense_path: Path, log_file) -> None:
    dense_path.mkdir(parents=True, exist_ok=True)
    args = [
        "--image_path", str(image_path),
        "--input_path", str(sparse_model_path),
        "--output_path", str(dense_path),
        "--output_type", "COLMAP",
    ]
    _run_stage(bin_path, "image_undistorter", args, log_file=log_file, timeout=DEFAULT_STAGE_TIMEOUT_SECONDS * 2)


def run_patch_match_stereo(bin_path: str, *, dense_path: Path, use_gpu: bool, log_file) -> None:
    args = ["--workspace_path", str(dense_path)]
    if not use_gpu:
        # patch_match_stereo requires CUDA in stock COLMAP builds; CPU-only environments cannot
        # run true multi-view stereo. Callers must treat this stage as unavailable without GPU
        # and fall back to a sparse-only (point-cloud) mesh instead of raising an opaque error.
        raise ColmapStageError("patch_match_stereo", "Dense stereo requires COLMAP_USE_GPU=true; skipping to sparse-based meshing.")
    _run_stage(bin_path, "patch_match_stereo", args, log_file=log_file, timeout=DEFAULT_STAGE_TIMEOUT_SECONDS * 4)


def run_stereo_fusion(bin_path: str, *, dense_path: Path, fused_output: Path, log_file) -> None:
    args = [
        "--workspace_path", str(dense_path),
        "--output_path", str(fused_output),
    ]
    _run_stage(bin_path, "stereo_fusion", args, log_file=log_file, timeout=DEFAULT_STAGE_TIMEOUT_SECONDS * 2)


def run_mesher(bin_path: str, *, input_ply: Path, output_ply: Path, log_file) -> None:
    stage = "poisson_mesher" if colmap_service.command_supported(bin_path, "poisson_mesher") else "delaunay_mesher"
    if stage == "poisson_mesher":
        args = ["--input_path", str(input_ply), "--output_path", str(output_ply)]
    else:
        args = ["--input_path", str(input_ply.parent), "--output_path", str(output_ply)]
    _run_stage(bin_path, stage, args, log_file=log_file, timeout=DEFAULT_STAGE_TIMEOUT_SECONDS)


def export_sparse_as_ply(bin_path: str, *, sparse_model_path: Path, output_ply: Path, log_file) -> None:
    """CPU fallback path: convert the sparse point cloud directly to PLY for meshing when dense
    stereo (which needs CUDA) is not available."""
    args = [
        "--input_path", str(sparse_model_path),
        "--output_path", str(output_ply),
        "--output_type", "PLY",
    ]
    _run_stage(bin_path, "model_converter", args, log_file=log_file)
