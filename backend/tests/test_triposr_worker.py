from __future__ import annotations

from pathlib import Path

import httpx
import pytest

from app.config import Settings
from app.services.model3d import ai_provider_service, triposr_worker_client
from app.services.model3d.ai_provider import Ai3DProviderError, STATUS_FAILED, STATUS_IN_PROGRESS, STATUS_SUCCEEDED


def _settings(tmp_path: Path, **overrides) -> Settings:
    defaults = {
        "triposr_worker_url": "http://triposr-worker:8100",
        "triposr_worker_jobs_dir": str(tmp_path / "triposr-jobs"),
    }
    defaults.update(overrides)
    return Settings(
        mongodb_url="mongodb://localhost:27017",
        mongodb_database="museum_guide_test",
        jwt_secret_key="test-secret-key-that-is-long-enough",
        _env_file=None,
        **defaults,
    )


def _mock_response(status_code: int, json_body: dict) -> httpx.Response:
    return httpx.Response(status_code, json=json_body, request=httpx.Request("GET", "http://x"))


# --- detect() -----------------------------------------------------------------


def test_detect_unavailable_when_worker_unreachable(tmp_path, monkeypatch):
    settings = _settings(tmp_path)

    def fake_get(url, timeout=None):
        raise httpx.ConnectError("connection refused")

    monkeypatch.setattr(triposr_worker_client.httpx, "get", fake_get)
    result = triposr_worker_client.detect(settings)
    assert result.available is False
    assert "unreachable" in result.message.lower()


def test_detect_unavailable_when_runtime_missing_on_worker(tmp_path, monkeypatch):
    settings = _settings(tmp_path)
    monkeypatch.setattr(
        triposr_worker_client.httpx,
        "get",
        lambda url, timeout=None: _mock_response(200, {"status": "healthy", "infer_script_present": False, "repo_present": True, "busy": False}),
    )
    result = triposr_worker_client.detect(settings)
    assert result.available is False


def test_detect_available_when_worker_healthy(tmp_path, monkeypatch):
    settings = _settings(tmp_path)
    monkeypatch.setattr(
        triposr_worker_client.httpx,
        "get",
        lambda url, timeout=None: _mock_response(200, {"status": "healthy", "infer_script_present": True, "repo_present": True, "busy": False}),
    )
    result = triposr_worker_client.detect(settings)
    assert result.available is True
    assert result.message is None


def test_detect_unavailable_when_disabled(tmp_path):
    settings = _settings(tmp_path, local_ai_3d_enabled=False)
    result = triposr_worker_client.detect(settings)
    assert result.available is False


# --- create_generation / get_generation_status (HTTP mocked) ------------------


def test_create_and_poll_succeeded(tmp_path, monkeypatch):
    settings = _settings(tmp_path)
    output_glb = None

    def fake_post(self, url, json=None):
        nonlocal output_glb
        output_glb = Path(json["output_glb"])
        output_glb.parent.mkdir(parents=True, exist_ok=True)
        output_glb.write_bytes(b"glb-bytes")
        return _mock_response(200, {"job_id": "worker-job-1", "running": True})

    def fake_get(self, url):
        return _mock_response(200, {"job_id": "worker-job-1", "running": False, "returncode": 0, "result": {"success": True, "output": str(output_glb)}})

    monkeypatch.setattr(httpx.Client, "post", fake_post)
    monkeypatch.setattr(httpx.Client, "get", fake_get)

    image = tmp_path / "image.jpg"
    image.write_bytes(b"fake-image")

    provider = triposr_worker_client.TripoSrWorkerProvider(settings)
    handle = provider.create_generation([image])
    result = provider.get_generation_status(handle.provider_task_id)

    assert result.status == STATUS_SUCCEEDED
    assert Path(result.glb_url).read_bytes() == b"glb-bytes"


def test_poll_still_running(tmp_path, monkeypatch):
    settings = _settings(tmp_path)
    monkeypatch.setattr(httpx.Client, "post", lambda self, url, json=None: _mock_response(200, {"job_id": "j1", "running": True}))
    monkeypatch.setattr(httpx.Client, "get", lambda self, url: _mock_response(200, {"job_id": "j1", "running": True}))

    provider = triposr_worker_client.TripoSrWorkerProvider(settings)
    handle = provider.create_generation([tmp_path / "image.jpg"])
    result = provider.get_generation_status(handle.provider_task_id)
    assert result.status == STATUS_IN_PROGRESS


def test_worker_busy_raises_provider_error(tmp_path, monkeypatch):
    settings = _settings(tmp_path)
    monkeypatch.setattr(httpx.Client, "post", lambda self, url, json=None: _mock_response(409, {"detail": "busy"}))

    provider = triposr_worker_client.TripoSrWorkerProvider(settings)
    with pytest.raises(Ai3DProviderError):
        provider.create_generation([tmp_path / "image.jpg"])


def test_worker_reports_failure(tmp_path, monkeypatch):
    settings = _settings(tmp_path)
    monkeypatch.setattr(httpx.Client, "post", lambda self, url, json=None: _mock_response(200, {"job_id": "j1", "running": True}))
    monkeypatch.setattr(
        httpx.Client,
        "get",
        lambda self, url: _mock_response(200, {"job_id": "j1", "running": False, "returncode": 1, "result": {"success": False, "message": "boom"}}),
    )

    provider = triposr_worker_client.TripoSrWorkerProvider(settings)
    handle = provider.create_generation([tmp_path / "image.jpg"])
    result = provider.get_generation_status(handle.provider_task_id)
    assert result.status == STATUS_FAILED
    assert "boom" in result.error_message


def test_create_generation_requires_jobs_dir_configured(tmp_path):
    settings = _settings(tmp_path, triposr_worker_jobs_dir="")
    provider = triposr_worker_client.TripoSrWorkerProvider(settings)
    with pytest.raises(Ai3DProviderError):
        provider.create_generation([tmp_path / "image.jpg"])


def test_create_generation_sends_remove_background_setting(tmp_path, monkeypatch):
    """Regression test for the real OOM fix: rembg's ~1GB model coexisting with TripoSR's own
    model pushed a real job over this VPS's memory ceiling (see compose.prod.yaml /
    TRIPOSR_REMOVE_BACKGROUND). The worker must actually receive whatever this backend is
    configured with, not silently default to True regardless."""
    settings = _settings(tmp_path, triposr_remove_background=False)
    sent_payload = {}

    def fake_post(self, url, json=None):
        sent_payload.update(json)
        return _mock_response(200, {"job_id": "j1", "running": True})

    monkeypatch.setattr(httpx.Client, "post", fake_post)

    provider = triposr_worker_client.TripoSrWorkerProvider(settings)
    provider.create_generation([tmp_path / "image.jpg"])

    assert sent_payload["remove_background"] is False


def test_download_result_copies_shared_volume_file(tmp_path):
    settings = _settings(tmp_path)
    provider = triposr_worker_client.TripoSrWorkerProvider(settings)
    source = tmp_path / "source.glb"
    source.write_bytes(b"shared-volume-bytes")
    destination = tmp_path / "nested" / "dest.glb"

    provider.download_result(str(source), destination)

    assert destination.read_bytes() == b"shared-volume-bytes"


# --- dispatch: worker mode wins over local-subprocess mode when configured ----


def test_dispatch_uses_worker_provider_when_url_configured(tmp_path, monkeypatch):
    settings = _settings(tmp_path)
    monkeypatch.setattr(
        triposr_worker_client.httpx,
        "get",
        lambda url, timeout=None: _mock_response(200, {"status": "healthy", "infer_script_present": True, "repo_present": True, "busy": False}),
    )

    availability = ai_provider_service.detect_ai_availability(settings)
    assert availability.available is True
    assert availability.provider == "triposr"
    assert isinstance(ai_provider_service.get_provider(settings), triposr_worker_client.TripoSrWorkerProvider)
