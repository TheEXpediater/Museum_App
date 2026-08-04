from __future__ import annotations

from datetime import timedelta
from io import BytesIO
import os
from pathlib import Path
from types import SimpleNamespace

import mongomock
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.ai.embedding_service import EmbeddingError, EmbeddingResult
from app.auth.jwt_handler import create_access_token
from app.auth.password import hash_password
from app.config import Settings
from app.repositories import artifact_repository
from app.schemas.ai import RecognitionResponse
from app.services.artifact_indexing_service import ArtifactIndexingService
from app.services.artifact_recognition_service import (
    RecognitionInputError,
    RecognitionUnavailableError,
    ArtifactRecognitionService,
)
from app.utils import utc_now
from app.vector.artifact_vector_repository import point_id_for_image
from app.vector.qdrant_manager import QdrantUnavailableError
from main import create_app


JWT_SECRET = "test-secret-key-that-is-long-enough"
ADMIN_EMAIL = "admin@example.com"
ADMIN_PASSWORD = "ChangeThisPassword123!"

os.environ.setdefault("MONGODB_URL", "mongodb://localhost:27017")
os.environ.setdefault("MONGODB_DATABASE", "museum_guide_test")
os.environ.setdefault("JWT_SECRET_KEY", JWT_SECRET)


def make_settings(tmp_path: Path | None = None, **overrides) -> Settings:
    values = {
        "mongodb_url": "mongodb://localhost:27017",
        "mongodb_database": "museum_guide_test",
        "jwt_secret_key": JWT_SECRET,
        "max_image_size_mb": 1,
        "ai_recognition_strong_threshold": 0.8,
        "ai_recognition_possible_threshold": 0.5,
        "_env_file": None,
    }
    if tmp_path is not None:
        values["upload_directory"] = str(tmp_path / "uploads" / "images")
    values.update(overrides)
    return Settings(**values)


def image_bytes(format_name: str = "JPEG", size: tuple[int, int] = (32, 32)) -> bytes:
    buffer = BytesIO()
    Image.new("RGB", size, color=(80, 140, 80)).save(buffer, format=format_name)
    return buffer.getvalue()


def create_stored_image(settings: Settings, filename: str) -> str:
    settings.upload_path.mkdir(parents=True, exist_ok=True)
    (settings.upload_path / filename).write_bytes(image_bytes("PNG" if filename.endswith(".png") else "JPEG"))
    return f"uploads/images/{filename}"


def insert_artifact(database, *, code: str, image_paths: list[str] | None = None, name: str = "Wooden Plow") -> dict:
    return artifact_repository.create_artifact(
        database,
        {
            "artifact_code": code,
            "name": name,
            "description": "A traditional farming tool.",
            "category": "Farm Tools",
            "origin": "Pampanga",
            "historical_period": "Early 20th Century",
            "material": "Wood",
            "dimensions": "120 cm x 35 cm",
            "condition": "Good",
            "image_paths": image_paths or [],
            "primary_image_path": image_paths[0] if image_paths else None,
            "created_by": "admin",
        },
    )


class FakeEmbeddingService:
    def __init__(self, *, fail_names: set[str] | None = None, fail_all: bool = False) -> None:
        self.fail_names = fail_names or set()
        self.fail_all = fail_all
        self.calls: list[str] = []

    def embed_image(self, image_source) -> EmbeddingResult:
        source_name = Path(str(image_source)).name if not isinstance(image_source, bytes) else "bytes"
        self.calls.append(source_name)
        if self.fail_all or source_name in self.fail_names:
            raise EmbeddingError("fake embedding failure")
        return EmbeddingResult(vector=[1.0, 0.0, 0.0], dimension=3)


class FakeQdrantManager:
    def __init__(self, *, count: int = 10, fail: bool = False) -> None:
        self.count = count
        self.fail = fail
        self.ensured_dimensions: list[int] = []

    def ensure_collection(self, vector_size: int):
        if self.fail:
            raise QdrantUnavailableError("Qdrant is unavailable.")
        self.ensured_dimensions.append(vector_size)
        return SimpleNamespace(status="ready", ready=True, points_count=self.count, vector_size=vector_size, distance="cosine")

    def get_collection_status(self, expected_vector_size=None):
        if self.fail:
            raise QdrantUnavailableError("Qdrant is unavailable.")
        return SimpleNamespace(
            exists=True,
            ready=True,
            status="ready",
            points_count=self.count,
            vector_size=expected_vector_size or 3,
            distance="cosine",
            message=None,
        )

    def count_vectors(self) -> int:
        return self.count


class FakeVectorRepository:
    def __init__(self, search_response=None) -> None:
        self.points: dict[str, dict] = {}
        self.deleted_points: list[str] = []
        self.deleted_artifacts: list[str] = []
        self.search_response = search_response or []

    def upsert_image_vector(self, vector: list[float], payload) -> str:
        point_id = point_id_for_image(payload.artifact_id, payload.image_path)
        self.points[point_id] = {"vector": vector, "payload": payload.as_qdrant_payload()}
        return point_id

    def delete_point(self, point_id: str) -> None:
        self.deleted_points.append(point_id)
        self.points.pop(point_id, None)

    def delete_image_vector(self, artifact_id: str, image_path: str) -> None:
        self.delete_point(point_id_for_image(artifact_id, image_path))

    def delete_artifact_vectors(self, artifact_id: str) -> None:
        self.deleted_artifacts.append(artifact_id)
        for point_id, point in list(self.points.items()):
            if point["payload"].get("artifact_id") == artifact_id:
                self.points.pop(point_id, None)

    def search_vectors(self, vector: list[float], *, limit: int = 5):
        return self.search_response[:limit]


def indexing_service(settings: Settings, vector_repo: FakeVectorRepository | None = None, **embedding_kwargs) -> ArtifactIndexingService:
    return ArtifactIndexingService(
        settings,
        embedding_service=FakeEmbeddingService(**embedding_kwargs),
        qdrant_manager=FakeQdrantManager(),
        vector_repository=vector_repo or FakeVectorRepository(),
    )


def make_hit(artifact_id: str, score: float, image_path: str = "uploads/images/one.jpg", point_id: str | None = None):
    return SimpleNamespace(
        id=point_id or point_id_for_image(artifact_id, image_path),
        score=score,
        payload={
            "artifact_id": artifact_id,
            "artifact_code": "ART",
            "artifact_name": "Artifact",
            "category": "Farm Tools",
            "image_path": image_path,
        },
    )


def test_indexing_one_image_uses_deterministic_point_id(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()["museum_guide_test"]
    path = create_stored_image(settings, "one.jpg")
    artifact = insert_artifact(database, code="ART-1", image_paths=[path])
    vector_repo = FakeVectorRepository()

    result = indexing_service(settings, vector_repo).index_artifact(database, artifact)

    expected_id = point_id_for_image(str(artifact["_id"]), path)
    assert result.ai_index_status == "indexed"
    assert result.indexed_images == 1
    assert list(vector_repo.points) == [expected_id]
    assert vector_repo.points[expected_id]["payload"]["artifact_name"] == "Wooden Plow"


def test_indexing_multiple_images(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()["museum_guide_test"]
    paths = [create_stored_image(settings, "one.jpg"), create_stored_image(settings, "two.png")]
    artifact = insert_artifact(database, code="ART-2", image_paths=paths)
    vector_repo = FakeVectorRepository()

    result = indexing_service(settings, vector_repo).index_artifact(database, artifact)

    assert result.indexed_images == 2
    assert len(vector_repo.points) == 2


def test_metadata_change_updates_vector_payload(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()["museum_guide_test"]
    path = create_stored_image(settings, "one.jpg")
    artifact = insert_artifact(database, code="ART-3", image_paths=[path])
    vector_repo = FakeVectorRepository()
    service = indexing_service(settings, vector_repo)
    service.index_artifact(database, artifact)

    previous = dict(artifact)
    current = artifact_repository.update_artifact(database, artifact["_id"], {"name": "Updated Name"})
    service.synchronize_after_update(database, previous, current)

    point_id = point_id_for_image(str(artifact["_id"]), path)
    assert vector_repo.points[point_id]["payload"]["artifact_name"] == "Updated Name"


def test_removed_image_vector_is_deleted(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()["museum_guide_test"]
    first = create_stored_image(settings, "one.jpg")
    second = create_stored_image(settings, "two.jpg")
    artifact = insert_artifact(database, code="ART-4", image_paths=[first, second])
    vector_repo = FakeVectorRepository()
    service = indexing_service(settings, vector_repo)
    service.index_artifact(database, artifact)

    current = artifact_repository.update_artifact(database, artifact["_id"], {"image_paths": [first], "primary_image_path": first})
    service.synchronize_after_update(database, artifact, current)

    assert point_id_for_image(str(artifact["_id"]), second) in vector_repo.deleted_points


def test_deleting_artifact_vectors_removes_all_points(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()["museum_guide_test"]
    paths = [create_stored_image(settings, "one.jpg"), create_stored_image(settings, "two.jpg")]
    artifact = insert_artifact(database, code="ART-5", image_paths=paths)
    vector_repo = FakeVectorRepository()
    service = indexing_service(settings, vector_repo)
    service.index_artifact(database, artifact)

    assert service.delete_artifact_vectors(str(artifact["_id"])) is True
    assert str(artifact["_id"]) in vector_repo.deleted_artifacts
    assert vector_repo.points == {}


def test_ai_failure_updates_retryable_status_without_deleting_artifact(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()["museum_guide_test"]
    path = create_stored_image(settings, "one.jpg")
    artifact = insert_artifact(database, code="ART-6", image_paths=[path])

    result = indexing_service(settings, fail_all=True).index_artifact(database, artifact)
    stored = artifact_repository.get_artifact(database, artifact["_id"])

    assert result.ai_index_status == "failed"
    assert stored is not None
    assert stored["ai_index_status"] == "failed"
    assert stored["ai_index_error"]


def test_partial_indexing_status(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()["museum_guide_test"]
    paths = [create_stored_image(settings, "one.jpg"), create_stored_image(settings, "two.jpg")]
    artifact = insert_artifact(database, code="ART-7", image_paths=paths)

    result = indexing_service(settings, fail_names={"two.jpg"}).index_artifact(database, artifact)

    assert result.ai_index_status == "partial"
    assert result.indexed_images == 1
    assert result.failed_images == 1


def test_recognition_returns_one_strong_match(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()["museum_guide_test"]
    artifact = insert_artifact(database, code="ART-R1", image_paths=["uploads/images/one.jpg"])
    vector_repo = FakeVectorRepository(search_response=[make_hit(str(artifact["_id"]), 0.91)])
    service = ArtifactRecognitionService(
        settings,
        embedding_service=FakeEmbeddingService(),
        qdrant_manager=FakeQdrantManager(count=1),
        vector_repository=vector_repo,
    )

    response = service.recognize(database, image_bytes=image_bytes(), content_type="image/jpeg", base_url="http://testserver/")

    assert response.matched is True
    assert response.match_level == "strong"
    assert response.best_match.artifact.id == str(artifact["_id"])


def test_recognition_groups_multiple_image_hits_into_one_artifact(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()["museum_guide_test"]
    artifact = insert_artifact(database, code="ART-R2", image_paths=["uploads/images/one.jpg", "uploads/images/two.jpg"])
    vector_repo = FakeVectorRepository(
        search_response=[
            make_hit(str(artifact["_id"]), 0.72, "uploads/images/one.jpg"),
            make_hit(str(artifact["_id"]), 0.88, "uploads/images/two.jpg"),
        ]
    )
    service = ArtifactRecognitionService(settings, embedding_service=FakeEmbeddingService(), qdrant_manager=FakeQdrantManager(count=2), vector_repository=vector_repo)

    response = service.recognize(database, image_bytes=image_bytes(), content_type="image/jpeg", base_url="http://testserver/")

    assert response.best_match.supporting_image_hits == 2
    assert response.best_match.similarity_score == 0.88
    assert response.best_match.matched_image_path == "uploads/images/two.jpg"


def test_recognition_ranks_unique_artifacts(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()["museum_guide_test"]
    lower = insert_artifact(database, code="ART-R3A", image_paths=["uploads/images/one.jpg"], name="Lower")
    higher = insert_artifact(database, code="ART-R3B", image_paths=["uploads/images/two.jpg"], name="Higher")
    vector_repo = FakeVectorRepository(
        search_response=[
            make_hit(str(lower["_id"]), 0.62, "uploads/images/one.jpg"),
            make_hit(str(higher["_id"]), 0.93, "uploads/images/two.jpg"),
        ]
    )
    service = ArtifactRecognitionService(settings, embedding_service=FakeEmbeddingService(), qdrant_manager=FakeQdrantManager(count=2), vector_repository=vector_repo)

    response = service.recognize(database, image_bytes=image_bytes(), content_type="image/jpeg", base_url="http://testserver/")

    assert response.best_match.artifact.name == "Higher"
    assert response.other_matches[0].artifact.name == "Lower"


def test_recognition_below_threshold_returns_no_match(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()["museum_guide_test"]
    artifact = insert_artifact(database, code="ART-R4", image_paths=["uploads/images/one.jpg"])
    vector_repo = FakeVectorRepository(search_response=[make_hit(str(artifact["_id"]), 0.49)])
    service = ArtifactRecognitionService(settings, embedding_service=FakeEmbeddingService(), qdrant_manager=FakeQdrantManager(count=1), vector_repository=vector_repo)

    response = service.recognize(database, image_bytes=image_bytes(), content_type="image/jpeg", base_url="http://testserver/")

    assert response.matched is False
    assert response.match_level == "no_match"


def test_stale_vector_is_dropped_from_recognition(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()["museum_guide_test"]
    stale_id = "64b79e7a03d7692666d42b01"
    point_id = point_id_for_image(stale_id, "uploads/images/stale.jpg")
    vector_repo = FakeVectorRepository(search_response=[make_hit(stale_id, 0.95, "uploads/images/stale.jpg", point_id)])
    service = ArtifactRecognitionService(settings, embedding_service=FakeEmbeddingService(), qdrant_manager=FakeQdrantManager(count=1), vector_repository=vector_repo)

    response = service.recognize(database, image_bytes=image_bytes(), content_type="image/jpeg", base_url="http://testserver/")

    assert response.matched is False
    assert point_id in vector_repo.deleted_points


def test_invalid_recognition_file_type_is_rejected(tmp_path):
    service = ArtifactRecognitionService(make_settings(tmp_path), embedding_service=FakeEmbeddingService(), qdrant_manager=FakeQdrantManager(), vector_repository=FakeVectorRepository())
    with pytest.raises(RecognitionInputError) as exc:
        service.recognize(mongomock.MongoClient()["museum_guide_test"], image_bytes=b"abc", content_type="text/plain", base_url="http://testserver/")
    assert exc.value.status_code == 415


def test_invalid_recognition_image_content_is_rejected(tmp_path):
    service = ArtifactRecognitionService(make_settings(tmp_path), embedding_service=FakeEmbeddingService(), qdrant_manager=FakeQdrantManager(), vector_repository=FakeVectorRepository())
    with pytest.raises(RecognitionInputError) as exc:
        service.recognize(mongomock.MongoClient()["museum_guide_test"], image_bytes=b"not an image", content_type="image/jpeg", base_url="http://testserver/")
    assert exc.value.status_code == 415


def test_oversized_recognition_image_is_rejected(tmp_path):
    service = ArtifactRecognitionService(make_settings(tmp_path, max_image_size_mb=1), embedding_service=FakeEmbeddingService(), qdrant_manager=FakeQdrantManager(), vector_repository=FakeVectorRepository())
    with pytest.raises(RecognitionInputError) as exc:
        service.recognize(mongomock.MongoClient()["museum_guide_test"], image_bytes=b"x" * (1024 * 1024 + 1), content_type="image/jpeg", base_url="http://testserver/")
    assert exc.value.status_code == 413


def test_empty_qdrant_collection_returns_no_match(tmp_path):
    service = ArtifactRecognitionService(make_settings(tmp_path), embedding_service=FakeEmbeddingService(), qdrant_manager=FakeQdrantManager(count=0), vector_repository=FakeVectorRepository())
    response = service.recognize(mongomock.MongoClient()["museum_guide_test"], image_bytes=image_bytes(), content_type="image/jpeg", base_url="http://testserver/")
    assert response.matched is False
    assert "No indexed" in response.message


def test_qdrant_unavailable_raises_safe_recognition_error(tmp_path):
    service = ArtifactRecognitionService(make_settings(tmp_path), embedding_service=FakeEmbeddingService(), qdrant_manager=FakeQdrantManager(fail=True), vector_repository=FakeVectorRepository())
    with pytest.raises(RecognitionUnavailableError):
        service.recognize(mongomock.MongoClient()["museum_guide_test"], image_bytes=image_bytes(), content_type="image/jpeg", base_url="http://testserver/")


def test_openclip_unavailable_raises_safe_recognition_error(tmp_path):
    service = ArtifactRecognitionService(make_settings(tmp_path), embedding_service=FakeEmbeddingService(fail_all=True), qdrant_manager=FakeQdrantManager(), vector_repository=FakeVectorRepository())
    with pytest.raises(RecognitionUnavailableError):
        service.recognize(mongomock.MongoClient()["museum_guide_test"], image_bytes=image_bytes(), content_type="image/jpeg", base_url="http://testserver/")


@pytest.fixture()
def route_context(tmp_path):
    settings = make_settings(tmp_path, ai_enabled=False, cors_origins="http://testserver")
    database = mongomock.MongoClient()["museum_guide_test"]
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
        yield client, database, str(admin_id)


def auth_headers(client: TestClient) -> dict[str, str]:
    response = client.post("/api/v1/auth/login", json={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD})
    assert response.status_code == 200
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def test_admin_authorization_required_for_index_maintenance(route_context):
    client, _, _ = route_context
    response = client.get("/api/v1/ai/index/status")
    assert response.status_code == 401


def test_recognition_endpoint_accepts_authenticated_visitors(route_context, monkeypatch):
    client, database, _ = route_context
    guest_id = database.guest_sessions.insert_one(
        {
            "first_name": "Maria",
            "last_name": "Santos",
            "display_name": "Maria Santos",
            "relationship_type": "General Visitor",
            "relationship_detail": None,
            "batch_or_graduation_year": None,
            "office_or_department": None,
            "role": "guest",
            "created_at": utc_now(),
            "expires_at": utc_now() + timedelta(hours=24),
            "last_seen_at": utc_now(),
            "device_session_id": "test-device",
        }
    ).inserted_id
    token, _ = create_access_token(str(guest_id), "", "guest", make_settings())

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
    response = client.post(
        "/api/v1/ai/recognize",
        files={"image": ("query.jpg", image_bytes(), "image/jpeg")},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 200
    assert response.json()["matched"] is False


def test_dashboard_counts_use_real_artifact_data(route_context):
    client, database, _ = route_context
    insert_artifact(database, code="ART-D1", image_paths=["uploads/images/one.jpg"])
    indexed = insert_artifact(database, code="ART-D2", image_paths=["uploads/images/two.jpg", "uploads/images/three.jpg"])
    artifact_repository.update_ai_index_state(database, indexed["_id"], status="indexed", indexed_image_count=2)

    response = client.get("/api/v1/admin/dashboard", headers=auth_headers(client))

    assert response.status_code == 200
    body = response.json()
    assert body["total_artifacts"] == 2
    assert body["total_images"] == 3
    assert body["indexed_artifacts"] == 1
    assert body["recent_artifacts"]


def test_backfill_script_dry_run(monkeypatch, tmp_path):
    from scripts import index_existing_artifacts

    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()["museum_guide_test"]
    monkeypatch.setattr("sys.argv", ["index_existing_artifacts", "--dry-run"])
    monkeypatch.setattr(index_existing_artifacts, "load_settings", lambda _reporter: settings)
    monkeypatch.setattr(index_existing_artifacts, "verify_ai", lambda _settings, _reporter: True)
    monkeypatch.setattr(index_existing_artifacts, "maybe_rebuild_collection", lambda _settings, _args, _reporter: True)
    monkeypatch.setattr(index_existing_artifacts.mongo_manager, "connect", lambda _settings: database)
    monkeypatch.setattr(index_existing_artifacts.mongo_manager, "close", lambda: None)

    class FakeBackfillService:
        def index_all(self, db, *, artifact_id=None, dry_run=False):
            assert db is database
            assert artifact_id is None
            assert dry_run is True
            return {
                "total_artifacts": 1,
                "total_images": 1,
                "indexed_images": 0,
                "failed_images": 0,
                "skipped_images": 1,
                "duration": 0.001,
                "errors": [],
            }

    monkeypatch.setattr(index_existing_artifacts.ArtifactIndexingService, "from_settings", lambda _settings: FakeBackfillService())

    assert index_existing_artifacts.main() == 0
