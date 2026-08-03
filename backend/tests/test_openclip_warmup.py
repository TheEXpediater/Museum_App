from __future__ import annotations

from contextlib import contextmanager
from concurrent.futures import Future
from io import BytesIO
from pathlib import Path
from types import SimpleNamespace

import bcrypt
import mongomock
from fastapi.testclient import TestClient
from PIL import Image

from app.ai.embedding_service import EmbeddingResult
from app.auth.password import hash_password, verify_password
from app.config import Settings
from app.repositories import artifact_repository
from app.services.openclip_warmup_service import (
    SAFE_WARMUP_FAILURE,
    WARMUP_FAILED,
    WARMUP_LOADED,
    WARMUP_LOADING,
    OpenCLIPWarmupService,
    OpenCLIPWarmupSnapshot,
)
from app.utils import utc_now
from main import create_app


JWT_SECRET = "test-secret-key-that-is-long-enough"
ADMIN_EMAIL = "admin@example.com"
ADMIN_PASSWORD = "ChangeThisPassword123!"


def make_settings(tmp_path: Path | None = None, **overrides) -> Settings:
    values = {
        "mongodb_url": "mongodb://localhost:27017",
        "mongodb_database": "museum_guide_test",
        "jwt_secret_key": JWT_SECRET,
        "max_image_size_mb": 1,
        "_env_file": None,
    }
    if tmp_path is not None:
        values["upload_directory"] = str(tmp_path / "uploads" / "images")
    values.update(overrides)
    return Settings(**values)


class FakeModelManager:
    def __init__(self, *, loaded: bool = False, dimension: int | None = None) -> None:
        self._loaded = (
            SimpleNamespace(device="cpu", embedding_dimension=dimension, model_name="fake", pretrained="fake")
            if loaded
            else None
        )

    @property
    def is_loaded(self) -> bool:
        return self._loaded is not None

    @property
    def loaded_model(self):
        return self._loaded

    @property
    def actual_device(self) -> str | None:
        return self._loaded.device if self._loaded else None

    @property
    def embedding_dimension(self) -> int | None:
        return self._loaded.embedding_dimension if self._loaded else None

    def set_embedding_dimension(self, dimension: int) -> None:
        if self._loaded is None:
            self._loaded = SimpleNamespace(
                device="cpu",
                embedding_dimension=dimension,
                model_name="fake",
                pretrained="fake",
            )
        else:
            self._loaded.embedding_dimension = dimension


class ControlledExecutor:
    def __init__(self) -> None:
        self.tasks: list = []

    def submit(self, fn):
        future: Future = Future()
        self.tasks.append(fn)
        return future


class ImmediateExecutor:
    def submit(self, fn):
        future: Future = Future()
        try:
            result = fn()
            future.set_result(result)
        except Exception as exc:
            future.set_exception(exc)
        return future


class RecordingEmbeddingFactory:
    def __init__(self, *, fail: bool = False) -> None:
        self.fail = fail
        self.sources: list[object] = []

    def __call__(self, _settings, *, model_manager):
        factory = self

        class RecordingEmbeddingService:
            def embed_image(self, image_source) -> EmbeddingResult:
                factory.sources.append(image_source)
                if factory.fail:
                    raise RuntimeError("technical cache path C:/secret/cache failed")
                model_manager.set_embedding_dimension(512)
                return EmbeddingResult(vector=[1.0, 0.0], dimension=512)

        return RecordingEmbeddingService()


def test_warmup_starts_once_and_duplicate_returns_loading(tmp_path):
    executor = ControlledExecutor()
    service = OpenCLIPWarmupService(
        make_settings(tmp_path),
        model_manager=FakeModelManager(),
        embedding_service_factory=RecordingEmbeddingFactory(),
        executor=executor,
    )

    first = service.start()
    second = service.start()

    assert first.state == WARMUP_LOADING
    assert second.state == WARMUP_LOADING
    assert len(executor.tasks) == 1


def test_warmup_loaded_response_uses_in_memory_image(tmp_path):
    factory = RecordingEmbeddingFactory()
    service = OpenCLIPWarmupService(
        make_settings(tmp_path),
        model_manager=FakeModelManager(),
        embedding_service_factory=factory,
        executor=ImmediateExecutor(),
    )

    response = service.start()

    assert response.state == WARMUP_LOADED
    assert response.embedding_dimension == 512
    assert len(factory.sources) == 1
    assert isinstance(factory.sources[0], bytes)
    with Image.open(BytesIO(factory.sources[0])) as image:
        assert image.mode == "RGB"


def test_warmup_failed_response_is_safe_and_retryable(tmp_path):
    factory = RecordingEmbeddingFactory(fail=True)
    service = OpenCLIPWarmupService(
        make_settings(tmp_path),
        model_manager=FakeModelManager(),
        embedding_service_factory=factory,
        executor=ImmediateExecutor(),
    )

    failed = service.start()
    retry = service.start()

    assert failed.state == WARMUP_FAILED
    assert failed.error == SAFE_WARMUP_FAILURE
    assert "C:/secret/cache" not in failed.message
    assert retry.state == WARMUP_FAILED
    assert len(factory.sources) == 2


class FakeWarmupRouteService:
    def __init__(self, state: str) -> None:
        self.state = state
        self.starts = 0

    def start(self) -> OpenCLIPWarmupSnapshot:
        self.starts += 1
        return self.snapshot()

    def status(self) -> OpenCLIPWarmupSnapshot:
        return self.snapshot()

    def snapshot(self) -> OpenCLIPWarmupSnapshot:
        return OpenCLIPWarmupSnapshot(
            state=self.state,
            message="OpenCLIP is ready." if self.state == WARMUP_LOADED else "OpenCLIP is loading.",
            model_name="ViT-B-32",
            pretrained="laion2b_s34b_b79k",
            device="cpu",
            embedding_dimension=512 if self.state == WARMUP_LOADED else None,
            error=SAFE_WARMUP_FAILURE if self.state == WARMUP_FAILED else None,
        )


@contextmanager
def route_client(tmp_path, warmup_service: FakeWarmupRouteService):
    settings = make_settings(tmp_path, cors_origins="http://testserver")
    database = mongomock.MongoClient()["museum_guide_test"]
    app = create_app(settings=settings, database=database)
    app.state.openclip_warmup_service = warmup_service
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
        yield client, database


def auth_headers(client: TestClient) -> dict[str, str]:
    response = client.post("/api/v1/auth/login", json={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD})
    assert response.status_code == 200
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def test_warmup_endpoint_requires_admin(tmp_path):
    service = FakeWarmupRouteService(WARMUP_LOADING)
    with route_client(tmp_path, service) as (client, _):
        response = client.post("/api/v1/ai/warmup")
        assert response.status_code == 401
        assert service.starts == 0


def test_warmup_route_returns_202_for_loading_and_status_for_loaded(tmp_path):
    service = FakeWarmupRouteService(WARMUP_LOADING)
    with route_client(tmp_path, service) as (client, _):
        headers = auth_headers(client)
        response = client.post("/api/v1/ai/warmup", headers=headers)
        status_response = client.get("/api/v1/ai/warmup/status", headers=headers)

        assert response.status_code == 202
        assert response.json()["state"] == WARMUP_LOADING
        assert status_response.status_code == 200
        assert status_response.json()["state"] == WARMUP_LOADING


def test_index_all_after_loaded_warmup(tmp_path, monkeypatch):
    service = FakeWarmupRouteService(WARMUP_LOADED)
    with route_client(tmp_path, service) as (client, database):
        artifact_repository.create_artifact(
            database,
            {
                "artifact_code": "ART-1",
                "name": "Jar",
                "description": "Clay jar",
                "category": "Ceramics",
                "image_paths": ["uploads/images/jar.jpg"],
                "created_by": "admin",
            },
        )

        class FakeIndexingService:
            def index_all(self, db):
                assert db is database
                return {
                    "total_artifacts": 1,
                    "total_images": 1,
                    "indexed_images": 1,
                    "failed_images": 0,
                    "skipped_images": 0,
                    "duration": 0.001,
                    "errors": [],
                }

        monkeypatch.setattr("app.routes.ai.ArtifactIndexingService.from_settings", lambda _settings: FakeIndexingService())

        response = client.post("/api/v1/ai/index/all", headers=auth_headers(client))

        assert response.status_code == 200
        assert response.json()["indexed_images"] == 1


def test_rebuild_endpoint_deletes_collection_before_indexing(tmp_path, monkeypatch):
    service = FakeWarmupRouteService(WARMUP_LOADED)
    with route_client(tmp_path, service) as (client, database):
        calls: list[str] = []

        class FakeQdrantManager:
            def delete_collection_if_exists(self):
                calls.append("delete")
                return True

        class FakeIndexingService:
            def index_all(self, db):
                assert db is database
                calls.append("index")
                return {
                    "total_artifacts": 0,
                    "total_images": 0,
                    "indexed_images": 0,
                    "failed_images": 0,
                    "skipped_images": 0,
                    "duration": 0.001,
                    "errors": [],
                }

        monkeypatch.setattr("app.routes.ai.qdrant_vectors.get_qdrant_manager", lambda _settings: FakeQdrantManager())
        monkeypatch.setattr("app.routes.ai.ArtifactIndexingService.from_settings", lambda _settings: FakeIndexingService())

        response = client.post("/api/v1/ai/index/rebuild", headers=auth_headers(client))

        assert response.status_code == 200
        assert calls == ["delete", "index"]


def test_bcrypt_existing_hashes_and_malformed_values():
    password = "ExistingPassword123!"
    legacy_2a = bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(prefix=b"2a")).decode("utf-8")
    current = hash_password(password)
    legacy_2y = current.replace("$2b$", "$2y$", 1)

    assert verify_password(password, legacy_2a) is True
    assert verify_password(password, current) is True
    assert verify_password(password, legacy_2y) is True
    assert verify_password("wrong-password", current) is False
    assert verify_password(password, "not-a-bcrypt-hash") is False
