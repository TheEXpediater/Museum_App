from __future__ import annotations

from datetime import timedelta
from io import BytesIO
import json
import os
from pathlib import Path

import jwt
import mongomock
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.auth.jwt_handler import create_access_token
from app.auth.password import hash_password, verify_password
from app.config import BACKEND_DIR, ENV_FILE, Settings
from app.utils import to_object_id, utc_now
from scripts.create_admin import create_or_update_admin, validate_email, validate_password


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
        ai_enabled=False,
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


def image_bytes(
    format_name: str = "JPEG",
    size: tuple[int, int] = (32, 32),
    color: tuple[int, int, int] = (180, 40, 40),
) -> bytes:
    buffer = BytesIO()
    Image.new("RGB", size, color=color).save(buffer, format=format_name)
    return buffer.getvalue()


def image_upload_files(count: int, *, start: int = 1) -> list[tuple[str, tuple[str, bytes, str]]]:
    return [
        (
            "images",
            (
                f"photo-{index:02d}.jpg",
                image_bytes(color=((index * 37) % 256, (index * 71) % 256, (index * 109) % 256)),
                "image/jpeg",
            ),
        )
        for index in range(start, start + count)
    ]


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


def test_health_endpoint_reports_backend_status(test_context):
    client, _, _, _ = test_context
    response = client.get("/api/v1/health")
    assert response.status_code == 200
    body = response.json()
    assert body == {
        "status": "healthy",
        "database": "connected",
        "uploads_directory": "available",
    }


def test_settings_resolves_relative_upload_directory_from_backend_dir():
    settings = Settings(
        app_name="Museum Guide System Test",
        app_env="test",
        mongodb_url="mongodb://localhost:27017",
        mongodb_database="museum_guide_test",
        jwt_secret_key=JWT_SECRET,
        upload_directory="relative_uploads/images",
        _env_file=None,
    )
    assert ENV_FILE == BACKEND_DIR / ".env"
    assert settings.upload_path == (BACKEND_DIR / "relative_uploads" / "images").resolve()
    assert settings.upload_root_path == (BACKEND_DIR / "relative_uploads").resolve()


def test_settings_keeps_absolute_upload_directory(tmp_path):
    upload_directory = tmp_path / "uploads" / "images"
    settings = Settings(
        app_name="Museum Guide System Test",
        app_env="test",
        mongodb_url="mongodb://localhost:27017",
        mongodb_database="museum_guide_test",
        jwt_secret_key=JWT_SECRET,
        upload_directory=str(upload_directory),
        _env_file=None,
    )
    assert settings.upload_path == upload_directory.resolve()
    assert settings.upload_root_path == upload_directory.parent.resolve()


def test_create_admin_validates_credentials():
    assert validate_email("ADMIN@EXAMPLE.COM") == ADMIN_EMAIL
    assert validate_password(ADMIN_PASSWORD) == ADMIN_PASSWORD
    with pytest.raises(ValueError):
        validate_email("not-an-email")
    with pytest.raises(ValueError):
        validate_password("short")


def test_create_or_update_admin_creates_account():
    database = mongomock.MongoClient()["museum_guide_test"]
    result = create_or_update_admin(
        database,
        email=ADMIN_EMAIL,
        full_name="Museum Administrator",
        password=ADMIN_PASSWORD,
        update_existing=False,
    )
    user = database.users.find_one({"email": ADMIN_EMAIL})
    assert result == "created"
    assert user["role"] == "admin"
    assert user["is_active"] is True
    assert verify_password(ADMIN_PASSWORD, user["password_hash"])


def test_create_or_update_admin_existing_is_nondestructive_without_flag():
    database = mongomock.MongoClient()["museum_guide_test"]
    old_hash = hash_password("OriginalPassword123!")
    database.users.insert_one(
        {
            "email": ADMIN_EMAIL,
            "full_name": "Original Admin",
            "password_hash": old_hash,
            "role": "visitor",
            "is_active": False,
            "created_at": utc_now(),
            "updated_at": utc_now(),
        }
    )
    result = create_or_update_admin(
        database,
        email=ADMIN_EMAIL,
        full_name="Museum Administrator",
        password=ADMIN_PASSWORD,
        update_existing=False,
    )
    user = database.users.find_one({"email": ADMIN_EMAIL})
    assert result == "exists"
    assert user["full_name"] == "Original Admin"
    assert user["role"] == "visitor"
    assert user["is_active"] is False
    assert user["password_hash"] == old_hash


def test_create_or_update_admin_updates_existing_with_explicit_flag():
    database = mongomock.MongoClient()["museum_guide_test"]
    database.users.insert_one(
        {
            "email": ADMIN_EMAIL,
            "full_name": "Original Admin",
            "password_hash": hash_password("OriginalPassword123!"),
            "role": "visitor",
            "is_active": False,
            "created_at": utc_now(),
            "updated_at": utc_now(),
        }
    )
    result = create_or_update_admin(
        database,
        email=ADMIN_EMAIL,
        full_name="Museum Administrator",
        password=ADMIN_PASSWORD,
        update_existing=True,
    )
    user = database.users.find_one({"email": ADMIN_EMAIL})
    assert result == "updated"
    assert user["full_name"] == "Museum Administrator"
    assert user["role"] == "admin"
    assert user["is_active"] is True
    assert verify_password(ADMIN_PASSWORD, user["password_hash"])


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
    assert response.json()["detail"] == "Invalid email or password."
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


@pytest.mark.parametrize("image_count", [1, 5, 6, 20, 42])
def test_artifact_creation_accepts_many_valid_images_without_discarding(test_context, image_count):
    client, _, _, _ = test_context
    response = client.post(
        "/api/v1/artifacts",
        data={
            "artifact_code": f"ART-MANY-{image_count}",
            "name": f"Artifact With {image_count} Images",
            "category": "Tests",
            "primary_image_index": "0",
        },
        files=image_upload_files(image_count),
        headers=auth_headers(client),
    )

    assert response.status_code == 201, response.text
    body = response.json()
    assert len(body["image_paths"]) == image_count
    assert len(set(body["image_paths"])) == image_count
    assert body["primary_image_path"] == body["image_paths"][0]
    assert body["image_paths"].count(body["primary_image_path"]) == 1


def test_artifact_image_urls_use_request_host_for_lan_clients(test_context):
    client, _, _, _ = test_context
    response = client.post(
        "/api/v1/artifacts",
        data={
            "artifact_code": "ART-0020",
            "name": "LAN Image",
            "description": "Image URL host test",
            "category": "Tests",
        },
        files=[("images", ("lan.jpg", image_bytes(), "image/jpeg"))],
        headers={**auth_headers(client), "host": "192.168.100.12:8000"},
    )

    assert response.status_code == 201, response.text
    body = response.json()
    assert body["image_urls"][0].startswith("http://192.168.100.12:8000/uploads/images/")
    assert body["primary_image_url"].startswith("http://192.168.100.12:8000/uploads/images/")


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


def test_add_images_preserves_existing_many_image_artifact_without_discarding(test_context):
    client, _, _, _ = test_context
    headers = auth_headers(client)
    created = client.post(
        "/api/v1/artifacts",
        data={
            "artifact_code": "ART-MANY-ADD",
            "name": "Many Image Artifact",
            "category": "Tests",
            "primary_image_index": "0",
        },
        files=image_upload_files(20),
        headers=headers,
    )
    assert created.status_code == 201, created.text
    artifact = created.json()
    original_paths = artifact["image_paths"]
    original_primary = artifact["primary_image_path"]

    response = client.post(
        f"/api/v1/artifacts/{artifact['id']}/images",
        files=image_upload_files(22, start=101),
        headers=headers,
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert len(body["image_paths"]) == 42
    assert body["image_paths"][:20] == original_paths
    assert len(set(body["image_paths"])) == 42
    assert body["primary_image_path"] == original_primary
    assert body["image_paths"].count(body["primary_image_path"]) == 1


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


def test_draft_creation_allows_incomplete_metadata_and_custom_fields(test_context):
    client, _, _, _ = test_context
    custom_fields = [
        {"id": "weight", "label": "Weight", "value": "3.5", "unit": "kg", "type": "number"},
        {"id": "local-name", "label": "Local Name", "value": "Araro", "unit": None, "type": "text"},
    ]
    response = client.post(
        "/api/v1/artifacts",
        data={
            "artifact_code": "DRAFT-1",
            "name": "Imported Draft",
            "status": "draft",
            "custom_fields": json.dumps(custom_fields),
        },
        headers=auth_headers(client),
    )

    assert response.status_code == 201, response.text
    body = response.json()
    assert body["status"] == "draft"
    assert body["description"] == ""
    assert body["category"] == "Uncategorized"
    assert body["custom_fields"][0]["label"] == "Weight"


def test_multiline_metadata_and_legitimate_whitespace_are_allowed(test_context):
    client, _, _, _ = test_context
    description = "Paragraph one.\n\nParagraph two with more than one hundred characters so long museum descriptions keep working without falling back to old short limits."
    material = "Woven bamboo.\n\nAdditional fibers are visible."
    condition = "Surface wear is visible.\r\nMinor discoloration is present."
    custom_note = "First paragraph.\n\nSecond paragraph with\ttabbed context."
    metadata_sections = [
        {
            "id": "historical_details",
            "title": "Historical Details",
            "fields": [
                {
                    "id": "use-notes",
                    "label": "Use Notes",
                    "value": custom_note,
                    "type": "long_text",
                    "order": 0,
                }
            ],
        }
    ]

    response = client.post(
        "/api/v1/artifacts",
        data={
            "artifact_code": "MULTILINE-1",
            "name": "Paragraph Artifact",
            "status": "draft",
            "description": description,
            "material": material,
            "condition": condition,
            "custom_fields": json.dumps(
                [
                    {
                        "id": "notes",
                        "label": "Curatorial Notes",
                        "value": custom_note,
                        "type": "long_text",
                    }
                ]
            ),
            "metadata_sections": json.dumps(metadata_sections),
        },
        headers=auth_headers(client),
    )

    assert response.status_code == 201, response.text
    body = response.json()
    assert body["description"] == description
    assert body["material"] == material
    assert body["condition"] == condition
    assert body["custom_fields"][0]["value"] == custom_note
    assert body["metadata_sections"][0]["fields"][0]["value"] == custom_note


def test_unsafe_control_characters_are_rejected(test_context):
    client, _, _, _ = test_context
    response = client.post(
        "/api/v1/artifacts",
        data={
            "artifact_code": "CONTROL-1",
            "name": "Bad Control Character",
            "description": "Invalid\x00description",
        },
        headers=auth_headers(client),
    )

    assert response.status_code == 422
    assert "unsupported control characters" in response.json()["detail"]


def test_create_and_publish_do_not_auto_index(test_context, monkeypatch):
    client, _, _, _ = test_context

    def fail_if_called(_settings):
        raise AssertionError("Artifact persistence should not automatically call AI indexing.")

    monkeypatch.setattr("app.routes.artifacts.ArtifactIndexingService.from_settings", fail_if_called)
    headers = auth_headers(client)

    created = client.post(
        "/api/v1/artifacts",
        data={
            "artifact_code": "NO-AUTO-AI",
            "name": "Manual AI Artifact",
            "category": "Farm Tools",
            "status": "draft",
        },
        files=[("images", ("only.jpg", image_bytes(), "image/jpeg"))],
        headers=headers,
    )
    assert created.status_code == 201, created.text
    assert created.json()["ai_index_status"] == "not_indexed"

    created_published = client.post(
        "/api/v1/artifacts",
        data={
            "artifact_code": "NO-AUTO-AI-PUBLISHED",
            "name": "Manual AI Published Artifact",
            "category": "Farm Tools",
            "status": "published",
        },
        files=[("images", ("published.jpg", image_bytes(), "image/jpeg"))],
        headers=headers,
    )
    assert created_published.status_code == 201, created_published.text
    assert created_published.json()["status"] == "published"
    assert created_published.json()["ai_index_status"] == "not_indexed"

    published = client.patch(
        f"/api/v1/artifacts/{created.json()['id']}",
        data={"status": "published"},
        headers=headers,
    )
    assert published.status_code == 200, published.text
    assert published.json()["status"] == "published"
    assert published.json()["ai_index_status"] == "not_indexed"


def test_duplicate_custom_field_labels_are_rejected(test_context):
    client, _, _, _ = test_context
    response = client.post(
        "/api/v1/artifacts",
        data={
            "artifact_code": "DRAFT-2",
            "name": "Duplicate Field Draft",
            "custom_fields": json.dumps(
                [
                    {"id": "a", "label": "Weight", "value": "1", "type": "number"},
                    {"id": "b", "label": "weight", "value": "2", "type": "number"},
                ]
            ),
        },
        headers=auth_headers(client),
    )

    assert response.status_code == 422
    assert "duplicate labels" in response.json()["detail"]


def test_multiple_uploaded_images_require_explicit_primary_and_respect_index(test_context):
    client, _, _, _ = test_context
    headers = auth_headers(client)
    data = {
        "artifact_code": "ART-MULTI",
        "name": "Multiple Images",
        "description": "Has multiple images",
        "category": "Tests",
    }
    files = [
        ("images", ("first.jpg", image_bytes(size=(32, 32)), "image/jpeg")),
        ("images", ("second.jpg", image_bytes(size=(40, 40)), "image/jpeg")),
    ]
    missing_primary = client.post("/api/v1/artifacts", data=data, files=files, headers=headers)
    assert missing_primary.status_code == 422
    assert missing_primary.json()["detail"] == "Select a main image before saving."

    files = [
        ("images", ("first.jpg", image_bytes(size=(32, 32)), "image/jpeg")),
        ("images", ("second.jpg", image_bytes(size=(40, 40)), "image/jpeg")),
    ]
    response = client.post(
        "/api/v1/artifacts",
        data={**data, "artifact_code": "ART-MULTI-2", "primary_image_index": "1"},
        files=files,
        headers=headers,
    )
    assert response.status_code == 201, response.text
    body = response.json()
    assert body["primary_image_path"] == body["image_paths"][1]


def test_publish_validation_requires_category_and_primary_image(test_context):
    client, _, _, _ = test_context
    headers = auth_headers(client)
    artifact = client.post(
        "/api/v1/artifacts",
        data={"artifact_code": "DRAFT-PUBLISH", "name": "Publish Candidate", "status": "draft"},
        files=[("images", ("only.jpg", image_bytes(), "image/jpeg"))],
        headers=headers,
    ).json()

    invalid = client.patch(f"/api/v1/artifacts/{artifact['id']}", data={"status": "published"}, headers=headers)
    assert invalid.status_code == 422
    assert "Category" in invalid.json()["detail"]

    valid = client.patch(
        f"/api/v1/artifacts/{artifact['id']}",
        data={"category": "Farm Tools", "status": "published"},
        headers=headers,
    )
    assert valid.status_code == 200, valid.text
    assert valid.json()["status"] == "published"


def test_removing_primary_image_requires_replacement_when_other_images_remain(test_context):
    client, _, _, _ = test_context
    headers = auth_headers(client)
    response = client.post(
        "/api/v1/artifacts",
        data={
            "artifact_code": "ART-PRIMARY-REMOVE",
            "name": "Primary Remove",
            "category": "Tests",
            "primary_image_index": "0",
        },
        files=[
            ("images", ("first.jpg", image_bytes(size=(32, 32)), "image/jpeg")),
            ("images", ("second.jpg", image_bytes(size=(40, 40)), "image/jpeg")),
        ],
        headers=headers,
    )
    assert response.status_code == 201, response.text
    artifact = response.json()
    first_name = Path(artifact["image_paths"][0]).name
    second_path = artifact["image_paths"][1]

    blocked = client.delete(f"/api/v1/artifacts/{artifact['id']}/images/{first_name}", headers=headers)
    assert blocked.status_code == 422
    assert "Choose a new main image" in blocked.json()["detail"]

    removed = client.delete(
        f"/api/v1/artifacts/{artifact['id']}/images/{first_name}",
        params={"replacement_primary_image_path": second_path},
        headers=headers,
    )
    assert removed.status_code == 200, removed.text
    assert removed.json()["primary_image_path"] == second_path


def test_category_management_endpoints(test_context):
    client, database, _, _ = test_context
    headers = auth_headers(client)
    created = client.post("/api/v1/artifact-categories", json={"name": "Agricultural Tools"}, headers=headers)
    assert created.status_code == 201, created.text
    category = created.json()
    assert category["normalized_name"] == "agricultural tools"

    artifact = create_artifact(client, headers, code="ART-CATEGORY")
    update = client.patch(
        f"/api/v1/artifacts/{artifact['id']}",
        data={"category": "Agricultural Tools"},
        headers=headers,
    )
    assert update.status_code == 200

    categories = client.get("/api/v1/artifact-categories", headers=headers)
    assert categories.status_code == 200
    assert categories.json()[0]["artifact_count"] == 1

    renamed = client.patch(
        f"/api/v1/artifact-categories/{category['id']}",
        json={"name": "Farm Implements"},
        headers=headers,
    )
    assert renamed.status_code == 200, renamed.text
    assert database.artifacts.find_one({"_id": to_object_id(artifact["id"])})["category"] == "Farm Implements"

    deactivated = client.delete(f"/api/v1/artifact-categories/{category['id']}", headers=headers)
    assert deactivated.status_code == 200
    assert deactivated.json()["is_active"] is False
    assert database.artifacts.count_documents({}) == 1

    active_only = client.get("/api/v1/artifact-categories", headers=headers)
    assert active_only.status_code == 200
    assert "Farm Implements" not in {item["name"] for item in active_only.json()}

    include_inactive = client.get("/api/v1/artifact-categories", params={"include_inactive": "true"}, headers=headers)
    assert include_inactive.status_code == 200
    inactive = next(item for item in include_inactive.json() if item["id"] == category["id"])
    assert inactive["is_active"] is False
    assert inactive["artifact_count"] == 1

    reactivated = client.patch(
        f"/api/v1/artifact-categories/{category['id']}",
        json={"is_active": True},
        headers=headers,
    )
    assert reactivated.status_code == 200
    assert reactivated.json()["is_active"] is True
