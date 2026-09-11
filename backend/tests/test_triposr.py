from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.config import Settings
from app.services.model3d import ai_provider_service, triposr_provider
from app.services.model3d.ai_provider import Ai3DProviderError, STATUS_FAILED, STATUS_IN_PROGRESS, STATUS_SUCCEEDED
from app.services.model3d.coverage import estimate_coverage
from app.services.model3d.meshy_provider import MeshyProvider


def _settings(tmp_path: Path, **overrides) -> Settings:
    return Settings(
        mongodb_url="mongodb://localhost:27017",
        mongodb_database="museum_guide_test",
        jwt_secret_key="test-secret-key-that-is-long-enough",
        _env_file=None,
        triposr_root=str(tmp_path / "triposr"),
        **overrides,
    )


def _make_fake_runtime(tmp_path: Path) -> Path:
    root = tmp_path / "triposr"
    (root / ".venv" / "Scripts").mkdir(parents=True, exist_ok=True)
    (root / ".venv" / "Scripts" / "python.exe").write_bytes(b"")
    (root / "infer.py").write_text("# fake\n", encoding="utf-8")
    (root / "repo" / "tsr").mkdir(parents=True, exist_ok=True)
    return root


# --- detect() ---------------------------------------------------------------


def test_detect_unavailable_when_disabled(tmp_path):
    settings = _settings(tmp_path, local_ai_3d_enabled=False)
    result = triposr_provider.detect(settings)
    assert result.available is False
    assert "disabled" in result.message.lower()


def test_detect_unavailable_when_runtime_missing(tmp_path):
    settings = _settings(tmp_path, local_ai_3d_enabled=True)
    result = triposr_provider.detect(settings)
    assert result.available is False
    assert result.message


def test_detect_available_when_runtime_present(tmp_path):
    _make_fake_runtime(tmp_path)
    settings = _settings(tmp_path, local_ai_3d_enabled=True)
    result = triposr_provider.detect(settings)
    assert result.available is True
    assert result.message is None


def test_museum_backend_unaffected_when_local_ai_missing(tmp_path):
    """Mirrors colmap_service semantics: an unconfigured/missing optional 3D feature must never
    raise - only availability is False."""
    settings = _settings(tmp_path, local_ai_3d_enabled=True)
    result = triposr_provider.detect(settings)
    assert result.available is False


# --- attempt planning --------------------------------------------------------


def test_attempt_plan_auto_device_tries_gpu_then_reduced_gpu_then_cpu(tmp_path):
    settings = _settings(tmp_path, triposr_device="auto", triposr_cpu_fallback=True)
    plan = triposr_provider._build_attempt_plan(settings)
    devices = [attempt.device for attempt in plan]
    assert devices == ["cuda", "cuda", "cpu"]
    assert plan[1].chunk_size < plan[0].chunk_size
    assert plan[1].mc_resolution < plan[0].mc_resolution


def test_attempt_plan_cpu_device_never_tries_gpu(tmp_path):
    settings = _settings(tmp_path, triposr_device="cpu")
    plan = triposr_provider._build_attempt_plan(settings)
    assert [attempt.device for attempt in plan] == ["cpu"]


def test_attempt_plan_cuda_device_does_not_fall_back_to_cpu(tmp_path):
    settings = _settings(tmp_path, triposr_device="cuda")
    plan = triposr_provider._build_attempt_plan(settings)
    assert all(attempt.device == "cuda" for attempt in plan)


def test_persisted_profile_is_tried_first(tmp_path):
    settings = _settings(tmp_path, triposr_device="auto")
    triposr_provider._persist_profile(
        settings,
        triposr_provider._Attempt(
            device="cuda", chunk_size=1234, mc_resolution=111, bake_texture=True, texture_resolution=777, label="gpu-default"
        ),
    )
    plan = triposr_provider._build_attempt_plan(settings)
    assert plan[0].chunk_size == 1234
    assert plan[0].mc_resolution == 111


# --- create_generation / get_generation_status (subprocess mocked) ----------


class _FakeProcess:
    def __init__(self, returncode: int) -> None:
        self.returncode = returncode
        self.killed = False

    def poll(self):
        return self.returncode

    def kill(self):
        self.killed = True

    def wait(self, timeout=None):
        return self.returncode


def _patch_popen(monkeypatch, results: list[dict]):
    """results[i] describes the i-th subprocess.Popen() call's outcome. Writes directly through
    the already-open stdout file HANDLE passed by triposr_provider (rather than reopening the
    path), since the caller still holds - and later closes - that same handle in a `with` block."""
    calls = {"count": 0}

    def fake_popen(args, cwd=None, stdout=None, stderr=None, env=None):
        index = calls["count"]
        calls["count"] += 1
        outcome = results[index]
        if outcome["payload"] is not None:
            stdout.write(json.dumps(outcome["payload"]))
            stdout.flush()
        return _FakeProcess(outcome["returncode"])

    monkeypatch.setattr(triposr_provider.subprocess, "Popen", fake_popen)
    return calls


def _runtime_settings(tmp_path: Path, **overrides) -> Settings:
    _make_fake_runtime(tmp_path)
    return _settings(tmp_path, local_ai_3d_enabled=True, **overrides)


def test_create_generation_succeeds_on_first_gpu_attempt(tmp_path, monkeypatch):
    settings = _runtime_settings(tmp_path, triposr_device="cuda")
    output = tmp_path / "out.glb"
    output.write_bytes(b"glb-bytes")
    _patch_popen(monkeypatch, [{"returncode": 0, "payload": {"success": True, "output": str(output), "has_texture": True}}])

    provider = triposr_provider.TripoSrProvider(settings)
    handle = provider.create_generation([tmp_path / "image.jpg"])
    result = provider.get_generation_status(handle.provider_task_id)

    assert result.status == STATUS_SUCCEEDED
    assert result.glb_url == str(output)


def test_get_generation_status_reports_in_progress_while_process_runs(tmp_path, monkeypatch):
    settings = _runtime_settings(tmp_path, triposr_device="cuda")

    class _StillRunning:
        def poll(self):
            return None

    monkeypatch.setattr(triposr_provider.subprocess, "Popen", lambda *a, **k: _StillRunning())

    provider = triposr_provider.TripoSrProvider(settings)
    handle = provider.create_generation([tmp_path / "image.jpg"])
    result = provider.get_generation_status(handle.provider_task_id)
    assert result.status == STATUS_IN_PROGRESS


def test_gpu_oom_falls_back_to_reduced_gpu_then_cpu_success(tmp_path, monkeypatch):
    settings = _runtime_settings(tmp_path, triposr_device="auto", triposr_cpu_fallback=True)
    output = tmp_path / "out.glb"
    output.write_bytes(b"glb-bytes")
    _patch_popen(
        monkeypatch,
        [
            {"returncode": 1, "payload": {"success": False, "error_type": "cuda_oom", "message": "CUDA out of memory"}},
            {"returncode": 1, "payload": {"success": False, "error_type": "cuda_oom", "message": "CUDA out of memory"}},
            {"returncode": 0, "payload": {"success": True, "output": str(output), "has_texture": False}},
        ],
    )

    provider = triposr_provider.TripoSrProvider(settings)
    handle = provider.create_generation([tmp_path / "image.jpg"])
    first = provider.get_generation_status(handle.provider_task_id)
    assert first.status == STATUS_IN_PROGRESS  # advanced to the reduced-GPU attempt
    second = provider.get_generation_status(handle.provider_task_id)
    assert second.status == STATUS_IN_PROGRESS  # advanced to the CPU attempt
    third = provider.get_generation_status(handle.provider_task_id)
    assert third.status == STATUS_SUCCEEDED
    assert third.glb_url == str(output)


def test_all_attempts_failing_reports_failed_not_infinite_retry(tmp_path, monkeypatch):
    settings = _runtime_settings(tmp_path, triposr_device="cpu")
    _patch_popen(monkeypatch, [{"returncode": 1, "payload": {"success": False, "error_type": "error", "message": "boom"}}])

    provider = triposr_provider.TripoSrProvider(settings)
    handle = provider.create_generation([tmp_path / "image.jpg"])
    result = provider.get_generation_status(handle.provider_task_id)
    assert result.status == STATUS_FAILED
    assert "boom" in result.error_message


def test_create_generation_rejects_more_than_one_image(tmp_path):
    settings = _runtime_settings(tmp_path)
    provider = triposr_provider.TripoSrProvider(settings)
    with pytest.raises(ValueError):
        provider.create_generation([tmp_path / "a.jpg", tmp_path / "b.jpg"])


def test_download_result_copies_local_file_not_http(tmp_path):
    settings = _runtime_settings(tmp_path)
    provider = triposr_provider.TripoSrProvider(settings)
    source = tmp_path / "source.glb"
    source.write_bytes(b"real-glb-bytes")
    destination = tmp_path / "nested" / "dest.glb"

    provider.download_result(str(source), destination)

    assert destination.read_bytes() == b"real-glb-bytes"


def test_download_result_missing_local_file_raises(tmp_path):
    settings = _runtime_settings(tmp_path)
    provider = triposr_provider.TripoSrProvider(settings)
    with pytest.raises(Ai3DProviderError):
        provider.download_result(str(tmp_path / "does-not-exist.glb"), tmp_path / "dest.glb")


# --- dispatch precedence (local free provider vs. paid remote fallback) -----


def test_dispatch_prefers_local_triposr_over_configured_remote_meshy(tmp_path):
    """Quick AI 3D Preview (free, local) is the DEFAULT - even when a paid Meshy key happens to
    also be configured, the local provider must win so no cost is incurred unexpectedly."""
    settings = _runtime_settings(tmp_path, ai_3d_enabled=True, ai_3d_api_key="fake-meshy-key")

    availability = ai_provider_service.detect_ai_availability(settings)
    assert availability.available is True
    assert availability.provider == "triposr"
    assert availability.max_images == 1
    assert isinstance(ai_provider_service.get_provider(settings), triposr_provider.TripoSrProvider)


def test_dispatch_falls_back_to_remote_when_local_unavailable(tmp_path):
    settings = _settings(tmp_path, local_ai_3d_enabled=False, ai_3d_enabled=True, ai_3d_api_key="fake-meshy-key")

    availability = ai_provider_service.detect_ai_availability(settings)
    assert availability.available is True
    assert availability.provider == "meshy"
    assert availability.max_images == 4
    assert isinstance(ai_provider_service.get_provider(settings), MeshyProvider)


def test_dispatch_unavailable_when_neither_configured(tmp_path):
    settings = _settings(tmp_path, local_ai_3d_enabled=False)
    availability = ai_provider_service.detect_ai_availability(settings)
    assert availability.available is False
    with pytest.raises(ai_provider_service.Ai3DConfigurationError):
        ai_provider_service.get_provider(settings)


# --- coverage.py --------------------------------------------------------------


def test_estimate_coverage_unavailable_when_no_regions():
    supported, inferred, normalized = estimate_coverage([])
    assert supported is None
    assert inferred is None
    assert normalized == []


def test_estimate_coverage_rounds_cleanly_and_sums_to_100():
    supported, inferred, normalized = estimate_coverage(["Front", "right", "TOP"])
    assert normalized == ["front", "right", "top"]
    assert supported == 50  # 3/6 = 50%
    assert inferred == 50
    assert supported + inferred == 100


def test_estimate_coverage_ignores_unknown_region_names():
    supported, inferred, normalized = estimate_coverage(["front", "not-a-region"])
    assert normalized == ["front"]
    assert supported == round(100 / 6)
