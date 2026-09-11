from __future__ import annotations

from pathlib import Path

import pytest
import trimesh
from fastapi.testclient import TestClient

from app.services.model3d import ai_generation_service, ai_provider_service, states
from app.services.model3d.ai_provider import (
    Ai3DProvider,
    Ai3DProviderError,
    Ai3DTaskHandle,
    Ai3DTaskResult,
    STATUS_FAILED,
    STATUS_IN_PROGRESS,
    STATUS_SUCCEEDED,
)
from app.utils import utc_now
from test_api import auth_headers, create_artifact, image_bytes
from test_model3d import _add_reconstruction_images, model3d_context  # noqa: F401 (fixture)


def _valid_textured_glb_bytes() -> bytes:
    mesh = trimesh.creation.box(extents=(1, 1, 1))
    mesh.visual = trimesh.visual.texture.TextureVisuals(
        material=trimesh.visual.material.PBRMaterial(
            baseColorFactor=[200, 150, 100, 255], metallicFactor=0.0, roughnessFactor=0.8
        )
    )
    return mesh.export(file_type="glb")


class FakeAiProvider(Ai3DProvider):
    """Stands in for MeshyProvider - no real network call, no paid credits consumed."""

    max_images = 4

    def __init__(
        self,
        *,
        poll_sequence: list[str] | None = None,
        glb_bytes: bytes | None = None,
        fail_stage: str | None = None,
    ) -> None:
        self.poll_sequence = poll_sequence if poll_sequence is not None else [STATUS_SUCCEEDED]
        self.glb_bytes = glb_bytes if glb_bytes is not None else _valid_textured_glb_bytes()
        self.fail_stage = fail_stage
        self._poll_index = 0
        self.created_with: list[Path] | None = None

    def create_generation(self, image_paths: list[Path]) -> Ai3DTaskHandle:
        if self.fail_stage == "create":
            raise Ai3DProviderError("mock: provider rejected the request")
        self.created_with = image_paths
        return Ai3DTaskHandle(provider_task_id="fake-task-1")

    def get_generation_status(self, task_id: str) -> Ai3DTaskResult:
        if self.fail_stage == "poll":
            raise Ai3DProviderError("mock: provider status check failed")
        outcome = self.poll_sequence[min(self._poll_index, len(self.poll_sequence) - 1)]
        self._poll_index += 1
        if outcome == STATUS_SUCCEEDED:
            return Ai3DTaskResult(status=STATUS_SUCCEEDED, progress=100, glb_url="https://fake.example/model.glb")
        if outcome == STATUS_FAILED:
            return Ai3DTaskResult(status=STATUS_FAILED, error_message="mock: provider says generation failed")
        return Ai3DTaskResult(status=STATUS_IN_PROGRESS, progress=40)

    def download_result(self, glb_url: str, destination: Path) -> None:
        if self.fail_stage == "download":
            raise Ai3DProviderError("mock: download failed")
        destination.parent.mkdir(parents=True, exist_ok=True)
        if self.fail_stage == "invalid_glb":
            destination.write_bytes(b"not a real glb file")
        else:
            destination.write_bytes(self.glb_bytes)


def _enable_ai(settings, *, provider: FakeAiProvider | None = None, monkeypatch=None) -> FakeAiProvider:
    settings.ai_3d_enabled = True
    settings.ai_3d_api_key = "fake-test-key"
    fake_provider = provider or FakeAiProvider()
    if monkeypatch is not None:
        monkeypatch.setattr(ai_provider_service, "get_provider", lambda settings: fake_provider)
    return fake_provider


def _run_ai_worker_synchronously() -> None:
    ai_generation_service.set_worker_starter(
        lambda database, settings, artifact_id, job_id, relative_image_paths: ai_generation_service.run(
            database, settings, artifact_id, job_id, relative_image_paths
        )
    )


def _reconstruction_image_ids(client: TestClient, headers: dict, artifact_id: str) -> list[str]:
    state = client.get(f"/api/v1/artifacts/{artifact_id}/3d", headers=headers).json()
    return [image["id"] for image in state["images"]]


# --- Availability / configuration --------------------------------------------


def test_ai_unavailable_by_default_no_credentials_configured(model3d_context):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)

    response = client.get(f"/api/v1/artifacts/{artifact['id']}/3d", headers=headers)
    assert response.status_code == 200
    body = response.json()
    assert body["ai_available"] is False
    assert body["ai_max_images"] is None


def test_ai_available_when_enabled_and_configured(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    _enable_ai(settings, monkeypatch=monkeypatch)

    response = client.get(f"/api/v1/artifacts/{artifact['id']}/3d", headers=headers)
    body = response.json()
    assert body["ai_available"] is True
    assert body["ai_max_images"] == 4


def test_museum_starts_normally_with_ai_unconfigured(model3d_context):
    """AI 3D must be strictly optional - existing COLMAP/artifact endpoints must not care."""
    client, database, settings = model3d_context
    headers = auth_headers(client)
    assert settings.ai_3d_enabled is False
    response = client.get("/api/v1/health")
    assert response.status_code == 200
    assert client.get("/api/v1/artifacts", headers=headers).status_code == 200


# --- Request validation ---------------------------------------------------------


def test_build_ai_rejected_when_ai_disabled(model3d_context):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=3)
    image_ids = _reconstruction_image_ids(client, headers, artifact_id)

    response = client.post(f"/api/v1/artifacts/{artifact_id}/3d/build-ai", json={"image_ids": image_ids}, headers=headers)
    assert response.status_code == 503


def test_build_ai_rejected_with_no_images_selected(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _enable_ai(settings, monkeypatch=monkeypatch)

    response = client.post(f"/api/v1/artifacts/{artifact_id}/3d/build-ai", json={"image_ids": []}, headers=headers)
    assert response.status_code == 422


def test_build_ai_rejected_over_provider_image_cap(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=5)
    image_ids = _reconstruction_image_ids(client, headers, artifact_id)
    _enable_ai(settings, monkeypatch=monkeypatch)  # FakeAiProvider.max_images == 4

    response = client.post(f"/api/v1/artifacts/{artifact_id}/3d/build-ai", json={"image_ids": image_ids}, headers=headers)
    assert response.status_code == 422
    assert "4" in response.json()["detail"]


def test_build_ai_rejected_for_unknown_image_id(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _enable_ai(settings, monkeypatch=monkeypatch)

    response = client.post(
        f"/api/v1/artifacts/{artifact_id}/3d/build-ai",
        json={"image_ids": ["000000000000000000000000"]},
        headers=headers,
    )
    assert response.status_code == 404


def test_real_provider_rejects_more_images_than_its_documented_max():
    """Exercises the actual MeshyProvider class (not the fake) against its real, documented
    request-shape constraint - Meshy's Multi-Image to 3D API accepts 1 to 4 images."""
    from app.services.model3d.meshy_provider import MeshyProvider

    provider = MeshyProvider(api_key="unused-in-this-test")
    with pytest.raises(ValueError):
        provider.create_generation([Path("a.jpg"), Path("b.jpg"), Path("c.jpg"), Path("d.jpg"), Path("e.jpg")])


def test_real_provider_rejects_empty_image_list():
    from app.services.model3d.meshy_provider import MeshyProvider

    provider = MeshyProvider(api_key="unused-in-this-test")
    with pytest.raises(ValueError):
        provider.create_generation([])


# --- Background job lifecycle -----------------------------------------------------


def test_ai_build_reaches_pending_review_with_generation_method(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=3)
    image_ids = _reconstruction_image_ids(client, headers, artifact_id)
    provider = _enable_ai(settings, monkeypatch=monkeypatch)
    monkeypatch.setattr(ai_generation_service, "AI_POLL_INTERVAL_SECONDS", 0)
    _run_ai_worker_synchronously()

    build = client.post(f"/api/v1/artifacts/{artifact_id}/3d/build-ai", json={"image_ids": image_ids}, headers=headers)
    assert build.status_code == 202, build.text

    state = client.get(f"/api/v1/artifacts/{artifact_id}/3d", headers=headers).json()
    assert state["status"] == "pending_review"
    assert state["draft_version"] == 1
    assert state["draft_generation_method"] == "ai_multiview"
    assert len(state["draft_sha256"]) == 64
    assert state["draft_size_bytes"] > 0
    assert any("AI-generated" in line for line in state["guidance"])
    assert provider.created_with is not None
    assert len(provider.created_with) == 3


def test_published_model_url_stays_available_while_a_new_draft_is_pending_review(model3d_context, monkeypatch):
    """Regression test: model_url must reflect the PUBLISHED model whenever one exists, not just
    when status == ready - an admin (or Visitor) must still be able to reference/preview the
    currently published version while a newer draft is generating or awaiting review."""
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=3)
    image_ids = _reconstruction_image_ids(client, headers, artifact_id)
    _enable_ai(settings, monkeypatch=monkeypatch)
    monkeypatch.setattr(ai_generation_service, "AI_POLL_INTERVAL_SECONDS", 0)
    _run_ai_worker_synchronously()

    # Publish v1.
    client.post(f"/api/v1/artifacts/{artifact_id}/3d/build-ai", json={"image_ids": image_ids}, headers=headers)
    accepted = client.post(f"/api/v1/artifacts/{artifact_id}/3d/accept", headers=headers).json()
    assert accepted["status"] == "ready"
    published_url = accepted["model_url"]
    assert published_url

    # Start a second generation - reaches pending_review while v1 stays published.
    client.post(f"/api/v1/artifacts/{artifact_id}/3d/build-ai", json={"image_ids": image_ids}, headers=headers)
    state = client.get(f"/api/v1/artifacts/{artifact_id}/3d", headers=headers).json()

    assert state["status"] == "pending_review"
    assert state["model_url"] == published_url  # unchanged, still the v1 published model
    assert state["draft_model_url"] is not None
    assert state["draft_model_url"] != published_url


def test_ai_build_rejected_while_another_job_is_active(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=3)
    image_ids = _reconstruction_image_ids(client, headers, artifact_id)
    _enable_ai(settings, monkeypatch=monkeypatch)

    # Never-completing worker: leaves the job "stuck" active.
    ai_generation_service.set_worker_starter(lambda *a, **k: None)
    first = client.post(f"/api/v1/artifacts/{artifact_id}/3d/build-ai", json={"image_ids": image_ids}, headers=headers)
    assert first.status_code == 202

    second = client.post(f"/api/v1/artifacts/{artifact_id}/3d/build-ai", json={"image_ids": image_ids}, headers=headers)
    assert second.status_code == 409


@pytest.mark.parametrize(
    "fail_stage,expected_error_snippet",
    [
        ("create", "provider rejected"),
        ("poll", "status check failed"),
        ("download", "download failed"),
    ],
)
def test_provider_failure_persists_as_failed_job(model3d_context, monkeypatch, fail_stage, expected_error_snippet):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=3)
    image_ids = _reconstruction_image_ids(client, headers, artifact_id)
    _enable_ai(settings, provider=FakeAiProvider(fail_stage=fail_stage), monkeypatch=monkeypatch)
    monkeypatch.setattr(ai_generation_service, "AI_POLL_INTERVAL_SECONDS", 0)
    _run_ai_worker_synchronously()

    build = client.post(f"/api/v1/artifacts/{artifact_id}/3d/build-ai", json={"image_ids": image_ids}, headers=headers)
    assert build.status_code == 202

    status_response = client.get(f"/api/v1/artifacts/{artifact_id}/3d/status", headers=headers).json()
    assert status_response["job"]["status"] == "failed"
    assert expected_error_snippet in status_response["job"]["error"]
    assert status_response["state"]["status"] == "failed"  # no prior published model to fall back to
    assert status_response["state"]["draft_version"] is None


def test_invalid_provider_glb_is_rejected_not_published(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=3)
    image_ids = _reconstruction_image_ids(client, headers, artifact_id)
    _enable_ai(settings, provider=FakeAiProvider(fail_stage="invalid_glb"), monkeypatch=monkeypatch)
    monkeypatch.setattr(ai_generation_service, "AI_POLL_INTERVAL_SECONDS", 0)
    _run_ai_worker_synchronously()

    client.post(f"/api/v1/artifacts/{artifact_id}/3d/build-ai", json={"image_ids": image_ids}, headers=headers)

    status_response = client.get(f"/api/v1/artifacts/{artifact_id}/3d/status", headers=headers).json()
    assert status_response["job"]["status"] == "failed"
    assert "validation" in status_response["job"]["error"].lower()
    assert status_response["state"]["draft_version"] is None


def test_failed_ai_job_preserves_existing_published_model(model3d_context, monkeypatch):
    """The core protection: a bad AI attempt must never take down a working published model."""
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers, with_image=True)
    artifact_id = artifact["id"]

    # Publish a real model directly via the repository, exactly as an accepted COLMAP/AI draft
    # would look - avoids depending on the COLMAP pipeline just to set up this AI-focused test.
    from bson import ObjectId

    from app.repositories import reconstruction_repository as repo

    repo.update_model_3d_state(
        database, ObjectId(artifact_id),
        {
            "status": states.READY,
            "version": 1,
            "path": f"uploads/models3d/{artifact_id}/model-v1.glb",
            "sha256": "a" * 64,
            "size_bytes": 4096,
            "created_at": utc_now(),
            "generation_method": states.GENERATION_COLMAP,
        },
    )

    _add_reconstruction_images(client, headers, artifact_id, count=3)
    image_ids = _reconstruction_image_ids(client, headers, artifact_id)
    _enable_ai(settings, provider=FakeAiProvider(fail_stage="download"), monkeypatch=monkeypatch)
    monkeypatch.setattr(ai_generation_service, "AI_POLL_INTERVAL_SECONDS", 0)
    _run_ai_worker_synchronously()

    client.post(f"/api/v1/artifacts/{artifact_id}/3d/build-ai", json={"image_ids": image_ids}, headers=headers)

    state = client.get(f"/api/v1/artifacts/{artifact_id}/3d", headers=headers).json()
    assert state["status"] == "ready"  # fell back to the existing published model, not "failed"
    assert state["version"] == 1
    assert state["sha256"] == "a" * 64
    assert state["failure_message"]  # still surfaced for the admin to see


# --- Admin review is mandatory; Visitor only sees accepted models -----------------


def test_ai_candidate_requires_admin_acceptance_before_visitor_sees_it(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers, code="ART-AI-REVIEW", with_image=True)
    artifact_id = artifact["id"]
    client.patch(f"/api/v1/artifacts/{artifact_id}", data={"status": "published"}, headers=headers)
    _add_reconstruction_images(client, headers, artifact_id, count=3)
    image_ids = _reconstruction_image_ids(client, headers, artifact_id)
    _enable_ai(settings, monkeypatch=monkeypatch)
    monkeypatch.setattr(ai_generation_service, "AI_POLL_INTERVAL_SECONDS", 0)
    _run_ai_worker_synchronously()

    client.post(f"/api/v1/artifacts/{artifact_id}/3d/build-ai", json={"image_ids": image_ids}, headers=headers)
    pending = client.get(f"/api/v1/artifacts/{artifact_id}/3d", headers=headers).json()
    assert pending["status"] == "pending_review"
    draft_sha = pending["draft_sha256"]

    guest_session = database.guest_sessions.insert_one({"role": "guest", "created_at": utc_now(), "expires_at": None}).inserted_id
    from app.auth.jwt_handler import create_access_token as make_token

    guest_token, _ = make_token(str(guest_session), "guest@example.com", "guest", settings)
    guest_headers = {"Authorization": f"Bearer {guest_token}"}

    # Never silently published: Visitor must not see the AI draft yet.
    before_accept = client.get(f"/api/v1/visitor/artifacts/{artifact_id}", headers=guest_headers).json()
    assert before_accept["model_3d_available"] is False

    accept = client.post(f"/api/v1/artifacts/{artifact_id}/3d/accept", headers=headers)
    assert accept.status_code == 200
    published = accept.json()
    assert published["status"] == "ready"
    assert published["sha256"] == draft_sha
    assert published["generation_method"] == "ai_multiview"

    after_accept = client.get(f"/api/v1/visitor/artifacts/{artifact_id}", headers=guest_headers).json()
    assert after_accept["model_3d_available"] is True
    assert after_accept["model_3d_sha256"] == draft_sha


def test_ai_candidate_rejection_discards_draft(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=3)
    image_ids = _reconstruction_image_ids(client, headers, artifact_id)
    _enable_ai(settings, monkeypatch=monkeypatch)
    monkeypatch.setattr(ai_generation_service, "AI_POLL_INTERVAL_SECONDS", 0)
    _run_ai_worker_synchronously()
    client.post(f"/api/v1/artifacts/{artifact_id}/3d/build-ai", json={"image_ids": image_ids}, headers=headers)

    reject = client.post(f"/api/v1/artifacts/{artifact_id}/3d/reject", headers=headers)
    assert reject.status_code == 200
    body = reject.json()
    assert body["draft_version"] is None
    assert body["draft_generation_method"] is None
