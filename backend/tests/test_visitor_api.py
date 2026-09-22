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


def create_student(client: TestClient, **overrides) -> dict:
    response = client.post("/api/v1/student/register", json=student_payload(**overrides))
    assert response.status_code == 201, response.text
    return response.json()


def activate_student(client: TestClient, student_id: str) -> dict:
    response = client.patch(
        f"/api/v1/admin/students/{student_id}/status",
        json={"account_status": "active"},
        headers=admin_headers(client),
    )
    assert response.status_code == 200, response.text
    return response.json()


def create_active_student(client: TestClient, **overrides) -> tuple[dict, dict[str, str]]:
    payload = student_payload(**overrides)
    response = client.post("/api/v1/student/register", json=payload)
    assert response.status_code == 201, response.text
    registration = response.json()
    activate_student(client, registration["id"])
    login = client.post(
        "/api/v1/student/login",
        json={"identifier": payload["student_id"], "password": payload["password"]},
    )
    assert login.status_code == 200, login.text
    body = login.json()
    return body, {"Authorization": f"Bearer {body['access_token']}"}


def insert_artifact(
    database,
    *,
    code: str = "ART-V1",
    status: str | None = None,
    custom_fields: list[dict] | None = None,
    image_paths: list[str] | None = None,
    primary_image_path: str | None = None,
    visitor_gallery_image_paths: list[str] | None = None,
    visitor_gallery_configured: bool | None = None,
) -> dict:
    paths = image_paths or []
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
        "image_paths": paths,
        "primary_image_path": primary_image_path if primary_image_path is not None else (paths[0] if paths else None),
        "created_by": "admin",
    }
    if status is not None:
        data["status"] = status
    if visitor_gallery_image_paths is not None:
        data["visitor_gallery_image_paths"] = visitor_gallery_image_paths
    if visitor_gallery_configured is not None:
        data["visitor_gallery_configured"] = visitor_gallery_configured
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


def test_student_registration_is_pending_and_hashes_password(test_context):
    client, database, _, _ = test_context
    body = create_student(client)

    assert body["status"] == "pending"
    assert body["student_id"] == "PSAU-2026-001"
    assert "message" in body
    assert "access_token" not in body
    assert "password_hash" not in str(body)

    stored = database.students.find_one({"student_id_normalized": "PSAU-2026-001"})
    assert stored is not None
    assert stored["account_status"] == "pending"
    assert stored["is_active"] is False
    assert stored["last_login_at"] is None
    assert stored["password_hash"] != "Student123"
    assert verify_password("Student123", stored["password_hash"])

    # A pending account cannot log in yet, even with the correct password.
    pending_login = client.post(
        "/api/v1/student/login",
        json={"identifier": "psau-2026-001", "password": "Student123"},
    )
    assert pending_login.status_code == 403
    assert "awaiting administrator approval" in pending_login.json()["detail"]


def test_activated_student_can_login_by_id_or_email_and_updates_last_login(test_context):
    client, database, _, _ = test_context
    body, headers = create_active_student(client)

    assert body["account_type"] == "student"
    assert body["profile"]["student_id"] == "PSAU-2026-001"
    assert body["profile"]["email"] == "juan.reyes@example.com"
    assert body["profile"]["display_name"] == "Juan D. Reyes"
    assert body["profile"]["role"] == "student"

    stored = database.students.find_one({"student_id_normalized": "PSAU-2026-001"})
    assert stored is not None
    assert stored["last_login_at"] is not None

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

    # Wrong password on a still-pending account must stay a generic 401 - the
    # password check runs before the status is ever inspected, so a bad
    # password never leaks that the account exists or what state it is in.
    wrong_password = client.post("/api/v1/student/login", json={"identifier": "PSAU-2026-001", "password": "wrong"})
    assert wrong_password.status_code == 401
    assert wrong_password.json()["detail"] == "Invalid student ID, email, or password."
    assert "password_hash" not in wrong_password.text

    # Correct password on a still-pending account is a distinct, non-generic 403.
    pending = client.post("/api/v1/student/login", json={"identifier": "PSAU-2026-001", "password": "Student123"})
    assert pending.status_code == 403
    assert "awaiting administrator approval" in pending.json()["detail"]


def test_role_boundaries_for_admin_visitor_and_recognition(test_context, monkeypatch):
    client, database, _, _ = test_context
    _, guest_headers = create_guest(client)
    _, student_headers = create_active_student(client)

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

    student, _ = create_active_student(client)
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


def test_admin_can_list_and_search_students_and_non_admin_is_rejected(test_context):
    client, _, _, _ = test_context
    pending = create_student(client)
    active_body, _ = create_active_student(client, student_id="PSAU-2026-002", email="active@example.com")
    _, guest_headers = create_guest(client)

    assert client.get("/api/v1/admin/students", headers=guest_headers).status_code == 403
    assert client.get("/api/v1/admin/students").status_code == 401

    headers = admin_headers(client)
    default_list = client.get("/api/v1/admin/students", headers=headers)
    assert default_list.status_code == 200
    assert [item["student_id"] for item in default_list.json()] == [pending["student_id"]]

    active_list = client.get("/api/v1/admin/students", params={"status": "active"}, headers=headers)
    assert [item["student_id"] for item in active_list.json()] == [active_body["profile"]["student_id"]]

    all_list = client.get("/api/v1/admin/students", params={"status": "all"}, headers=headers)
    assert len(all_list.json()) == 2

    search = client.get("/api/v1/admin/students", params={"status": "all", "search": "Reyes"}, headers=headers)
    assert len(search.json()) == 2
    for item in default_list.json() + active_list.json():
        assert "password_hash" not in item


def test_admin_student_detail_hides_password_hash(test_context):
    client, _, _, _ = test_context
    registration = create_student(client)
    headers = admin_headers(client)

    response = client.get(f"/api/v1/admin/students/{registration['id']}", headers=headers)
    assert response.status_code == 200
    body = response.json()
    assert body["account_status"] == "pending"
    assert body["student_id"] == registration["student_id"]
    assert "password_hash" not in body
    assert "password_hash" not in response.text


def test_admin_activate_deactivate_reactivate_lifecycle(test_context):
    client, database, _, _ = test_context
    registration = create_student(client)
    headers = admin_headers(client)
    payload = student_payload()

    # Still pending: login is rejected.
    still_pending = client.post(
        "/api/v1/student/login",
        json={"identifier": payload["student_id"], "password": payload["password"]},
    )
    assert still_pending.status_code == 403

    # Admin activates: student can now log in, and last_login_at updates.
    activated = activate_student(client, registration["id"])
    assert activated["account_status"] == "active"
    assert activated["approved_at"] is not None

    login = client.post(
        "/api/v1/student/login",
        json={"identifier": payload["student_id"], "password": payload["password"]},
    )
    assert login.status_code == 200
    token = login.json()["access_token"]
    stored_after_login = database.students.find_one({"student_id_normalized": "PSAU-2026-001"})
    assert stored_after_login["last_login_at"] is not None

    # Admin deactivates: the *existing* token immediately stops working, and login is rejected.
    deactivate = client.patch(
        f"/api/v1/admin/students/{registration['id']}/status",
        json={"account_status": "inactive"},
        headers=headers,
    )
    assert deactivate.status_code == 200
    assert deactivate.json()["account_status"] == "inactive"

    assert client.get("/api/v1/visitor/me", headers={"Authorization": f"Bearer {token}"}).status_code == 401

    rejected_login = client.post(
        "/api/v1/student/login",
        json={"identifier": payload["student_id"], "password": payload["password"]},
    )
    assert rejected_login.status_code == 403
    assert "inactive" in rejected_login.json()["detail"].lower()

    # Admin reactivates: login works again, and the original approval timestamp is preserved.
    reactivated = client.patch(
        f"/api/v1/admin/students/{registration['id']}/status",
        json={"account_status": "active"},
        headers=headers,
    )
    assert reactivated.status_code == 200
    assert reactivated.json()["account_status"] == "active"
    assert reactivated.json()["approved_at"] == activated["approved_at"]

    relogin = client.post(
        "/api/v1/student/login",
        json={"identifier": payload["student_id"], "password": payload["password"]},
    )
    assert relogin.status_code == 200


def test_legacy_student_records_without_account_status_still_work(test_context):
    client, database, _, _ = test_context
    now = utc_now()

    def insert_legacy_student(student_id: str, email: str, is_active: bool) -> dict:
        document = {
            "student_id": student_id,
            "student_id_normalized": student_id.upper(),
            "first_name": "Legacy",
            "last_name": "Student",
            "display_name": "Legacy Student",
            "year_level": "Third Year",
            "course": "Bachelor of Science in Agriculture",
            "email": email,
            "email_normalized": email.lower(),
            "password_hash": hash_password("LegacyPass123"),
            "role": "student",
            "is_active": is_active,
            "created_at": now,
            "updated_at": now,
            "last_login_at": None,
        }
        inserted_id = database.students.insert_one(document).inserted_id
        return str(inserted_id)

    active_id = insert_legacy_student("PSAU-LEGACY-ACTIVE", "legacy.active@example.com", True)
    inactive_id = insert_legacy_student("PSAU-LEGACY-INACTIVE", "legacy.inactive@example.com", False)

    headers = admin_headers(client)
    active_detail = client.get(f"/api/v1/admin/students/{active_id}", headers=headers)
    assert active_detail.status_code == 200
    assert active_detail.json()["account_status"] == "active"

    inactive_detail = client.get(f"/api/v1/admin/students/{inactive_id}", headers=headers)
    assert inactive_detail.status_code == 200
    assert inactive_detail.json()["account_status"] == "inactive"

    active_login = client.post(
        "/api/v1/student/login",
        json={"identifier": "PSAU-LEGACY-ACTIVE", "password": "LegacyPass123"},
    )
    assert active_login.status_code == 200

    inactive_login = client.post(
        "/api/v1/student/login",
        json={"identifier": "PSAU-LEGACY-INACTIVE", "password": "LegacyPass123"},
    )
    assert inactive_login.status_code == 403
    assert "inactive" in inactive_login.json()["detail"].lower()


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


def test_visitor_gallery_defaults_to_main_plus_first_five_non_main(test_context):
    client, database, _, _ = test_context
    paths = [f"uploads/images/gallery-{index}.jpg" for index in range(1, 12)]
    main_path = paths[2]
    artifact = insert_artifact(
        database,
        code="ART-GALLERY-DEFAULT",
        status="published",
        image_paths=paths,
        primary_image_path=main_path,
        visitor_gallery_image_paths=[],
        visitor_gallery_configured=False,
    )
    _, visitor_headers = create_guest(client)

    details = client.get(f"/api/v1/visitor/artifacts/{artifact['_id']}", headers=visitor_headers)

    assert details.status_code == 200
    expected_paths = [main_path, paths[0], paths[1], paths[3], paths[4], paths[5]]
    assert details.json()["image_urls"] == [f"http://testserver/{path}" for path in expected_paths]

    admin_details = client.get(f"/api/v1/artifacts/{artifact['_id']}", headers=admin_headers(client))
    assert admin_details.status_code == 200
    assert admin_details.json()["visitor_gallery_image_paths"] == expected_paths[1:]
    assert admin_details.json()["visitor_gallery_configured"] is False


def test_visitor_gallery_intentional_zero_selection_persists(test_context):
    client, database, _, _ = test_context
    paths = [f"uploads/images/zero-{index}.jpg" for index in range(1, 8)]
    main_path = paths[0]
    artifact = insert_artifact(
        database,
        code="ART-GALLERY-ZERO",
        status="published",
        image_paths=paths,
        primary_image_path=main_path,
        visitor_gallery_image_paths=[],
        visitor_gallery_configured=False,
    )
    headers = admin_headers(client)

    update = client.patch(
        f"/api/v1/artifacts/{artifact['_id']}",
        data={
            "visitor_gallery_image_paths": "[]",
            "visitor_gallery_configured": "true",
        },
        headers=headers,
    )
    assert update.status_code == 200, update.text
    assert update.json()["visitor_gallery_configured"] is True
    assert update.json()["visitor_gallery_image_paths"] == []

    _, visitor_headers = create_guest(client)
    details = client.get(f"/api/v1/visitor/artifacts/{artifact['_id']}", headers=visitor_headers)

    assert details.status_code == 200
    assert details.json()["image_urls"] == [f"http://testserver/{main_path}"]


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
