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
from app.auth.password import hash_password, verify_password
from app.config import Settings
from app.repositories import artifact_repository
from app.schemas.ai import RecognitionResponse
from app.utils import utc_now
from main import create_app


ADMIN_EMAIL = "admin@example.com"
ADMIN_PASSWORD = "ChangeThisPassword123!"
JWT_SECRET = "test-secret-key-that-is-long-enough"

os.environ.setdefault("MONGODB_URL", "mongodb://localhost:27017")
os.environ.setdefault("MONGODB_DATABASE", "museum_guide_test")
os.environ.setdefault("JWT_SECRET_KEY", JWT_SECRET)


@pytest.fixture()
def test_context(tmp_path):
    settings = Settings(
        app_name="Museum Guide Visitor Test",
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


def image_bytes(format_name: str = "JPEG", size: tuple[int, int] = (32, 32)) -> bytes:
    buffer = BytesIO()
    Image.new("RGB", size, color=(80, 140, 80)).save(buffer, format=format_name)
    return buffer.getvalue()


def admin_headers(client: TestClient) -> dict[str, str]:
    response = client.post("/api/v1/auth/login", json={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD})
    assert response.status_code == 200
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def guest_payload(**overrides) -> dict:
    payload = {
        "first_name": "Maria",
        "last_name": "Santos",
        "relationship_type": "General Visitor",
        "device_session_id": "device-123",
    }
    payload.update(overrides)
    return payload


def create_guest(client: TestClient, **overrides) -> tuple[dict, dict[str, str]]:
    response = client.post("/api/v1/visitor/guest-session", json=guest_payload(**overrides))
    assert response.status_code == 200, response.text
    body = response.json()
    return body, {"Authorization": f"Bearer {body['access_token']}"}


def student_payload(**overrides) -> dict:
    payload = {
        "student_id": "psau-2026-001",
        "first_name": "Juan",
        "middle_initial": "D",
        "last_name": "Reyes",
        "year_level": "Third Year",
        "course": "Bachelor of Science in Agriculture",
        "email": "Juan.Reyes@example.com",
        "password": "Student123",
        "confirm_password": "Student123",
    }
    payload.update(overrides)
    return payload


def create_student(client: TestClient, **overrides) -> tuple[dict, dict[str, str]]:
    response = client.post("/api/v1/student/register", json=student_payload(**overrides))
    assert response.status_code == 201, response.text
    body = response.json()
    return body, {"Authorization": f"Bearer {body['access_token']}"}


def insert_artifact(database, *, code: str = "ART-V1", status: str | None = None, custom_fields: list[dict] | None = None) -> dict:
    data = {
        "artifact_code": code,
        "name": "Wooden Plow",
        "description": "A traditional farming tool.",
        "category": "Farm Tools",
        "origin": "Pampanga",
        "historical_period": "Early 20th Century",
        "material": "Wood",
        "dimensions": "120 cm x 35 cm",
        "condition": "Good",
        "custom_fields": custom_fields or [],
        "image_paths": [],
        "primary_image_path": None,
        "created_by": "admin",
    }
    if status is not None:
        data["status"] = status
    return artifact_repository.create_artifact(database, data)


def test_guest_session_creation_and_validation(test_context):
    client, database, _, _ = test_context

    body, _ = create_guest(client, first_name=" Maria   Clara ", last_name=" Santos ")

    assert body["account_type"] == "guest"
    assert body["profile"]["display_name"] == "Maria Clara Santos"
    assert body["profile"]["relationship_type"] == "General Visitor"
    assert body["profile"]["role"] == "guest"
    assert database.guest_sessions.count_documents({}) == 1
    assert body["access_token"]

    missing_name = client.post("/api/v1/visitor/guest-session", json=guest_payload(first_name=" "))
    assert missing_name.status_code == 422

    invalid_relationship = client.post(
        "/api/v1/visitor/guest-session",
        json=guest_payload(relationship_type="Current Student"),
    )
    assert invalid_relationship.status_code == 422

    other_missing_detail = client.post(
        "/api/v1/visitor/guest-session",
        json=guest_payload(relationship_type="Other", relationship_detail=""),
    )
    assert other_missing_detail.status_code == 422

    other = client.post(
        "/api/v1/visitor/guest-session",
        json=guest_payload(relationship_type="Other", relationship_detail="Community partner"),
    )
    assert other.status_code == 200
    assert other.json()["profile"]["relationship_detail"] == "Community partner"


def test_student_registration_login_and_password_hashing(test_context):
    client, database, _, _ = test_context
    body, headers = create_student(client)

    assert body["account_type"] == "student"
    assert body["profile"]["student_id"] == "PSAU-2026-001"
    assert body["profile"]["email"] == "juan.reyes@example.com"
    assert body["profile"]["display_name"] == "Juan D. Reyes"
    assert body["profile"]["role"] == "student"

    stored = database.students.find_one({"student_id_normalized": "PSAU-2026-001"})
    assert stored is not None
    assert stored["password_hash"] != "Student123"
    assert verify_password("Student123", stored["password_hash"])
    assert "password_hash" not in str(body)

    by_student_id = client.post(
        "/api/v1/student/login",
        json={"identifier": "psau-2026-001", "password": "Student123"},
    )
    assert by_student_id.status_code == 200
    assert by_student_id.json()["profile"]["student_id"] == "PSAU-2026-001"

    by_email = client.post(
        "/api/v1/student/login",
        json={"identifier": "JUAN.REYES@EXAMPLE.COM", "password": "Student123"},
    )
    assert by_email.status_code == 200

    me = client.get("/api/v1/visitor/me", headers=headers)
    assert me.status_code == 200
    assert me.json()["account_type"] == "student"


def test_student_duplicate_and_password_validation(test_context):
    client, _, _, _ = test_context
    create_student(client)

    duplicate_id = client.post(
        "/api/v1/student/register",
        json=student_payload(email="other@example.com"),
    )
    assert duplicate_id.status_code == 409
    assert "Student ID" in duplicate_id.json()["detail"]

    duplicate_email = client.post(
        "/api/v1/student/register",
        json=student_payload(student_id="PSAU-2026-002", email="juan.reyes@example.com"),
    )
    assert duplicate_email.status_code == 409
    assert "email" in duplicate_email.json()["detail"].lower()

    weak_password = client.post(
        "/api/v1/student/register",
        json=student_payload(student_id="PSAU-2026-003", email="weak@example.com", password="studentabc", confirm_password="studentabc"),
    )
    assert weak_password.status_code == 422

    mismatch = client.post(
        "/api/v1/student/register",
        json=student_payload(student_id="PSAU-2026-004", email="mismatch@example.com", confirm_password="Student124"),
    )
    assert mismatch.status_code == 422


def test_invalid_student_login_is_safe(test_context):
    client, _, _, _ = test_context
    create_student(client)
    response = client.post("/api/v1/student/login", json={"identifier": "PSAU-2026-001", "password": "wrong"})
    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid student ID, email, or password."
    assert "password_hash" not in response.text


def test_role_boundaries_for_admin_visitor_and_recognition(test_context, monkeypatch):
    client, database, _, _ = test_context
    _, guest_headers = create_guest(client)
    _, student_headers = create_student(client)

    assert client.get("/api/v1/admin/dashboard", headers=guest_headers).status_code == 403
    assert client.get("/api/v1/admin/dashboard", headers=student_headers).status_code == 403
    assert client.get("/api/v1/admin/dashboard", headers=admin_headers(client)).status_code == 200

    insert_artifact(database)
    assert client.get("/api/v1/visitor/artifacts", headers=guest_headers).status_code == 200
    assert client.get("/api/v1/visitor/artifacts", headers=student_headers).status_code == 200
    assert client.get("/api/v1/visitor/artifacts").status_code == 401

    class FakeRecognitionRouteService:
        def recognize(self, *_args, **_kwargs):
            return RecognitionResponse(
                matched=False,
                match_level="no_match",
                best_match=None,
                other_matches=[],
                message="No reliable artifact match was found.",
            )

    monkeypatch.setattr("app.routes.ai.ArtifactRecognitionService.from_settings", lambda _settings: FakeRecognitionRouteService())
    anonymous = client.post("/api/v1/ai/recognize", files={"image": ("query.jpg", image_bytes(), "image/jpeg")})
    assert anonymous.status_code == 401
    visitor = client.post(
        "/api/v1/ai/recognize",
        files={"image": ("query.jpg", image_bytes(), "image/jpeg")},
        headers=guest_headers,
    )
    assert visitor.status_code == 200
    admin = client.post(
        "/api/v1/ai/recognize",
        files={"image": ("query.jpg", image_bytes(), "image/jpeg")},
        headers=admin_headers(client),
    )
    assert admin.status_code == 200


def test_expired_tokens_and_role_tampering_are_rejected(test_context):
    client, database, settings, _ = test_context
    guest = database.guest_sessions.insert_one(
        {
            "first_name": "Expired",
            "last_name": "Guest",
            "display_name": "Expired Guest",
            "relationship_type": "General Visitor",
            "relationship_detail": None,
            "batch_or_graduation_year": None,
            "office_or_department": None,
            "role": "guest",
            "created_at": utc_now() - timedelta(days=2),
            "expires_at": utc_now() - timedelta(hours=1),
            "last_seen_at": utc_now() - timedelta(days=2),
            "device_session_id": "expired-guest",
        }
    ).inserted_id
    expired_guest_token = jwt.encode(
        {
            "sub": str(guest),
            "email": "",
            "role": "guest",
            "type": "access",
            "exp": utc_now() - timedelta(minutes=1),
        },
        settings.jwt_secret_key,
        algorithm=settings.jwt_algorithm,
    )
    assert client.get("/api/v1/visitor/me", headers={"Authorization": f"Bearer {expired_guest_token}"}).status_code == 401

    student, _ = create_student(client)
    expired_student_token = jwt.encode(
        {
            "sub": student["profile"]["id"],
            "email": student["profile"]["email"],
            "role": "student",
            "type": "access",
            "exp": utc_now() - timedelta(minutes=1),
        },
        settings.jwt_secret_key,
        algorithm=settings.jwt_algorithm,
    )
    assert client.get("/api/v1/visitor/me", headers={"Authorization": f"Bearer {expired_student_token}"}).status_code == 401

    tampered_role_token, _ = create_access_token(student["profile"]["id"], student["profile"]["email"], "admin", settings)
    assert client.get("/api/v1/admin/dashboard", headers={"Authorization": f"Bearer {tampered_role_token}"}).status_code == 401


def test_public_content_filters_and_museum_information(test_context):
    client, database, _, _ = test_context
    now = utc_now()
    published_news_id = database.news.insert_one(
        {
            "title": "Published News",
            "summary": "Visible",
            "body": "Visible body",
            "cover_image_url": None,
            "published_at": now,
            "is_published": True,
            "created_at": now,
            "updated_at": now,
        }
    ).inserted_id
    database.news.insert_one(
        {
            "title": "Draft News",
            "summary": "Hidden",
            "body": "Hidden",
            "published_at": now,
            "is_published": False,
            "created_at": now,
            "updated_at": now,
        }
    )
    database.announcements.insert_many(
        [
            {
                "title": "Active Announcement",
                "message": "Visible",
                "priority": "high",
                "starts_at": now - timedelta(days=1),
                "expires_at": now + timedelta(days=1),
                "is_active": True,
                "created_at": now,
                "updated_at": now,
            },
            {
                "title": "Expired Announcement",
                "message": "Hidden",
                "priority": "normal",
                "starts_at": now - timedelta(days=2),
                "expires_at": now - timedelta(days=1),
                "is_active": True,
                "created_at": now,
                "updated_at": now,
            },
        ]
    )
    article_id = database.museum_articles.insert_one(
        {
            "title": "Published Article",
            "summary": "Visible",
            "body": "Visible body",
            "cover_image_url": None,
            "category": "Heritage",
            "published_at": now,
            "is_published": True,
            "created_at": now,
            "updated_at": now,
        }
    ).inserted_id
    database.museum_articles.insert_one(
        {
            "title": "Draft Article",
            "summary": "Hidden",
            "body": "Hidden",
            "category": "Heritage",
            "published_at": now,
            "is_published": False,
            "created_at": now,
            "updated_at": now,
        }
    )
    database.museum_information.insert_one(
        {
            "museum_name": "Configured Museum",
            "description": "Configured description",
            "campus_location": "Configured location",
            "opening_hours": "Configured hours",
            "contact_email": "museum@example.com",
            "contact_phone": "Configured phone",
            "visitor_guidelines": "Configured guidelines",
            "accessibility_information": "Configured accessibility",
            "latitude": 15.0,
            "longitude": 120.0,
            "updated_at": now,
        }
    )
    active_program = database.programs.insert_one(
        {
            "name": "Bachelor of Science in Agriculture",
            "name_normalized": "bachelor of science in agriculture",
            "active": True,
            "created_at": now,
            "updated_at": now,
        }
    ).inserted_id
    database.programs.insert_one(
        {
            "name": "Inactive Program",
            "name_normalized": "inactive program",
            "active": False,
            "created_at": now,
            "updated_at": now,
        }
    )

    home = client.get("/api/v1/public/home")
    assert home.status_code == 200
    assert home.json()["latest_news"][0]["title"] == "Published News"
    assert home.json()["announcements"][0]["title"] == "Active Announcement"
    assert home.json()["museum_information"]["museum_name"] == "Configured Museum"

    news = client.get("/api/v1/public/news")
    assert [item["title"] for item in news.json()] == ["Published News"]
    assert client.get(f"/api/v1/public/news/{published_news_id}").status_code == 200

    announcements = client.get("/api/v1/public/announcements")
    assert [item["title"] for item in announcements.json()] == ["Active Announcement"]

    articles = client.get("/api/v1/public/articles", params={"category": "Heritage"})
    assert [item["title"] for item in articles.json()] == ["Published Article"]
    assert client.get(f"/api/v1/public/articles/{article_id}").status_code == 200

    museum_info = client.get("/api/v1/public/museum-info")
    assert museum_info.json()["latitude"] == 15.0

    programs = client.get("/api/v1/public/programs")
    assert programs.json() == [{"id": str(active_program), "name": "Bachelor of Science in Agriculture"}]


def test_empty_museum_information_uses_to_be_configured(test_context):
    client, _, _, _ = test_context
    response = client.get("/api/v1/public/museum-info")
    assert response.status_code == 200
    assert response.json()["museum_name"] == "To be configured."


def test_visitor_artifact_access_hides_admin_fields(test_context):
    client, database, _, _ = test_context
    artifact = insert_artifact(
        database,
        custom_fields=[
            {"id": "weight", "label": "Weight", "value": "3.5", "unit": "kg", "type": "number"},
            {"id": "empty", "label": "Remarks", "value": "", "unit": None, "type": "text"},
        ],
    )
    _, headers = create_guest(client)

    list_response = client.get("/api/v1/visitor/artifacts", headers=headers)
    assert list_response.status_code == 200
    item = list_response.json()["items"][0]
    assert item["artifact_code"] == "ART-V1"
    assert "ai_index_status" not in item
    assert "created_by" not in item

    details = client.get(f"/api/v1/visitor/artifacts/{artifact['_id']}", headers=headers)
    assert details.status_code == 200
    assert details.json()["name"] == "Wooden Plow"
    assert "created_by" not in details.json()
    assert details.json()["custom_fields"] == [{"label": "Weight", "value": "3.5", "unit": "kg", "type": "number"}]


def test_visitor_artifacts_hide_drafts(test_context):
    client, database, _, _ = test_context
    published = insert_artifact(database, code="ART-PUBLISHED", status="published")
    draft = insert_artifact(database, code="ART-DRAFT", status="draft")
    _, headers = create_guest(client)

    list_response = client.get("/api/v1/visitor/artifacts", headers=headers)
    assert list_response.status_code == 200
    codes = [item["artifact_code"] for item in list_response.json()["items"]]
    assert "ART-PUBLISHED" in codes
    assert "ART-DRAFT" not in codes

    assert client.get(f"/api/v1/visitor/artifacts/{published['_id']}", headers=headers).status_code == 200
    assert client.get(f"/api/v1/visitor/artifacts/{draft['_id']}", headers=headers).status_code == 404
