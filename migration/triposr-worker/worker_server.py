"""Minimal internal-only HTTP shim around infer.py (and, when background removal is requested,
preprocess_bg.py) for the isolated triposr-worker container.

This exists ONLY because a container cannot receive a subprocess.Popen() call from a sibling
container's process - everything else about how TripoSR actually runs is unchanged from local
development: this still launches infer.py as a subprocess in its own process and just reports
status/results over HTTP instead of a Python method return value.

Staged pipeline: when remove_background is requested, a job runs as TWO sequential subprocesses
instead of one - preprocess_bg.py (rembg background isolation) is run to completion and fully
exits before infer.py (TripoSR itself) ever starts. This is deliberate: rembg's ~1GB ONNX
segmentation model coexisting in the same process as TripoSR's own model caused a real OOM kill
on this VPS (dmesg, anon-rss ~7.7GB - see backend/app/config.py's triposr_remove_background
comment). Running them as separate processes means the OS fully reclaims rembg's memory before
TripoSR's model ever loads, regardless of what either library does internally. infer.py itself
is unmodified - it always receives --no-remove-bg from this worker, either because the caller
asked for the original image as-is, or because preprocess_bg.py already isolated it.

Not exposed publicly - only reachable from the backend container over the internal Docker
network (see compose.prod.yaml). Enforces concurrency=1 itself (in addition to the backend's own
MongoDB-backed one-active-job rule) as defense in depth against direct API misuse.
"""
from __future__ import annotations

import json
import os
import subprocess
import threading
import time
import uuid
from pathlib import Path

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

APP_DIR = Path(__file__).resolve().parent
INFER_SCRIPT = APP_DIR / "infer.py"
PREPROCESS_SCRIPT = APP_DIR / "preprocess_bg.py"
DEFAULT_TIMEOUT_SECONDS = int(os.environ.get("TRIPOSR_TIMEOUT_SECONDS", "1200"))
JOBS_ROOT = Path(os.environ.get("TRIPOSR_JOBS_ROOT", "/triposr-jobs"))
DEFAULT_FOREGROUND_RATIO = 0.85

app = FastAPI(title="triposr-worker")
_lock = threading.Lock()


class _Job:
    def __init__(self, job_id: str, started_at: float, timeout_seconds: int):
        self.job_id = job_id
        self.started_at = started_at
        self.timeout_seconds = timeout_seconds
        # Everything below is mutated by the background runner thread and read by the HTTP
        # handlers - all access must hold `_lock`.
        self.phase = "preprocess"  # or "infer"
        self.process: subprocess.Popen | None = None
        self.cancelled = False
        self.done = False
        self.returncode: int | None = None
        self.result: dict | None = None
        self.timed_out = False


_current: _Job | None = None


class GenerateRequest(BaseModel):
    image_path: str
    output_glb: str
    device: str = "cpu"
    model_path: str = "stabilityai/TripoSR"
    chunk_size: int = 2048
    mc_resolution: int = 192
    texture_resolution: int = 1024
    bake_texture: bool = True
    remove_background: bool = True
    timeout_seconds: int | None = None


class JobStatus(BaseModel):
    job_id: str
    running: bool
    returncode: int | None = None
    result: dict | None = None
    timed_out: bool = False


def _read_result_json(stdout_path: Path) -> dict | None:
    if not stdout_path.is_file():
        return None
    text = stdout_path.read_text(encoding="utf-8", errors="replace").strip()
    for line in reversed(text.splitlines()):
        line = line.strip()
        if not line:
            continue
        try:
            return json.loads(line)
        except ValueError:
            continue
    return None


def _finish(job: _Job, *, returncode: int | None, result: dict | None, timed_out: bool = False) -> None:
    with _lock:
        job.process = None
        job.returncode = returncode
        job.result = result
        job.timed_out = timed_out
        job.done = True


def _run_stage(job: _Job, phase: str, args: list[str]) -> tuple[int, bool]:
    """Launches one subprocess, records it as the job's current process, and blocks (in this
    background thread only) until it exits. Returns (returncode, was_cancelled). The subprocess
    is fully exited - and its memory fully reclaimed by the OS - before this function returns."""
    with _lock:
        if job.cancelled:
            return -1, True
        job.phase = phase

    stdout_path = JOBS_ROOT / job.job_id / f"{phase}_stdout.log"
    stderr_path = JOBS_ROOT / job.job_id / f"{phase}_stderr.log"
    with stdout_path.open("w", encoding="utf-8") as stdout_file, stderr_path.open("w", encoding="utf-8") as stderr_file:
        process = subprocess.Popen(args, cwd=str(APP_DIR), stdout=stdout_file, stderr=stderr_file)
        with _lock:
            job.process = process
        returncode = process.wait()

    with _lock:
        job.process = None
        cancelled = job.cancelled
    return returncode, cancelled


def _run_job(job: _Job, payload: GenerateRequest, image_path: Path, output_glb: Path) -> None:
    job_dir = JOBS_ROOT / job.job_id
    working_image_path = image_path

    try:
        if payload.remove_background:
            isolated_image = job_dir / "isolated.png"
            args = [
                "python", str(PREPROCESS_SCRIPT),
                "--image", str(image_path),
                "--output", str(isolated_image),
                "--foreground-ratio", str(DEFAULT_FOREGROUND_RATIO),
            ]
            returncode, cancelled = _run_stage(job, "preprocess", args)
            if cancelled:
                _finish(job, returncode=returncode, result=None, timed_out=True)
                return

            pre_result = _read_result_json(job_dir / "preprocess_stdout.log")
            if returncode != 0 or not pre_result or not pre_result.get("success"):
                message = (pre_result or {}).get("message") or f"background isolation exited with code {returncode}"
                _finish(
                    job,
                    returncode=returncode,
                    result={"success": False, "stage": "preprocess", "message": message},
                )
                return
            working_image_path = Path(pre_result["output"])

        infer_args = [
            "python", str(INFER_SCRIPT),
            "--image", str(working_image_path),
            "--output-glb", str(output_glb),
            "--device", payload.device,
            "--model-path", payload.model_path,
            "--chunk-size", str(payload.chunk_size),
            "--mc-resolution", str(payload.mc_resolution),
            "--texture-resolution", str(payload.texture_resolution),
            # Always disable infer.py's OWN background removal: either the caller didn't want it,
            # or preprocess_bg.py already did it (and terminated) above - infer.py must never
            # load rembg itself, or the two heavy phases end up coexisting after all.
            "--no-remove-bg",
        ]
        if payload.bake_texture:
            infer_args.append("--bake-texture")

        returncode, cancelled = _run_stage(job, "infer", infer_args)
        if cancelled:
            _finish(job, returncode=returncode, result=None, timed_out=True)
            return

        infer_result = _read_result_json(job_dir / "infer_stdout.log")
        _finish(job, returncode=returncode, result=infer_result)
    except Exception as exc:  # noqa: BLE001 - background thread has no other error channel
        _finish(job, returncode=None, result={"success": False, "message": str(exc)})


@app.get("/health")
def health() -> dict:
    with _lock:
        busy = _current is not None and not _current.done
    return {
        "status": "healthy",
        "infer_script_present": INFER_SCRIPT.is_file(),
        "repo_present": (APP_DIR / "repo" / "tsr").is_dir(),
        "busy": busy,
    }


@app.post("/jobs", response_model=JobStatus)
def create_job(payload: GenerateRequest) -> JobStatus:
    global _current
    with _lock:
        if _current is not None and not _current.done:
            raise HTTPException(status_code=409, detail="A TripoSR job is already running on this worker.")

        image_path = Path(payload.image_path)
        if not image_path.is_file():
            raise HTTPException(status_code=422, detail=f"Source image not found on worker: {image_path}")

        job_id = uuid.uuid4().hex
        job_dir = JOBS_ROOT / job_id
        job_dir.mkdir(parents=True, exist_ok=True)
        output_glb = Path(payload.output_glb)
        output_glb.parent.mkdir(parents=True, exist_ok=True)

        job = _Job(
            job_id=job_id,
            started_at=time.monotonic(),
            timeout_seconds=payload.timeout_seconds or DEFAULT_TIMEOUT_SECONDS,
        )
        _current = job

    thread = threading.Thread(target=_run_job, args=(job, payload, image_path, output_glb), daemon=True)
    thread.start()
    return JobStatus(job_id=job_id, running=True)


@app.get("/jobs/{job_id}", response_model=JobStatus)
def get_job(job_id: str) -> JobStatus:
    with _lock:
        job = _current
        if job is None or job.job_id != job_id:
            raise HTTPException(status_code=404, detail="Unknown or already-finished job.")

        if job.done:
            return JobStatus(job_id=job_id, running=False, returncode=job.returncode, result=job.result, timed_out=job.timed_out)

        elapsed = time.monotonic() - job.started_at
        if elapsed > job.timeout_seconds:
            # Never let a subprocess run unbounded - kill it here rather than relying solely on
            # the backend's own polling timeout, so a stuck job is cleaned up even if the
            # backend restarts or stops polling. Marking cancelled first means the background
            # runner thread (see _run_stage) will not advance to a further stage once this
            # subprocess exits.
            job.cancelled = True
            process = job.process
            if process is not None and process.poll() is None:
                process.kill()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    pass
            return JobStatus(job_id=job_id, running=False, timed_out=True)

        return JobStatus(job_id=job_id, running=True)
