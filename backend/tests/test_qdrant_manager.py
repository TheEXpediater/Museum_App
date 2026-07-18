from __future__ import annotations

from types import SimpleNamespace

import pytest

from app.config import Settings
from app.vector.qdrant_manager import CollectionCompatibilityError, QdrantManager, QdrantUnavailableError


JWT_SECRET = "test-secret-key-that-is-long-enough"


class FakeClient:
    def __init__(self, *, exists=True, size=512, distance="Cosine", fail_ping=False):
        self.exists = exists
        self.size = size
        self.distance = distance
        self.fail_ping = fail_ping
        self.created_size = None
        self.deleted = False

    def get_collections(self):
        if self.fail_ping:
            raise RuntimeError("offline")
        return []

    def collection_exists(self, collection_name):
        return self.exists

    def get_collection(self, collection_name):
        return {
            "config": {
                "params": {
                    "vectors": {
                        "size": self.size,
                        "distance": self.distance,
                    }
                }
            }
        }

    def count(self, collection_name, exact=True):
        return SimpleNamespace(count=3)

    def delete(self, *args, **kwargs):
        self.deleted = True


def make_manager(client: FakeClient) -> QdrantManager:
    settings = Settings(
        mongodb_url="mongodb://localhost:27017",
        mongodb_database="museum_guide_test",
        jwt_secret_key=JWT_SECRET,
        _env_file=None,
    )
    manager = QdrantManager(settings)
    manager._client = client
    return manager


def test_service_available():
    make_manager(FakeClient()).ping()


def test_service_unavailable():
    with pytest.raises(QdrantUnavailableError):
        make_manager(FakeClient(fail_ping=True)).ping()


def test_collection_missing():
    status = make_manager(FakeClient(exists=False)).get_collection_status()
    assert status.status == "missing"
    assert status.ready is False


def test_collection_creation(monkeypatch):
    client = FakeClient(exists=False)
    manager = make_manager(client)

    def fake_create(size):
        client.created_size = size

    monkeypatch.setattr(manager, "_create_collection", fake_create)
    status = manager.ensure_collection(512)
    assert status.status == "created"
    assert client.created_size == 512


def test_compatible_existing_dimension():
    status = make_manager(FakeClient(size=512, distance="Cosine")).ensure_collection(512)
    assert status.ready is True
    assert status.status == "ready"
    assert status.points_count == 3


def test_incompatible_existing_dimension():
    with pytest.raises(CollectionCompatibilityError):
        make_manager(FakeClient(size=256)).ensure_collection(512)


def test_existing_collection_is_not_deleted_automatically():
    client = FakeClient(size=256)
    with pytest.raises(CollectionCompatibilityError):
        make_manager(client).ensure_collection(512)
    assert client.deleted is False
