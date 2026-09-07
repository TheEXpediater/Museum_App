from __future__ import annotations

import shutil
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
    def fake_feature_extraction(bin_path, *, database_path, image_path, use_gpu, max_dimension, log_file):
        database_path.parent.mkdir(parents=True, exist_ok=True)
        database_path.touch()

    def fake_matching(bin_path, *, database_path, use_gpu, log_file):
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

    def fake_export_sparse_as_ply(bin_path, *, sparse_model_path, output_ply, log_file):
        output_ply.parent.mkdir(parents=True, exist_ok=True)
        _fake_colored_mesh(output_ply)

    def fake_mesher(bin_path, *, input_ply, output_ply, log_file):
        shutil.copy(input_ply, output_ply)

    monkeypatch.setattr(pipeline, "run_feature_extraction", fake_feature_extraction)
    monkeypatch.setattr(pipeline, "run_matching", fake_matching)
    monkeypatch.setattr(pipeline, "run_mapper", fake_mapper)
    monkeypatch.setattr(pipeline, "analyze_sparse_model", fake_analyze)
    monkeypatch.setattr(pipeline, "export_sparse_as_ply", fake_export_sparse_as_ply)
    monkeypatch.setattr(pipeline, "run_mesher", fake_mesher)


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


def test_preflight_needs_images_when_registration_is_poor(model3d_context, monkeypatch):
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


def test_full_build_publishes_glb_with_version_and_sha(model3d_context, monkeypatch):
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
    assert body["state"]["status"] == "ready"
    assert body["state"]["version"] == 1
    assert len(body["state"]["sha256"]) == 64
    assert body["state"]["size_bytes"] > 0
    assert body["state"]["model_url"] is not None
    assert body["job"]["status"] == "ready"

    published_path = settings.model_3d_root_path / artifact_id / "model-v1.glb"
    assert published_path.is_file()

    visitor_view = client.get(f"/api/v1/artifacts/{artifact_id}", headers=headers).json()
    assert visitor_view is not None  # admin view sanity check; visitor exposure covered below


def test_visitor_artifact_exposes_model_only_when_ready(model3d_context, monkeypatch):
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

    after = client.get(f"/api/v1/visitor/artifacts/{artifact_id}", headers=guest_headers)
    body = after.json()
    assert body["model_3d_available"] is True
    assert body["model_3d_version"] == 1
    assert body["model_3d_sha256"]
    assert body["model_3d_url"].endswith(".glb")


def test_failed_rebuild_preserves_previous_published_model(model3d_context, monkeypatch):
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
    first_status = client.get(f"/api/v1/artifacts/{artifact_id}/3d/status", headers=headers).json()
    assert first_status["state"]["status"] == "ready"
    original_sha = first_status["state"]["sha256"]
    original_path = settings.model_3d_root_path / artifact_id / "model-v1.glb"
    assert original_path.is_file()

    def broken_mesher(bin_path, *, input_ply, output_ply, log_file):
        raise pipeline.ColmapStageError("poisson_mesher", "simulated meshing failure")

    monkeypatch.setattr(pipeline, "run_mesher", broken_mesher)
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
