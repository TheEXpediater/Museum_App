from __future__ import annotations

import io
import shutil
import subprocess
from pathlib import Path

import mongomock
import numpy as np
import pytest
import trimesh
from fastapi.testclient import TestClient

from app.auth.jwt_handler import create_access_token
from app.auth.password import hash_password
from app.config import Settings
from app.repositories import reconstruction_repository as repo
from app.services.model3d import colmap_pipeline as pipeline
from app.services.model3d import colmap_service, reconstruction_service, states
from app.services.model3d.colmap_service import ColmapAvailability
from app.utils import to_object_id, utc_now
from main import create_app
from test_api import ADMIN_EMAIL, ADMIN_PASSWORD, auth_headers, create_artifact, image_bytes, login


@pytest.fixture()
def model3d_context(tmp_path):
    settings = Settings(
        app_name="Museum Guide System Test",
        app_env="test",
        mongodb_url="mongodb://localhost:27017",
        mongodb_database="museum_guide_test",
        jwt_secret_key="test-secret-key-that-is-long-enough",
        upload_directory=str(tmp_path / "uploads" / "images"),
        reconstruction_directory=str(tmp_path / "uploads" / "reconstruction"),
        model_3d_directory=str(tmp_path / "uploads" / "models3d"),
        max_image_size_mb=1,
        ai_enabled=False,
        # Deterministic baseline regardless of what happens to be installed on the machine
        # running these tests: this dev machine has a real tools/triposr/ runtime set up (see
        # LOCAL_AI_3D_ENABLED's class default), which would otherwise make
        # triposr_provider.detect() return available=True purely by filesystem coincidence.
        # Tests that want the local-AI-available path use _mock_triposr_available(monkeypatch).
        local_ai_3d_enabled=False,
        cors_origins="http://testserver",
        _env_file=None,
    )
    database = mongomock.MongoClient()[settings.mongodb_database]
    app = create_app(settings=settings, database=database)
    with TestClient(app) as client:
        database.users.insert_one(
            {
                "email": ADMIN_EMAIL,
                "full_name": "Museum Administrator",
                "password_hash": hash_password(ADMIN_PASSWORD),
                "role": "admin",
                "is_active": True,
                "created_at": utc_now(),
                "updated_at": utc_now(),
            }
        )
        yield client, database, settings
    reconstruction_service.set_worker_starter(None)


def _fake_colored_mesh(path: Path) -> None:
    mesh = trimesh.creation.box(extents=(1, 1, 1))
    colors = np.tile([120, 140, 160, 255], (len(mesh.vertices), 1)).astype("uint8")
    mesh.visual.vertex_colors = colors
    mesh.export(path)


def _patch_pipeline_success(monkeypatch, *, registered_ratio: float = 1.0, point_count: int = 1200):
    def fake_feature_extraction(bin_path, *, database_path, image_path, use_gpu, max_dimension, num_threads, max_features, log_file):
        database_path.parent.mkdir(parents=True, exist_ok=True)
        database_path.touch()

    def fake_matching(bin_path, *, database_path, use_gpu, num_threads, log_file):
        pass

    def fake_mapper(bin_path, *, database_path, image_path, sparse_path, log_file):
        (sparse_path / "0").mkdir(parents=True, exist_ok=True)

    def fake_analyze(bin_path, *, sparse_model_path, log_file):
        # sparse_model_path is <root>/workspace[/preflight]/sparse/0 - climb back to find the
        # artifact's <root>/source directory regardless of preflight vs. build nesting depth.
        source_dir = None
        current = sparse_model_path
        for _ in range(6):
            candidate = current / "source"
            if candidate.is_dir():
                source_dir = candidate
                break
            current = current.parent
        source_count = len(list(source_dir.glob("*"))) if source_dir else 0
        registered = max(int(source_count * registered_ratio), 0)
        return pipeline.SparseStats(registered_image_count=registered, sparse_point_count=point_count, mean_reprojection_error=0.55)

    def fake_sparse_mesher(bin_path, *, sparse_model_path, output_ply, log_file):
        output_ply.parent.mkdir(parents=True, exist_ok=True)
        _fake_colored_mesh(output_ply)

    monkeypatch.setattr(pipeline, "run_feature_extraction", fake_feature_extraction)
    monkeypatch.setattr(pipeline, "run_matching", fake_matching)
    monkeypatch.setattr(pipeline, "run_mapper", fake_mapper)
    monkeypatch.setattr(pipeline, "analyze_sparse_model", fake_analyze)
    monkeypatch.setattr(pipeline, "run_sparse_mesher", fake_sparse_mesher)


def _mock_colmap_available(monkeypatch) -> None:
    monkeypatch.setattr(
        colmap_service,
        "detect_colmap",
        lambda settings: ColmapAvailability(available=True, bin_path="colmap-mock", version="3.9", message=None),
    )


def _run_worker_synchronously() -> None:
    reconstruction_service.set_worker_starter(
        lambda database, settings, artifact_id, job_id: __import__(
            "app.services.model3d.reconstruction_worker", fromlist=["run"]
        ).run(database, settings, artifact_id, job_id)
    )


def _add_reconstruction_images(client: TestClient, headers: dict, artifact_id: str, count: int) -> None:
    files = [("images", (f"recon-{i}.jpg", image_bytes(color=(i * 10 % 255, 40, 80)), "image/jpeg")) for i in range(count)]
    response = client.post(f"/api/v1/artifacts/{artifact_id}/3d/images", files=files, headers=headers)
    assert response.status_code == 200, response.text


# --- Source image separation -------------------------------------------------


def test_reconstruction_images_do_not_affect_artifact_gallery_or_ai_fields(model3d_context):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers, with_image=True)
    artifact_id = artifact["id"]
    original_image_paths = artifact["image_paths"]
    original_ai_status = artifact["ai_index_status"]

    reuse_response = client.post(
        f"/api/v1/artifacts/{artifact_id}/3d/images",
        data={"reuse_image_paths": original_image_paths[0]},
        files=[("images", ("extra.jpg", image_bytes(color=(9, 9, 9)), "image/jpeg"))],
        headers=headers,
    )
    assert reuse_response.status_code == 200, reuse_response.text
    state = reuse_response.json()
    assert state["source_image_count"] == 2
    origins = {image["origin"] for image in state["images"]}
    assert origins == {"reused", "uploaded"}

    refreshed = client.get(f"/api/v1/artifacts/{artifact_id}", headers=headers).json()
    assert refreshed["image_paths"] == original_image_paths
    assert refreshed["ai_index_status"] == original_ai_status
    assert refreshed["visitor_gallery_image_paths"] == artifact["visitor_gallery_image_paths"]


def test_reuse_image_path_must_belong_to_artifact(model3d_context):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers, with_image=True)
    response = client.post(
        f"/api/v1/artifacts/{artifact['id']}/3d/images",
        data={"reuse_image_paths": "uploads/images/does-not-belong.jpg"},
        headers=headers,
    )
    assert response.status_code == 422


# --- Authorization and not-found ---------------------------------------------


def test_3d_endpoints_require_admin(model3d_context):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]

    no_auth = client.get(f"/api/v1/artifacts/{artifact_id}/3d")
    assert no_auth.status_code == 401

    visitor_id = database.users.insert_one(
        {
            "email": "visitor@example.com",
            "full_name": "Visitor",
            "password_hash": hash_password("VisitorPassword123!"),
            "role": "visitor",
            "is_active": True,
            "created_at": utc_now(),
            "updated_at": utc_now(),
        }
    ).inserted_id
    token, _ = create_access_token(str(visitor_id), "visitor@example.com", "visitor", settings)
    forbidden = client.get(f"/api/v1/artifacts/{artifact_id}/3d", headers={"Authorization": f"Bearer {token}"})
    assert forbidden.status_code == 403


def test_3d_state_for_nonexistent_artifact_is_404(model3d_context):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    response = client.get("/api/v1/artifacts/000000000000000000000000/3d", headers=headers)
    assert response.status_code == 404


# --- Preflight -----------------------------------------------------------------


def test_preflight_with_too_few_images_returns_needs_images_without_running_colmap(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=2)

    def fail_if_called(*args, **kwargs):
        raise AssertionError("COLMAP should not run below the absolute minimum image floor.")

    monkeypatch.setattr(pipeline, "run_feature_extraction", fail_if_called)

    response = client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "needs_images"
    assert body["guidance"]


def test_preflight_with_exactly_three_images_attempts_reconstruction(model3d_context, monkeypatch):
    """Three is the product minimum to ATTEMPT a preview - not a guarantee of success."""
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=3)

    _mock_colmap_available(monkeypatch)
    _patch_pipeline_success(monkeypatch, registered_ratio=1.0, point_count=50)

    response = client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ready_for_build"
    assert body["registered_image_count"] == 3


def test_preflight_ready_for_build_when_registration_is_good(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=10)

    _mock_colmap_available(monkeypatch)
    _patch_pipeline_success(monkeypatch, registered_ratio=1.0, point_count=1500)

    response = client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ready_for_build"
    assert body["registered_image_count"] == 10
    assert body["sparse_point_count"] == 1500


def test_preflight_ready_for_build_from_a_partial_low_ratio_reconstruction(model3d_context, monkeypatch):
    """This is a best-effort preview feature: a low registered-image ratio alone must not block
    an otherwise usable partial reconstruction from reaching admin review."""
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=10)

    _mock_colmap_available(monkeypatch)
    _patch_pipeline_success(monkeypatch, registered_ratio=0.3, point_count=1500)

    response = client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ready_for_build"
    assert body["registered_image_count"] == 3
    assert body["registered_image_ratio"] == 0.3
    # Status is unaffected (asserted above), but the new quality gate must still flag this as
    # low quality so Admin UI can offer the AI fallback - see quality.py.
    assert body["quality_assessment"] == "insufficient"
    assert body["quality_reasons"]


def test_thirty_source_three_registered_is_classified_insufficient_quality(model3d_context, monkeypatch):
    """The exact real-world scenario this quality gate exists for: COLMAP successfully produces
    a loadable GLB from 3 of 30 registered photos, but that must not be treated as a complete
    reconstruction merely because a GLB exists - see CLAUDE.MD's Salakot example."""
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=30)

    _mock_colmap_available(monkeypatch)
    _patch_pipeline_success(monkeypatch, registered_ratio=3 / 30, point_count=418)

    response = client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)
    assert response.status_code == 200
    body = response.json()
    # Still attemptable (existing sanity gate: 3 registered >= MIN_USABLE_REGISTERED_IMAGES) -
    # this must not regress.
    assert body["status"] == "ready_for_build"
    assert body["registered_image_count"] == 3
    assert body["source_image_count"] == 30
    # But NOT good quality - this is the actual new behavior under test.
    assert body["quality_assessment"] == "insufficient"
    assert any("3 of 30" in reason for reason in body["quality_reasons"])


def test_high_registration_ratio_is_classified_good_quality(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=10)

    _mock_colmap_available(monkeypatch)
    _patch_pipeline_success(monkeypatch, registered_ratio=1.0, point_count=1500)

    response = client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)
    body = response.json()
    assert body["status"] == "ready_for_build"
    assert body["quality_assessment"] == "good"
    assert body["quality_reasons"] == []


def test_preflight_needs_images_when_too_few_images_registered(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=10)

    _mock_colmap_available(monkeypatch)
    # Below MIN_USABLE_REGISTERED_IMAGES (2): not enough posed images for any real geometry.
    _patch_pipeline_success(monkeypatch, registered_ratio=0.1, point_count=1500)

    response = client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "needs_images"
    assert len(body["guidance"]) > 0


def test_preflight_needs_images_when_sparse_points_too_few(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=10)

    _mock_colmap_available(monkeypatch)
    # Registered images are fine, but the point cloud is degenerately small.
    _patch_pipeline_success(monkeypatch, registered_ratio=1.0, point_count=3)

    response = client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "needs_images"
    assert len(body["guidance"]) > 0


# --- Build job ------------------------------------------------------------------


def test_build_rejected_when_not_ready(model3d_context):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=10)

    response = client.post(f"/api/v1/artifacts/{artifact_id}/3d/build", headers=headers)
    assert response.status_code == 409


def test_build_rejected_while_another_job_is_active(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=10)
    _mock_colmap_available(monkeypatch)
    _patch_pipeline_success(monkeypatch)
    client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)

    # Never-completing worker: leaves the job "stuck" active so a second build is rejected.
    reconstruction_service.set_worker_starter(lambda *a, **k: None)
    first = client.post(f"/api/v1/artifacts/{artifact_id}/3d/build", headers=headers)
    assert first.status_code == 202

    second = client.post(f"/api/v1/artifacts/{artifact_id}/3d/build", headers=headers)
    assert second.status_code == 409


def test_full_build_reaches_pending_review_not_immediately_published(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers, with_image=True)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=10)
    _mock_colmap_available(monkeypatch)
    _patch_pipeline_success(monkeypatch)

    preflight = client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)
    assert preflight.json()["status"] == "ready_for_build"

    _run_worker_synchronously()
    build = client.post(f"/api/v1/artifacts/{artifact_id}/3d/build", headers=headers)
    assert build.status_code == 202

    status_response = client.get(f"/api/v1/artifacts/{artifact_id}/3d/status", headers=headers)
    body = status_response.json()
    # A finished build is a DRAFT awaiting admin review, not a published model.
    assert body["state"]["status"] == "pending_review"
    assert body["state"]["version"] == 0
    assert body["state"]["sha256"] is None
    assert body["state"]["model_url"] is None
    assert body["state"]["draft_version"] == 1
    assert len(body["state"]["draft_sha256"]) == 64
    assert body["state"]["draft_size_bytes"] > 0
    assert body["state"]["draft_model_url"] is not None
    assert body["job"]["status"] == "pending_review"

    draft_path = settings.model_3d_root_path / artifact_id / "model-v1.glb"
    assert draft_path.is_file()


def test_full_build_from_weak_reconstruction_is_flagged_insufficient_quality(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers, with_image=True)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=30)
    _mock_colmap_available(monkeypatch)
    _patch_pipeline_success(monkeypatch, registered_ratio=3 / 30, point_count=418)

    preflight = client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)
    assert preflight.json()["status"] == "ready_for_build"

    _run_worker_synchronously()
    build = client.post(f"/api/v1/artifacts/{artifact_id}/3d/build", headers=headers)
    assert build.status_code == 202

    state = client.get(f"/api/v1/artifacts/{artifact_id}/3d", headers=headers).json()
    assert state["status"] == "pending_review"  # draft is NOT deleted/hidden - kept for review
    assert state["draft_version"] == 1
    assert state["draft_generation_method"] == "colmap"
    assert state["quality_assessment"] == "insufficient"
    assert state["quality_reasons"]
    # Diagnostics remain available: the draft GLB is still a real, downloadable file.
    draft_path = settings.model_3d_root_path / artifact_id / "model-v1.glb"
    assert draft_path.is_file()


def test_accept_publishes_draft_model(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=10)
    _mock_colmap_available(monkeypatch)
    _patch_pipeline_success(monkeypatch)
    client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)
    _run_worker_synchronously()
    client.post(f"/api/v1/artifacts/{artifact_id}/3d/build", headers=headers)

    pending = client.get(f"/api/v1/artifacts/{artifact_id}/3d", headers=headers).json()
    draft_sha = pending["draft_sha256"]
    assert pending["status"] == "pending_review"

    accept = client.post(f"/api/v1/artifacts/{artifact_id}/3d/accept", headers=headers)
    assert accept.status_code == 200, accept.text
    body = accept.json()
    assert body["status"] == "ready"
    assert body["version"] == 1
    assert body["sha256"] == draft_sha
    assert body["model_url"] is not None
    assert body["draft_version"] is None
    assert body["draft_model_url"] is None


def test_reject_discards_draft_without_exposing_it(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers, code="ART-REJECT", with_image=True)
    artifact_id = artifact["id"]
    client.patch(f"/api/v1/artifacts/{artifact_id}", data={"status": "published"}, headers=headers)
    _add_reconstruction_images(client, headers, artifact_id, count=10)
    _mock_colmap_available(monkeypatch)
    _patch_pipeline_success(monkeypatch)
    client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)
    _run_worker_synchronously()
    client.post(f"/api/v1/artifacts/{artifact_id}/3d/build", headers=headers)

    draft_path = settings.model_3d_root_path / artifact_id / "model-v1.glb"
    assert draft_path.is_file()

    guest_session = database.guest_sessions.insert_one(
        {"role": "guest", "created_at": utc_now(), "expires_at": None}
    ).inserted_id
    from app.auth.jwt_handler import create_access_token as make_token

    guest_token, _ = make_token(str(guest_session), "guest@example.com", "guest", settings)
    guest_headers = {"Authorization": f"Bearer {guest_token}"}

    reject = client.post(f"/api/v1/artifacts/{artifact_id}/3d/reject", headers=headers)
    assert reject.status_code == 200, reject.text
    body = reject.json()
    assert body["status"] == "needs_images"  # no previously accepted model to fall back to
    assert body["draft_version"] is None
    assert not draft_path.is_file()

    visitor_view = client.get(f"/api/v1/visitor/artifacts/{artifact_id}", headers=guest_headers).json()
    assert visitor_view["model_3d_available"] is False

    # Rejecting again (nothing pending) is a conflict, not a silent no-op.
    second_reject = client.post(f"/api/v1/artifacts/{artifact_id}/3d/reject", headers=headers)
    assert second_reject.status_code == 409


def test_visitor_artifact_exposes_model_only_after_accept(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers, code="ART-VISIBLE", with_image=True)
    artifact_id = artifact["id"]
    publish = client.patch(f"/api/v1/artifacts/{artifact_id}", data={"status": "published"}, headers=headers)
    assert publish.status_code == 200, publish.text
    _add_reconstruction_images(client, headers, artifact_id, count=10)
    _mock_colmap_available(monkeypatch)
    _patch_pipeline_success(monkeypatch)
    client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)

    guest_session = database.guest_sessions.insert_one(
        {"role": "guest", "created_at": utc_now(), "expires_at": None}
    ).inserted_id
    from app.auth.jwt_handler import create_access_token as make_token

    guest_token, _ = make_token(str(guest_session), "guest@example.com", "guest", settings)
    guest_headers = {"Authorization": f"Bearer {guest_token}"}

    before = client.get(f"/api/v1/visitor/artifacts/{artifact_id}", headers=guest_headers)
    assert before.status_code == 200
    assert before.json()["model_3d_available"] is False

    _run_worker_synchronously()
    client.post(f"/api/v1/artifacts/{artifact_id}/3d/build", headers=headers)

    # A draft awaiting review must never reach the visitor.
    still_hidden = client.get(f"/api/v1/visitor/artifacts/{artifact_id}", headers=guest_headers)
    assert still_hidden.json()["model_3d_available"] is False

    client.post(f"/api/v1/artifacts/{artifact_id}/3d/accept", headers=headers)

    after = client.get(f"/api/v1/visitor/artifacts/{artifact_id}", headers=guest_headers)
    body = after.json()
    assert body["model_3d_available"] is True
    assert body["model_3d_version"] == 1
    assert body["model_3d_sha256"]
    assert body["model_3d_url"].endswith(".glb")


def test_failed_rebuild_preserves_previous_accepted_model(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=10)
    _mock_colmap_available(monkeypatch)
    _patch_pipeline_success(monkeypatch)
    client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)

    _run_worker_synchronously()
    client.post(f"/api/v1/artifacts/{artifact_id}/3d/build", headers=headers)
    accept = client.post(f"/api/v1/artifacts/{artifact_id}/3d/accept", headers=headers)
    first_status = accept.json()
    assert first_status["status"] == "ready"
    original_sha = first_status["sha256"]
    original_path = settings.model_3d_root_path / artifact_id / "model-v1.glb"
    assert original_path.is_file()

    def broken_mesher(bin_path, *, sparse_model_path, output_ply, log_file):
        raise pipeline.ColmapStageError("delaunay_mesher", "simulated meshing failure")

    monkeypatch.setattr(pipeline, "run_sparse_mesher", broken_mesher)
    client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)
    client.post(f"/api/v1/artifacts/{artifact_id}/3d/build", headers=headers)

    second_status = client.get(f"/api/v1/artifacts/{artifact_id}/3d/status", headers=headers).json()
    assert second_status["job"]["status"] == "failed"
    assert second_status["state"]["status"] == "ready"
    assert second_status["state"]["sha256"] == original_sha
    assert second_status["state"]["version"] == 1
    assert original_path.is_file()


# --- Image / reconstruction deletion -------------------------------------------


def test_delete_reconstruction_image_removes_file_and_record(model3d_context):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=1)

    state = client.get(f"/api/v1/artifacts/{artifact_id}/3d", headers=headers).json()
    image_id = state["images"][0]["id"]
    document = repo.get_reconstruction_image(database, to_object_id(artifact_id), to_object_id(image_id))
    full_path = settings.reconstruction_root_path / document["relative_path"]
    assert full_path.is_file()

    response = client.delete(f"/api/v1/artifacts/{artifact_id}/3d/images/{image_id}", headers=headers)
    assert response.status_code == 200
    assert response.json()["source_image_count"] == 0
    assert not full_path.is_file()


def test_delete_all_reconstruction_resets_state(model3d_context, monkeypatch):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = artifact["id"]
    _add_reconstruction_images(client, headers, artifact_id, count=10)
    _mock_colmap_available(monkeypatch)
    _patch_pipeline_success(monkeypatch)
    client.post(f"/api/v1/artifacts/{artifact_id}/3d/preflight", headers=headers)
    _run_worker_synchronously()
    client.post(f"/api/v1/artifacts/{artifact_id}/3d/build", headers=headers)

    response = client.delete(f"/api/v1/artifacts/{artifact_id}/3d", headers=headers)
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "none"
    assert body["source_image_count"] == 0
    assert not (settings.reconstruction_root_path / artifact_id).exists()
    assert not (settings.model_3d_root_path / artifact_id).exists()


# --- Startup reconciliation -----------------------------------------------------


def test_reconcile_abandoned_jobs_marks_interrupted(model3d_context):
    client, database, settings = model3d_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers)
    artifact_id = to_object_id(artifact["id"])

    job = repo.create_job(database, artifact_id=artifact_id, target_version=1, source_image_count=10)
    repo.update_job(database, job["_id"], {"status": states.SPARSE_RECONSTRUCTION})
    repo.update_model_3d_state(database, artifact_id, {"status": states.SPARSE_RECONSTRUCTION})

    repo.reconcile_abandoned_jobs(database)

    reconciled_job = repo.get_job(database, job["_id"])
    assert reconciled_job["status"] == states.INTERRUPTED

    from app.repositories import artifact_repository

    reconciled_artifact = artifact_repository.get_artifact(database, artifact_id)
    assert repo.get_model_3d_state(reconciled_artifact)["status"] == states.INTERRUPTED


# --- Low-resource CLI configuration ---------------------------------------------


def test_feature_extraction_command_applies_low_resource_limits(monkeypatch, tmp_path):
    """COLMAP crashed outright on a real 8GB/4-core CPU-only machine with default (all-core)
    threading at full image resolution; this locks in that the configured image-size, feature
    count, and thread limits actually reach the CLI invocation."""
    captured: dict = {}

    def fake_supports(bin_path, subcommand, option):
        return True  # pretend every option this pipeline might pass is supported

    def fake_run(command, **kwargs):
        captured["command"] = command
        return subprocess.CompletedProcess(command, 0, stdout="", stderr="")

    monkeypatch.setattr(pipeline, "_subcommand_supports", fake_supports)
    monkeypatch.setattr(pipeline.subprocess, "run", fake_run)

    log_path = tmp_path / "log.txt"
    with log_path.open("w", encoding="utf-8") as log_file:
        pipeline.run_feature_extraction(
            "colmap",
            database_path=tmp_path / "db.sqlite",
            image_path=tmp_path / "source",
            use_gpu=False,
            max_dimension=1280,
            num_threads=1,
            max_features=4096,
            log_file=log_file,
        )
    args = captured["command"]
    assert "--SiftExtraction.max_image_size" in args
    assert args[args.index("--SiftExtraction.max_image_size") + 1] == "1280"
    assert "--SiftExtraction.max_num_features" in args
    assert args[args.index("--SiftExtraction.max_num_features") + 1] == "4096"
    assert "--FeatureExtraction.num_threads" in args
    assert args[args.index("--FeatureExtraction.num_threads") + 1] == "1"

    with log_path.open("w", encoding="utf-8") as log_file:
        pipeline.run_matching(
            "colmap",
            database_path=tmp_path / "db.sqlite",
            use_gpu=False,
            num_threads=1,
            log_file=log_file,
        )
    match_args = captured["command"]
    assert "--FeatureMatching.num_threads" in match_args
    assert match_args[match_args.index("--FeatureMatching.num_threads") + 1] == "1"


def test_sparse_mesher_uses_delaunay_with_sparse_input_type(monkeypatch, tmp_path):
    """A real CPU-fallback build failed with "Ply file does not contain normals" the one time
    poisson_mesher actually ran against a raw sparse point cloud - poisson requires normals that
    a sparse SfM point cloud does not have. delaunay_mesher's --input_type sparse works directly
    on the sparse reconstruction and must be the only mesher used for this path (no poisson
    fallback, since poisson cannot work here regardless of availability)."""
    captured: dict = {}

    def fake_run(command, **kwargs):
        captured["command"] = command
        return subprocess.CompletedProcess(command, 0, stdout="", stderr="")

    monkeypatch.setattr(pipeline.subprocess, "run", fake_run)

    sparse_model_path = tmp_path / "sparse" / "0"
    sparse_model_path.mkdir(parents=True)
    output_ply = tmp_path / "output" / "mesh.ply"

    log_path = tmp_path / "log.txt"
    with log_path.open("w", encoding="utf-8") as log_file:
        pipeline.run_sparse_mesher(
            "colmap",
            sparse_model_path=sparse_model_path,
            output_ply=output_ply,
            log_file=log_file,
        )

    command = captured["command"]
    assert command[0] == "colmap"
    assert command[1] == "delaunay_mesher"
    assert "--input_type" in command
    assert command[command.index("--input_type") + 1] == "sparse"
    assert "--input_path" in command
    assert command[command.index("--input_path") + 1] == str(sparse_model_path)
    assert "poisson_mesher" not in command


def test_reconstruction_source_images_are_downscaled_and_exif_stripped(tmp_path):
    from PIL import Image

    from app.services.model3d.reconstruction_service import _write_source_image

    large = Image.new("RGB", (4000, 3000), color=(10, 20, 30))
    buffer = io.BytesIO()
    large.save(buffer, format="JPEG")
    data = buffer.getvalue()

    destination = tmp_path / "working-copy.jpg"
    width, height = _write_source_image(destination, data, max_dimension=1280)

    assert max(width, height) == 1280
    assert width / height == pytest.approx(4000 / 3000, rel=0.01)

    with Image.open(destination) as written:
        assert written.size == (width, height)
        assert len(written.getexif()) == 0
