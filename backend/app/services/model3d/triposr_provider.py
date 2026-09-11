from __future__ import annotations

import json
import os
import shutil
import subprocess
import time
from dataclasses import dataclass, field
from pathlib import Path
from uuid import uuid4

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

PROFILE_FILENAME = "profile.json"
JOBS_SUBDIR = "jobs"


@dataclass(frozen=True)
class TripoSrAvailability:
    available: bool
    message: str | None


def detect(settings: Settings) -> TripoSrAvailability:
    """Cheap, filesystem-only detection, safe to call on every `GET /3d` status poll (mirrors
    colmap_service.detect_colmap() being called on every request, but deliberately avoids
    spawning a Python interpreter + importing torch on each poll - that costs seconds, unlike
    COLMAP's `-h`). This only proves the runtime looks installed; a real generation attempt is
    what actually proves imports/model loading work, and its failure is reported through the
    normal job-failure path rather than here."""
    if not settings.local_ai_3d_enabled:
        return TripoSrAvailability(False, "Local AI 3D preview is disabled.")
    if settings.local_ai_3d_provider != "triposr":
        return TripoSrAvailability(False, f"Unsupported LOCAL_AI_3D_PROVIDER: {settings.local_ai_3d_provider!r}.")
    python_path = settings.triposr_python_path
    if python_path is None or not python_path.is_file():
        return TripoSrAvailability(
            False, "Local AI 3D runtime not found. Set up tools/triposr (see tools/triposr/requirements.txt)."
        )
    if not (settings.triposr_root_path / "infer.py").is_file():
        return TripoSrAvailability(False, "tools/triposr/infer.py is missing.")
    if not (settings.triposr_root_path / "repo" / "tsr").is_dir():
        return TripoSrAvailability(False, "tools/triposr/repo (TripoSR model source) is missing.")
    return TripoSrAvailability(True, None)


@dataclass(eq=True)
class _Attempt:
    device: str
    chunk_size: int
    mc_resolution: int
    bake_texture: bool
    texture_resolution: int
    label: str

    def to_json(self) -> dict:
        return {
            "device": self.device,
            "chunk_size": self.chunk_size,
            "mc_resolution": self.mc_resolution,
            "bake_texture": self.bake_texture,
            "texture_resolution": self.texture_resolution,
            "label": self.label,
        }

    @classmethod
    def from_json(cls, data: dict) -> "_Attempt":
        return cls(
            device=data["device"],
            chunk_size=int(data["chunk_size"]),
            mc_resolution=int(data["mc_resolution"]),
            bake_texture=bool(data["bake_texture"]),
            texture_resolution=int(data["texture_resolution"]),
            label=str(data.get("label", "persisted")),
        )


@dataclass
class _Task:
    image_path: Path
    job_dir: Path
    attempts: list[_Attempt]
    attempt_index: int = -1
    attempt_started_at: float = 0.0
    process: "subprocess.Popen | None" = None
    stdout_path: Path | None = None
    attempts_log: list[dict] = field(default_factory=list)


def _build_attempt_plan(settings: Settings) -> list[_Attempt]:
    device_setting = settings.triposr_device
    plan: list[_Attempt] = []

    persisted = _load_profile(settings)
    if persisted is not None and (device_setting == "auto" or persisted.device == device_setting):
        plan.append(persisted)

    if device_setting in ("auto", "cuda"):
        plan.append(
            _Attempt(
                device="cuda",
                chunk_size=settings.triposr_chunk_size,
                mc_resolution=settings.triposr_mc_resolution,
                bake_texture=settings.triposr_bake_texture,
                texture_resolution=settings.triposr_texture_resolution,
                label="gpu-default",
            )
        )
        if device_setting == "auto":
            plan.append(
                _Attempt(
                    device="cuda",
                    chunk_size=min(512, settings.triposr_chunk_size),
                    mc_resolution=min(96, settings.triposr_mc_resolution),
                    bake_texture=False,
                    texture_resolution=min(512, settings.triposr_texture_resolution),
                    label="gpu-reduced",
                )
            )

    if device_setting == "cpu" or (device_setting == "auto" and settings.triposr_cpu_fallback):
        plan.append(
            _Attempt(
                device="cpu",
                chunk_size=settings.triposr_chunk_size,
                mc_resolution=settings.triposr_mc_resolution,
                bake_texture=settings.triposr_bake_texture,
                texture_resolution=settings.triposr_texture_resolution,
                label="cpu-default",
            )
        )

    deduped: list[_Attempt] = []
    for attempt in plan:
        if not deduped or deduped[-1] != attempt:
            deduped.append(attempt)
    return deduped


def _profile_path(settings: Settings) -> Path:
    return settings.triposr_root_path / PROFILE_FILENAME


def _load_profile(settings: Settings) -> _Attempt | None:
    path = _profile_path(settings)
    if not path.is_file():
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return _Attempt.from_json(data)
    except (OSError, ValueError, KeyError):
        return None


def _persist_profile(settings: Settings, attempt: _Attempt) -> None:
    try:
        payload = attempt.to_json()
        payload["verified_at"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        path = _profile_path(settings)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    except OSError:
        pass  # persistence is an optimization, not a correctness requirement


def _read_result_json(stdout_path: Path | None) -> dict | None:
    if stdout_path is None or not stdout_path.is_file():
        return None
    text = stdout_path.read_text(encoding="utf-8", errors="replace").strip()
    if not text:
        return None
    for line in reversed(text.splitlines()):
        line = line.strip()
        if not line:
            continue
        try:
            return json.loads(line)
        except ValueError:
            continue
    return None


class TripoSrProvider(Ai3DProvider):
    """Local, free, single-image AI 3D provider (pretrained TripoSR inference). Unlike
    MeshyProvider (a remote HTTP task), generation runs as a local subprocess in an isolated
    Python environment (tools/triposr/.venv) - see infer.py. create_generation() launches the
    first attempt from a small device/memory retry plan (see _build_attempt_plan); subsequent
    polls in get_generation_status() advance to the next attempt if the current one fails on the
    GPU (OOM or any other CUDA failure), so a 4GB-VRAM card degrades to a smaller GPU profile and
    then CPU without ever looping indefinitely. glb_url in the returned Ai3DTaskResult is a local
    absolute file path, not a remote URL - download_result() copies it rather than fetching it.
    """

    max_images = 1

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._tasks: dict[str, _Task] = {}

    def create_generation(self, image_paths: list[Path]) -> Ai3DTaskHandle:
        if not image_paths:
            raise ValueError("At least one image is required.")
        if len(image_paths) > self.max_images:
            raise ValueError("TripoSR accepts exactly one primary image - select a single photo.")

        task_id = uuid4().hex
        job_dir = self._settings.triposr_root_path / JOBS_SUBDIR / task_id
        job_dir.mkdir(parents=True, exist_ok=True)
        attempts = _build_attempt_plan(self._settings)
        if not attempts:
            raise Ai3DProviderError("No TripoSR execution profile is configured (neither GPU nor CPU is enabled).")

        task = _Task(image_path=image_paths[0], job_dir=job_dir, attempts=attempts)
        self._tasks[task_id] = task
        self._launch_next_attempt(task)
        return Ai3DTaskHandle(provider_task_id=task_id)

    def get_generation_status(self, task_id: str) -> Ai3DTaskResult:
        task = self._tasks.get(task_id)
        if task is None:
            raise Ai3DProviderError("Unknown or already-finished TripoSR generation task.")
        if task.process is None:
            raise Ai3DProviderError("TripoSR task has no active attempt.")

        attempt = task.attempts[task.attempt_index]
        elapsed = time.monotonic() - task.attempt_started_at
        if elapsed > self._settings.triposr_timeout_seconds:
            task.process.kill()
            task.process.wait(timeout=15)
            task.attempts_log.append({"attempt": attempt.label, "result": "timeout"})
            del self._tasks[task_id]
            return Ai3DTaskResult(
                status=STATUS_FAILED,
                error_message=f"Local AI 3D generation timed out during the {attempt.label} attempt.",
            )

        returncode = task.process.poll()
        if returncode is None:
            return Ai3DTaskResult(status=STATUS_IN_PROGRESS, progress=None)

        payload = _read_result_json(task.stdout_path)
        task.attempts_log.append({"attempt": attempt.label, "returncode": returncode, "result": payload})

        if payload and payload.get("success"):
            output_path = Path(payload["output"])
            if not output_path.is_file() or output_path.stat().st_size == 0:
                del self._tasks[task_id]
                raise Ai3DProviderError("TripoSR reported success but produced no usable output file.")
            _persist_profile(self._settings, attempt)
            del self._tasks[task_id]
            return Ai3DTaskResult(status=STATUS_SUCCEEDED, progress=100, glb_url=str(output_path))

        message = (payload or {}).get("message") or f"TripoSR exited with code {returncode}."
        if attempt.device == "cuda" and task.attempt_index + 1 < len(task.attempts):
            self._launch_next_attempt(task)
            return Ai3DTaskResult(status=STATUS_IN_PROGRESS, progress=None)

        del self._tasks[task_id]
        return Ai3DTaskResult(
            status=STATUS_FAILED,
            error_message=f"Local AI 3D generation failed ({attempt.label}): {message}",
        )

    def download_result(self, glb_url: str, destination: Path) -> None:
        source = Path(glb_url)
        if not source.is_file():
            raise Ai3DProviderError("The generated TripoSR model file is missing on disk.")
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)

    def _launch_next_attempt(self, task: _Task) -> None:
        task.attempt_index += 1
        attempt = task.attempts[task.attempt_index]
        attempt_dir = task.job_dir / f"attempt-{task.attempt_index}"
        attempt_dir.mkdir(parents=True, exist_ok=True)
        output_glb = attempt_dir / "model.glb"
        stdout_path = attempt_dir / "stdout.log"
        stderr_path = attempt_dir / "stderr.log"

        python_path = self._settings.triposr_python_path
        if python_path is None:
            raise Ai3DProviderError("TripoSR Python runtime is not configured.")
        infer_script = self._settings.triposr_root_path / "infer.py"

        args = [
            str(python_path),
            str(infer_script),
            "--image", str(task.image_path),
            "--output-glb", str(output_glb),
            "--device", attempt.device,
            "--model-path", self._settings.triposr_model_path,
            "--chunk-size", str(attempt.chunk_size),
            "--mc-resolution", str(attempt.mc_resolution),
            "--texture-resolution", str(attempt.texture_resolution),
        ]
        if attempt.bake_texture:
            args.append("--bake-texture")

        env = dict(os.environ)
        env["HF_HOME"] = str(self._settings.triposr_root_path / "model_cache")

        with stdout_path.open("w", encoding="utf-8") as stdout_file, stderr_path.open("w", encoding="utf-8") as stderr_file:
            process = subprocess.Popen(
                args,
                cwd=str(self._settings.triposr_root_path),
                stdout=stdout_file,
                stderr=stderr_file,
                env=env,
            )

        task.process = process
        task.stdout_path = stdout_path
        task.attempt_started_at = time.monotonic()
