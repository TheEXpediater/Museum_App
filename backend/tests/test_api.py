from __future__ import annotations

from datetime import timedelta
from io import BytesIO
import os
from pathlib import Path

import jwt
import mongomock
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.auth.jwt_handler import create_access_token
from app.auth.password import hash_password
from app.config import Settings
from app.utils import utc_now


ADMIN_EMAIL = "admin@example.com"
ADMIN_PASSWORD = "ChangeThisPassword123!"
JWT_SECRET = "test-secret-key-that-is-long-enough"

os.environ.setdefault("MONGODB_URL", "mongodb://localhost:27017")
os.environ.setdefault("MONGODB_DATABASE", "museum_guide_test")
os.environ.setdefault("JWT_SECRET_KEY", JWT_SECRET)

from main import create_app


@pytest.fixture()
def test_context(tmp_path):
    settings = Settings(
        app_name="Museum Guide System Test",
        app_env="test",
        mongodb_url="mongodb://localhost:27017",
        mongodb_database="museum_guide_test",
        jwt_secret_key=JWT_SECRET,
        upload_directory=str(tmp_path / "uploads" / "images"),
        max_image_size_mb=1,
        cors_origins="http://testserver",
        _env_file=None,
    )
    database = mongomock.MongoClient()[settings.mongodb_database]
    app = create_app(settings=settings, database=database)
    with TestClient(app) as client:
        admin_id = database.users.insert_one(
            {
                "email": ADMIN_EMAIL,
                "full_name": "Museum Administrator",
                "password_hash": hash_password(ADMIN_PASSWORD),
                "role": "admin",
                "is_active": True,
                "created_at": utc_now(),
                "updated_at": utc_now(),
            }
        ).inserted_id
        yield client, database, settings, str(admin_id)


def image_bytes(format_name: str = "JPEG", size: tuple[int, int] = (32, 32)) -> bytes:
    buffer = BytesIO()
    Image.new("RGB", size, color=(180, 40, 40)).save(buffer, format=format_name)
    return buffer.getvalue()


def login(client: TestClient) -> dict:
    response = client.post(
        "/api/v1/auth/login",
        json={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD},
    )
    assert response.status_code == 200
    return response.json()


def auth_headers(client: TestClient) -> dict[str, str]:
    token = login(client)["access_token"]
    return {"Authorization": f"Bearer {token}"}


def create_artifact(client: TestClient, headers: dict[str, str], *, code: str = "ART-0001", with_image: bool = False) -> dict:
    data = {
        "artifact_code": code,
        "name": "Wooden Plow",
        "description": "A traditional farming tool used by local farmers.",
        "category": "Farm Tools",
        "origin": "Pampanga",
        "historical_period": "Early 20th Century",
        "material": "Wood and metal",
        "dimensions": "120 cm x 35 cm",
        "condition": "Good",
    }
    files = []
    if with_image:
        files = [("images", ("plow.jpg", image_bytes(), "image/jpeg"))]
    response = client.post("/api/v1/artifacts", data=data, files=files, headers=headers)
    assert response.status_code == 201, response.text
    return response.json()


def test_successful_admin_login(test_context):
    client, _, _, _ = test_context
    response = client.post("/api/v1/auth/login", json={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD})
    assert response.status_code == 200
    body = response.json()
    assert body["token_type"] == "bearer"
    assert body["user"]["role"] == "admin"
    assert body["expires_in"] == 28800


def test_invalid_admin_credentials_are_rejected(test_context):
    client, _, _, _ = test_context
    response = client.post("/api/v1/auth/login", json={"email": ADMIN_EMAIL, "password": "wrong-password"})
    assert response.status_code == 401
    assert "password_hash" not in response.text


def test_missing_jwt_token_is_rejected(test_context):
    client, _, _, _ = test_context
    response = client.get("/api/v1/artifacts")
    assert response.status_code == 401


def test_invalid_and_expired_jwt_tokens_are_rejected(test_context):
    client, _, settings, admin_id = test_context
    invalid = client.get("/api/v1/artifacts", headers={"Authorization": "Bearer not-a-token"})
    assert invalid.status_code == 401

    expired_token = jwt.encode(
        {
            "sub": admin_id,
            "email": ADMIN_EMAIL,
            "role": "admin",
            "type": "access",
            "exp": utc_now() - timedelta(minutes=1),
        },
        settings.jwt_secret_key,
        algorithm=settings.jwt_algorithm,
    )
    expired = client.get("/api/v1/artifacts", headers={"Authorization": f"Bearer {expired_token}"})
    assert expired.status_code == 401


def test_non_admin_role_is_rejected(test_context):
    client, database, settings, _ = test_context
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
    response = client.get("/api/v1/artifacts", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 403


def test_artifact_creation(test_context):
    client, _, _, _ = test_context
    artifact = create_artifact(client, auth_headers(client))
    assert artifact["artifact_code"] == "ART-0001"
    assert artifact["image_paths"] == []


def test_artifact_creation_with_valid_image(test_context):
    client, _, settings, _ = test_context
    artifact = create_artifact(client, auth_headers(client), code="ART-0002", with_image=True)
    assert len(artifact["image_paths"]) == 1
    assert artifact["primary_image_path"] == artifact["image_paths"][0]
    stored = settings.upload_path / Path(artifact["image_paths"][0]).name
    assert stored.exists()
    assert artifact["image_urls"][0].startswith("http://testserver/uploads/images/")


def test_unsupported_image_types_are_rejected(test_context):
    client, _, _, _ = test_context
    headers = auth_headers(client)
    response = client.post(
        "/api/v1/artifacts",
        data={
            "artifact_code": "ART-0003",
            "name": "Invalid File",
            "description": "Invalid upload",
            "category": "Tests",
        },
        files=[("images", ("note.txt", b"not an image", "text/plain"))],
        headers=headers,
    )
    assert response.status_code == 415


def test_oversized_files_are_rejected(test_context):
    client, _, _, _ = test_context
    response = client.post(
        "/api/v1/artifacts",
        data={
            "artifact_code": "ART-0004",
            "name": "Large File",
            "description": "Too large",
            "category": "Tests",
        },
        files=[("images", ("large.png", b"x" * (1024 * 1024 + 1), "image/png"))],
        headers=auth_headers(client),
    )
    assert response.status_code == 413


def test_artifact_list_retrieval_search_filter_and_sort(test_context):
    client, _, _, _ = test_context
    headers = auth_headers(client)
    create_artifact(client, headers, code="ART-0005")
    create_artifact(client, headers, code="ART-0006")
    response = client.get(
        "/api/v1/artifacts",
        params={"search": "plow", "category": "Farm Tools", "sort": "name_asc"},
        headers=headers,
    )
    assert response.status_code == 200
    body = response.json()
    assert body["total_items"] == 2
    assert body["items"][0]["name"] == "Wooden Plow"


def test_artifact_update(test_context):
    client, _, _, _ = test_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers, code="ART-0007")
    response = client.patch(f"/api/v1/artifacts/{artifact['id']}", data={"name": "Updated Plow"}, headers=headers)
    assert response.status_code == 200
    assert response.json()["name"] == "Updated Plow"


def test_metadata_only_update_preserves_images(test_context):
    client, _, _, _ = test_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers, code="ART-0008", with_image=True)
    original_paths = artifact["image_paths"]
    response = client.patch(f"/api/v1/artifacts/{artifact['id']}", data={"condition": "Excellent"}, headers=headers)
    assert response.status_code == 200
    assert response.json()["image_paths"] == original_paths


def test_adding_another_image(test_context):
    client, _, _, _ = test_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers, code="ART-0009", with_image=True)
    response = client.post(
        f"/api/v1/artifacts/{artifact['id']}/images",
        files=[("images", ("second.png", image_bytes("PNG"), "image/png"))],
        headers=headers,
    )
    assert response.status_code == 200
    assert len(response.json()["image_paths"]) == 2


def test_removing_an_image(test_context):
    client, _, settings, _ = test_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers, code="ART-0010", with_image=True)
    image_name = Path(artifact["image_paths"][0]).name
    response = client.delete(f"/api/v1/artifacts/{artifact['id']}/images/{image_name}", headers=headers)
    assert response.status_code == 200
    assert response.json()["image_paths"] == []
    assert not (settings.upload_path / image_name).exists()


def test_setting_primary_image(test_context):
    client, _, _, _ = test_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers, code="ART-0011", with_image=True)
    updated = client.post(
        f"/api/v1/artifacts/{artifact['id']}/images",
        files=[("images", ("second.webp", image_bytes("WEBP"), "image/webp"))],
        headers=headers,
    ).json()
    second_path = updated["image_paths"][1]
    response = client.patch(
        f"/api/v1/artifacts/{artifact['id']}/primary-image",
        json={"image_path": second_path},
        headers=headers,
    )
    assert response.status_code == 200
    assert response.json()["primary_image_path"] == second_path


def test_deleting_artifact_cleans_up_files(test_context):
    client, _, settings, _ = test_context
    headers = auth_headers(client)
    artifact = create_artifact(client, headers, code="ART-0012", with_image=True)
    image_name = Path(artifact["image_paths"][0]).name
    response = client.delete(f"/api/v1/artifacts/{artifact['id']}", headers=headers)
    assert response.status_code == 200
    assert response.json()["message"] == "Artifact deleted successfully."
    assert not (settings.upload_path / image_name).exists()
    get_response = client.get(f"/api/v1/artifacts/{artifact['id']}", headers=headers)
    assert get_response.status_code == 404


def test_duplicate_artifact_code_is_rejected(test_context):
    client, _, _, _ = test_context
    headers = auth_headers(client)
    create_artifact(client, headers, code="ART-0013")
    response = client.post(
        "/api/v1/artifacts",
        data={
            "artifact_code": "ART-0013",
            "name": "Duplicate",
            "description": "Duplicate code",
            "category": "Tests",
        },
        headers=headers,
    )
    assert response.status_code == 409


def test_invalid_mongodb_id_handling(test_context):
    client, _, _, _ = test_context
    headers = auth_headers(client)
    assert client.get("/api/v1/artifacts/not-a-valid-id", headers=headers).status_code == 404
    assert client.patch("/api/v1/artifacts/not-a-valid-id", data={"name": "Nope"}, headers=headers).status_code == 404
    assert client.delete("/api/v1/artifacts/not-a-valid-id", headers=headers).status_code == 404
