from __future__ import annotations

from types import SimpleNamespace

import mongomock
from fastapi.testclient import TestClient

from app.config import Settings
from app.services.openclip_warmup_service import OpenCLIPWarmupSnapshot
from app.utils import utc_now
from app.vector.qdrant_manager import QdrantUnavailableError
from main import create_app


JWT_SECRET = "test-secret-key-that-is-long-enough"


def make_client(settings: Settings, *, warmup_service=None):
    database = mongomock.MongoClient()["museum_guide_test"]
    database.users.insert_one(
        {
            "email": "admin@example.com",
            "full_name": "Admin",
            "password_hash": "unused",
            "role": "admin",
            "is_active": True,
            "created_at": utc_now(),
            "updated_at": utc_now(),
        }
    )
    app = create_app(settings=settings, database=database)
    if warmup_service is not None:
        app.state.openclip_warmup_service = warmup_service
    return TestClient(app)


def make_settings(**overrides) -> Settings:
    values = {
        "mongodb_url": "mongodb://localhost:27017",
        "mongodb_database": "museum_guide_test",
        "jwt_secret_key": JWT_SECRET,
        "qdrant_api_key": "secret-api-key",
        "_env_file": None,
    }
    values.update(overrides)
    return Settings(**values)


class FakeModelManager:
    def __init__(self, *, loaded=True):
        self.is_loaded = loaded
        self.actual_device = "cpu" if loaded else None
        self.embedding_dimension = 512 if loaded else None


class FakeQdrantManager:
    def __init__(self, *, unavailable=False, collection_status=None):
        self.unavailable = unavailable
        self.collection_status = collection_status or SimpleNamespace(
            status="ready",
            ready=True,
            points_count=0,
            vector_size=512,
            distance="cosine",
            message=None,
        )

    def ping(self):
        if self.unavailable:
            raise QdrantUnavailableError("Qdrant is unavailable.")

    def get_collection_status(self, expected_vector_size=None):
        return self.collection_status


class FakeWarmupService:
    def __init__(self, state: str):
        self.state = state

    def status(self):
        return OpenCLIPWarmupSnapshot(
            state=self.state,
            message="status",
            model_name="ViT-B-32",
            pretrained="laion2b_s34b_b79k",
            device="cpu" if self.state == "loaded" else "auto",
            embedding_dimension=512 if self.state == "loaded" else None,
            error="safe failure" if self.state == "failed" else None,
        )


def patch_ai(monkeypatch, qdrant_manager, *, model_loaded=True):
    monkeypatch.setattr("app.routes.ai.openclip_models.dependencies_available", lambda: True)
    monkeypatch.setattr("app.routes.ai.openclip_models.get_model_manager", lambda _settings: FakeModelManager(loaded=model_loaded))
    monkeypatch.setattr("app.routes.ai.qdrant_vectors.dependency_available", lambda: True)
    monkeypatch.setattr("app.routes.ai.qdrant_vectors.get_qdrant_manager", lambda _settings: qdrant_manager)


def test_ai_disabled():
    with make_client(make_settings(ai_enabled=False)) as client:
        body = client.get("/api/v1/ai/health").json()
    assert body["status"] == "disabled"
    assert body["ai_enabled"] is False


def test_healthy_ai_state(monkeypatch):
    patch_ai(monkeypatch, FakeQdrantManager())
    with make_client(make_settings()) as client:
        body = client.get("/api/v1/ai/health").json()
    assert body["status"] == "healthy"
    assert body["openclip"] == "loaded"
    assert body["qdrant"] == "connected"
    assert body["collection_status"] == "ready"


def test_ai_health_maps_idle_loading_and_failed(monkeypatch):
    for state, expected_status in [("idle", "healthy"), ("loading", "healthy"), ("failed", "degraded")]:
        patch_ai(monkeypatch, FakeQdrantManager(), model_loaded=False)
        with make_client(make_settings(), warmup_service=FakeWarmupService(state)) as client:
            body = client.get("/api/v1/ai/health").json()
        assert body["status"] == expected_status
        assert body["openclip"] == state


def test_ai_health_idle_uses_collection_vector_size_without_model_dimension(monkeypatch):
    patch_ai(monkeypatch, FakeQdrantManager(), model_loaded=False)
    with make_client(make_settings(), warmup_service=FakeWarmupService("idle")) as client:
        body = client.get("/api/v1/ai/health").json()
    assert body["openclip"] == "idle"
    assert body["embedding_dimension"] is None
    assert body["collection_vector_size"] == 512


def test_qdrant_unavailable(monkeypatch):
    patch_ai(monkeypatch, FakeQdrantManager(unavailable=True))
    with make_client(make_settings()) as client:
        body = client.get("/api/v1/ai/health").json()
    assert body["status"] == "degraded"
    assert body["qdrant"] == "unavailable"


def test_openclip_unavailable(monkeypatch):
    monkeypatch.setattr("app.routes.ai.openclip_models.dependencies_available", lambda: False)
    monkeypatch.setattr("app.routes.ai.qdrant_vectors.dependency_available", lambda: False)
    with make_client(make_settings()) as client:
        body = client.get("/api/v1/ai/health").json()
    assert body["status"] == "degraded"
    assert body["openclip"] == "not_installed"


def test_collection_incompatible(monkeypatch):
    patch_ai(
        monkeypatch,
        FakeQdrantManager(
            collection_status=SimpleNamespace(
                status="incompatible",
                ready=False,
                points_count=1,
                vector_size=256,
                distance="cosine",
                message="wrong size",
            )
        ),
    )
    with make_client(make_settings()) as client:
        body = client.get("/api/v1/ai/health").json()
    assert body["status"] == "degraded"
    assert body["collection_status"] == "incompatible"


def test_no_secrets_in_ai_health(monkeypatch):
    patch_ai(monkeypatch, FakeQdrantManager())
    with make_client(make_settings()) as client:
        text = client.get("/api/v1/ai/health").text
    assert "secret-api-key" not in text
