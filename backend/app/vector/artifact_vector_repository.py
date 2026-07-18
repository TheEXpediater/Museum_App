from __future__ import annotations

from dataclasses import dataclass
from typing import Any
from uuid import UUID, uuid5

from app.vector.qdrant_manager import QdrantManager


POINT_NAMESPACE = UUID("6ca260a4-2e2e-4ca7-95de-3cbd40f8a0ab")


def point_id_for_image(artifact_id: str, image_path: str) -> str:
    """Use deterministic UUIDv5 IDs so the same artifact image can be upserted safely."""
    return str(uuid5(POINT_NAMESPACE, f"{artifact_id}:{image_path}"))


@dataclass(frozen=True)
class ArtifactImagePayload:
    artifact_id: str
    artifact_code: str
    artifact_name: str
    category: str
    image_path: str

    def as_qdrant_payload(self) -> dict[str, str]:
        return {
            "artifact_id": self.artifact_id,
            "artifact_code": self.artifact_code,
            "artifact_name": self.artifact_name,
            "category": self.category,
            "image_path": self.image_path,
        }


class ArtifactVectorRepository:
    def __init__(self, manager: QdrantManager) -> None:
        self.manager = manager

    @property
    def client(self):
        return self.manager.client

    @property
    def collection_name(self) -> str:
        return self.manager.settings.qdrant_collection

    def upsert_image_vector(self, vector: list[float], payload: ArtifactImagePayload) -> str:
        from qdrant_client import models

        point_id = point_id_for_image(payload.artifact_id, payload.image_path)
        self.client.upsert(
            collection_name=self.collection_name,
            points=[
                models.PointStruct(
                    id=point_id,
                    vector=vector,
                    payload=payload.as_qdrant_payload(),
                )
            ],
        )
        return point_id

    def delete_point(self, point_id: str) -> None:
        from qdrant_client import models

        self.client.delete(
            collection_name=self.collection_name,
            points_selector=models.PointIdsList(points=[point_id]),
        )

    def delete_artifact_vectors(self, artifact_id: str) -> None:
        from qdrant_client import models

        self.client.delete(
            collection_name=self.collection_name,
            points_selector=models.FilterSelector(
                filter=models.Filter(
                    must=[
                        models.FieldCondition(
                            key="artifact_id",
                            match=models.MatchValue(value=artifact_id),
                        )
                    ]
                )
            ),
        )

    def count_vectors(self) -> int:
        return self.manager.count_vectors()

    def search_vectors(self, vector: list[float], *, limit: int = 5) -> Any:
        query_points = getattr(self.client, "query_points", None)
        if callable(query_points):
            return query_points(collection_name=self.collection_name, query=vector, limit=limit)
        return self.client.search(collection_name=self.collection_name, query_vector=vector, limit=limit)

    def collection_info(self):
        return self.client.get_collection(self.collection_name)
