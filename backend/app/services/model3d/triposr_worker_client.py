from __future__ import annotations

import shutil
import time
from dataclasses import dataclass
from pathlib import Path
from uuid import uuid4

import httpx

from app.config import Settings
from app.services.model3d.ai_provider import (
    Ai3DProvider,
    Ai3DProviderError,
    Ai3DTaskHandle,
    Ai3DTaskResult,
    STATUS_FAILED,
    STATUS_IN_PROGRESS,
    STATUS_SUCCEEDED,
)

_HTTP_TIMEOUT_SECONDS = 10.0


@dataclass(frozen=True)
class TripoSrWorkerAvailability:
    available: bool
    message: str | None


def detect(settings: Settings) -> TripoSrWorkerAvailability:
    """Worker-mode equivalent of triposr_provider.detect(): a real network probe of the
    worker's /health instead of local filesystem checks, since the venv/repo live in a
    different container now. Safe to call on every status poll - short timeout, never raises."""
    if not settings.local_ai_3d_enabled:
        return TripoSrWorkerAvailability(False, "Local AI 3D preview is disabled.")
    if settings.local_ai_3d_provider != "triposr":
        return TripoSrWorkerAvailability(False, f"Unsupported LOCAL_AI_3D_PROVIDER: {settings.local_ai_3d_provider!r}.")
    try:
        response = httpx.get(f"{settings.triposr_worker_url}/health", timeout=_HTTP_TIMEOUT_SECONDS)
        response.raise_for_status()
        payload = response.json()
    except (httpx.HTTPError, ValueError):
        return TripoSrWorkerAvailability(False, "The triposr-worker service is unreachable.")
    if not payload.get("infer_script_present") or not payload.get("repo_present"):
        return TripoSrWorkerAvailability(False, "triposr-worker is reachable but its TripoSR runtime is not installed.")
    return TripoSrWorkerAvailability(True, None)


@dataclass
class _Task:
    worker_job_id: str
    output_glb: Path


class TripoSrWorkerProvider(Ai3DProvider):
    """Same product behavior as TripoSrProvider (local, free, single-image TripoSR preview), but
    delegates the actual subprocess launch to an isolated triposr-worker container over HTTP
    instead of calling subprocess.Popen() in-process. image_paths/output paths must resolve
    identically in both containers - see compose.prod.yaml's shared uploads (read-only) and
    triposr-jobs (read-write) volumes. Unlike TripoSrProvider, this does not implement the
    GPU/reduced-profile retry ladder: the worker is CPU-only by construction on this deployment,
    so there is nothing to fall back from.
    """

    max_images = 1

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._base_url = settings.triposr_worker_url.rstrip("/")
        self._client = httpx.Client(timeout=_HTTP_TIMEOUT_SECONDS)
        self._tasks: dict[str, _Task] = {}

    def create_generation(self, image_paths: list[Path]) -> Ai3DTaskHandle:
        if not image_paths:
            raise ValueError("At least one image is required.")
        if len(image_paths) > self.max_images:
            raise ValueError("TripoSR accepts exactly one primary image - select a single photo.")

        jobs_root = self._settings.triposr_worker_jobs_path
        if jobs_root is None:
            raise Ai3DProviderError("TRIPOSR_WORKER_JOBS_DIR is not configured - cannot hand off worker output.")

        task_id = uuid4().hex
        output_glb = jobs_root / task_id / "model.glb"

        try:
            response = self._client.post(
                f"{self._base_url}/jobs",
                json={
                    "image_path": str(image_paths[0]),
                    "output_glb": str(output_glb),
                    "device": "cpu",
                    "model_path": self._settings.triposr_model_path,
                    "chunk_size": self._settings.triposr_chunk_size,
                    "mc_resolution": self._settings.triposr_mc_resolution,
                    "texture_resolution": self._settings.triposr_texture_resolution,
                    "bake_texture": self._settings.triposr_bake_texture,
                    "remove_background": self._settings.triposr_remove_background,
                    "timeout_seconds": self._settings.triposr_timeout_seconds,
                },
            )
        except httpx.HTTPError as exc:
            raise Ai3DProviderError(f"Could not reach triposr-worker: {exc}") from exc

        if response.status_code == 409:
            raise Ai3DProviderError("triposr-worker is already busy with another generation job.")
        if response.is_error:
            raise Ai3DProviderError(f"triposr-worker rejected the job request: {response.text}")

        payload = response.json()
        worker_job_id = payload["job_id"]
        self._tasks[task_id] = _Task(worker_job_id=worker_job_id, output_glb=output_glb)
        return Ai3DTaskHandle(provider_task_id=task_id)

    def get_generation_status(self, task_id: str) -> Ai3DTaskResult:
        task = self._tasks.get(task_id)
        if task is None:
            raise Ai3DProviderError("Unknown or already-finished TripoSR generation task.")

        try:
            response = self._client.get(f"{self._base_url}/jobs/{task.worker_job_id}")
        except httpx.HTTPError as exc:
            raise Ai3DProviderError(f"Could not reach triposr-worker: {exc}") from exc

        if response.status_code == 404:
            del self._tasks[task_id]
            raise Ai3DProviderError("triposr-worker lost track of the job (it may have restarted).")
        if response.is_error:
            raise Ai3DProviderError(f"triposr-worker returned an error: {response.text}")

        payload = response.json()
        if payload.get("running"):
            return Ai3DTaskResult(status=STATUS_IN_PROGRESS, progress=None)

        if payload.get("timed_out"):
            del self._tasks[task_id]
            return Ai3DTaskResult(status=STATUS_FAILED, error_message="Local AI 3D generation timed out on the worker.")

        result = payload.get("result") or {}
        returncode = payload.get("returncode")
        if result.get("success"):
            if not task.output_glb.is_file() or task.output_glb.stat().st_size == 0:
                del self._tasks[task_id]
                raise Ai3DProviderError("TripoSR reported success but produced no usable output file.")
            del self._tasks[task_id]
            return Ai3DTaskResult(status=STATUS_SUCCEEDED, progress=100, glb_url=str(task.output_glb))

        message = result.get("message") or f"TripoSR worker exited with code {returncode}."
        del self._tasks[task_id]
        return Ai3DTaskResult(status=STATUS_FAILED, error_message=f"Local AI 3D generation failed: {message}")

    def download_result(self, glb_url: str, destination: Path) -> None:
        # Same shared-volume convention as TripoSrProvider: glb_url is a local absolute path
        # (on the volume shared between the backend and triposr-worker containers), not a
        # remote URL, so this is a plain filesystem copy rather than an HTTP fetch.
        source = Path(glb_url)
        if not source.is_file():
            raise Ai3DProviderError("The generated TripoSR model file is missing on disk.")
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
