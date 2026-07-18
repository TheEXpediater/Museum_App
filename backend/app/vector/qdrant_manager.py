from __future__ import annotations

import importlib.util
import threading
from dataclasses import dataclass
from typing import Any

from app.config import Settings


class QdrantSetupError(RuntimeError):
    pass


class QdrantUnavailableError(QdrantSetupError):
    pass


class CollectionCompatibilityError(QdrantSetupError):
    pass


@dataclass(frozen=True)
class QdrantCollectionStatus:
    exists: bool
    ready: bool
    status: str
    vector_size: int | None = None
    distance: str | None = None
    points_count: int | None = None
    message: str | None = None


def dependency_available() -> bool:
    return importlib.util.find_spec("qdrant_client") is not None


class QdrantManager:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self._client: Any | None = None
        self._lock = threading.Lock()

    @property
    def client(self) -> Any:
        if self._client is None:
            with self._lock:
                if self._client is None:
                    self._client = self._create_client()
        return self._client

    def _create_client(self) -> Any:
        try:
            from qdrant_client import QdrantClient
        except ImportError as exc:
            raise QdrantSetupError(
                "qdrant-client is not installed. Run `python start_backend.py --setup-ai` first."
            ) from exc

        try:
            return QdrantClient(
                url=self.settings.qdrant_url,
                api_key=self.settings.qdrant_api_key,
                timeout=5,
                prefer_grpc=False,
            )
        except Exception as exc:
            raise QdrantUnavailableError("Could not create the Qdrant client.") from exc

    def ping(self) -> None:
        try:
            self.client.get_collections()
        except QdrantSetupError:
            raise
        except Exception as exc:
            raise QdrantUnavailableError("Qdrant is unavailable.") from exc

    def collection_exists(self) -> bool:
        try:
            exists = getattr(self.client, "collection_exists", None)
            if callable(exists):
                return bool(exists(collection_name=self.settings.qdrant_collection))
            self.client.get_collection(self.settings.qdrant_collection)
            return True
        except Exception:
            return False

    def ensure_collection(self, vector_size: int) -> QdrantCollectionStatus:
        if vector_size <= 0:
            raise CollectionCompatibilityError("Embedding dimension must be greater than zero.")

        self.ping()
        if not self.collection_exists():
            self._create_collection(vector_size)
            return QdrantCollectionStatus(
                exists=True,
                ready=True,
                status="created",
                vector_size=vector_size,
                distance=self.settings.qdrant_distance,
                points_count=self.count_vectors(),
            )

        status = self.get_collection_status(expected_vector_size=vector_size)
        if not status.ready:
            raise CollectionCompatibilityError(status.message or "Qdrant collection is incompatible.")
        return status

    def get_collection_status(self, expected_vector_size: int | None = None) -> QdrantCollectionStatus:
        self.ping()
        if not self.collection_exists():
            return QdrantCollectionStatus(exists=False, ready=False, status="missing", message="Collection does not exist.")

        try:
            collection = self.client.get_collection(self.settings.qdrant_collection)
        except Exception as exc:
            raise QdrantUnavailableError("Could not read Qdrant collection information.") from exc

        vector_size, distance = self._collection_vector_config(collection)
        distance = distance.lower() if distance else None
        expected_distance = self.settings.qdrant_distance.lower()
        if expected_vector_size is not None and vector_size != expected_vector_size:
            return QdrantCollectionStatus(
                exists=True,
                ready=False,
                status="incompatible",
                vector_size=vector_size,
                distance=distance,
                points_count=self.count_vectors(),
                message=(
                    f"Collection vector size is {vector_size}, but OpenCLIP produced {expected_vector_size}. "
                    "Changing the OpenCLIP model requires rebuilding the Qdrant collection."
                ),
            )
        if distance is not None and distance != expected_distance:
            return QdrantCollectionStatus(
                exists=True,
                ready=False,
                status="incompatible",
                vector_size=vector_size,
                distance=distance,
                points_count=self.count_vectors(),
                message=f"Collection distance is {distance}, but {expected_distance} is configured.",
            )

        return QdrantCollectionStatus(
            exists=True,
            ready=True,
            status="ready",
            vector_size=vector_size,
            distance=distance,
            points_count=self.count_vectors(),
        )

    def count_vectors(self) -> int:
        try:
            result = self.client.count(collection_name=self.settings.qdrant_collection, exact=True)
            return int(getattr(result, "count", 0))
        except Exception:
            return 0

    def _create_collection(self, vector_size: int) -> None:
        try:
            from qdrant_client import models
        except ImportError as exc:
            raise QdrantSetupError("qdrant-client models are unavailable.") from exc

        distances = {
            "cosine": models.Distance.COSINE,
            "dot": models.Distance.DOT,
            "euclid": models.Distance.EUCLID,
        }
        try:
            self.client.create_collection(
                collection_name=self.settings.qdrant_collection,
                vectors_config=models.VectorParams(
                    size=vector_size,
                    distance=distances[self.settings.qdrant_distance],
                ),
            )
        except Exception as exc:
            raise QdrantUnavailableError("Could not create the Qdrant collection.") from exc

    def _collection_vector_config(self, collection: Any) -> tuple[int | None, str | None]:
        config = self._value(collection, "config")
        params = self._value(config, "params")
        vectors = self._value(params, "vectors")
        if isinstance(vectors, dict) and "size" not in vectors:
            vectors = next(iter(vectors.values()), None)

        size = self._value(vectors, "size")
        distance = self._value(vectors, "distance")
        if hasattr(distance, "value"):
            distance = distance.value
        if distance is not None:
            distance = str(distance).rsplit(".", 1)[-1]
        return int(size) if size is not None else None, str(distance).lower() if distance else None

    def _value(self, source: Any, key: str) -> Any:
        if source is None:
            return None
        if isinstance(source, dict):
            return source.get(key)
        return getattr(source, key, None)


_manager_cache: dict[tuple[str, str | None, str], QdrantManager] = {}
_manager_cache_lock = threading.Lock()


def get_qdrant_manager(settings: Settings) -> QdrantManager:
    key = (settings.qdrant_url, settings.qdrant_api_key, settings.qdrant_collection)
    with _manager_cache_lock:
        manager = _manager_cache.get(key)
        if manager is None:
            manager = QdrantManager(settings)
            _manager_cache[key] = manager
        return manager
