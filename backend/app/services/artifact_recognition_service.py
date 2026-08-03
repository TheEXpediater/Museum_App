from __future__ import annotations

import logging
from dataclasses import dataclass
from io import BytesIO
from typing import Any

from PIL import Image, UnidentifiedImageError
from pymongo.database import Database

from app.ai.embedding_service import EmbeddingError, OpenCLIPEmbeddingService
from app.ai.model_manager import AIModelError
from app.config import Settings
from app.repositories import artifact_repository
from app.schemas.ai import ArtifactMatchResponse, RecognizedArtifact, RecognitionResponse
from app.services.image_storage import ALLOWED_MIME_TYPES, FORMAT_TO_EXTENSION, FORMAT_TO_MIME_TYPES, image_url_for_path
from app.utils import to_object_id
from app.vector.artifact_vector_repository import ArtifactVectorRepository
from app.vector.qdrant_manager import (
    CollectionCompatibilityError,
    QdrantManager,
    QdrantSetupError,
    get_qdrant_manager,
)


logger = logging.getLogger(__name__)

NO_MATCH_MESSAGE = "No reliable artifact match was found."
AI_UNAVAILABLE_MESSAGE = "AI recognition is temporarily unavailable."


class RecognitionInputError(ValueError):
    def __init__(self, detail: str, status_code: int) -> None:
        super().__init__(detail)
        self.detail = detail
        self.status_code = status_code


class RecognitionUnavailableError(RuntimeError):
    pass


@dataclass(frozen=True)
class VectorHit:
    point_id: str | None
    artifact_id: str
    score: float
    matched_image_path: str | None
    payload: dict[str, Any]


@dataclass
class ArtifactHitGroup:
    artifact_id: str
    hits: list[VectorHit]

    @property
    def best_hit(self) -> VectorHit:
        return max(self.hits, key=lambda hit: hit.score)


@dataclass(frozen=True)
class ArtifactMatchCandidate:
    raw_score: float
    response: ArtifactMatchResponse


class ArtifactRecognitionService:
    def __init__(
        self,
        settings: Settings,
        *,
        embedding_service: OpenCLIPEmbeddingService | None = None,
        qdrant_manager: QdrantManager | None = None,
        vector_repository: ArtifactVectorRepository | None = None,
    ) -> None:
        self.settings = settings
        self.embedding_service = embedding_service or OpenCLIPEmbeddingService(settings)
        self.qdrant_manager = qdrant_manager or get_qdrant_manager(settings)
        self.vector_repository = vector_repository or ArtifactVectorRepository(self.qdrant_manager)

    @classmethod
    def from_settings(cls, settings: Settings) -> "ArtifactRecognitionService":
        return cls(settings)

    def recognize(
        self,
        database: Database,
        *,
        image_bytes: bytes,
        content_type: str | None,
        base_url: str,
        limit: int | None = None,
    ) -> RecognitionResponse:
        if not self.settings.ai_enabled:
            raise RecognitionUnavailableError("AI recognition is disabled.")

        requested_limit = self._safe_limit(limit)
        self._validate_image_bytes(image_bytes, content_type)
        try:
            embedding = self.embedding_service.embed_image(image_bytes)
            collection_status = self.qdrant_manager.get_collection_status(expected_vector_size=embedding.dimension)
            if not collection_status.exists or (collection_status.points_count or 0) == 0:
                return self.no_match("No indexed artifact images are available yet.")
            if not collection_status.ready:
                raise RecognitionUnavailableError(collection_status.message or AI_UNAVAILABLE_MESSAGE)
            candidate_limit = max(requested_limit, self.settings.ai_recognition_vector_candidates)
            raw_hits = self.vector_repository.search_vectors(embedding.vector, limit=candidate_limit)
        except (AIModelError, EmbeddingError, CollectionCompatibilityError, QdrantSetupError) as exc:
            logger.exception("AI recognition dependency failure")
            raise RecognitionUnavailableError(AI_UNAVAILABLE_MESSAGE) from exc
        except Exception as exc:
            logger.exception("Unexpected AI recognition failure")
            raise RecognitionUnavailableError(AI_UNAVAILABLE_MESSAGE) from exc

        matches = self._matches_from_hits(database, raw_hits, base_url=base_url)
        accepted = [match for match in matches if match.raw_score >= self.settings.ai_recognition_possible_threshold]
        if not accepted:
            return self.no_match()

        best = accepted[0]
        return RecognitionResponse(
            matched=True,
            match_level=self.match_level(best.raw_score),
            best_match=best.response,
            other_matches=[match.response for match in accepted[1:requested_limit]],
            message="Artifact matches are ranked by visual similarity.",
        )

    def no_match(self, message: str = NO_MATCH_MESSAGE) -> RecognitionResponse:
        return RecognitionResponse(
            matched=False,
            match_level="no_match",
            best_match=None,
            other_matches=[],
            message=message,
        )

    def match_level(self, score: float) -> str:
        if score >= self.settings.ai_recognition_strong_threshold:
            return "strong"
        if score >= self.settings.ai_recognition_possible_threshold:
            return "possible"
        return "weak"

    def _matches_from_hits(self, database: Database, raw_hits: Any, *, base_url: str) -> list[ArtifactMatchCandidate]:
        groups: dict[str, ArtifactHitGroup] = {}
        for hit in self._parse_hits(raw_hits):
            if not hit.artifact_id:
                continue
            groups.setdefault(hit.artifact_id, ArtifactHitGroup(artifact_id=hit.artifact_id, hits=[])).hits.append(hit)

        matches: list[ArtifactMatchCandidate] = []
        for group in sorted(groups.values(), key=lambda item: item.best_hit.score, reverse=True):
            artifact_object_id = to_object_id(group.artifact_id)
            artifact = artifact_repository.get_artifact(database, artifact_object_id) if artifact_object_id else None
            if artifact is None:
                self._drop_stale_hits(group)
                continue
            matches.append(self._match_response(artifact, group, base_url))
        return matches

    def _match_response(self, artifact: dict, group: ArtifactHitGroup, base_url: str) -> ArtifactMatchCandidate:
        best_hit = group.best_hit
        return ArtifactMatchCandidate(
            raw_score=best_hit.score,
            response=ArtifactMatchResponse(
                artifact=RecognizedArtifact(
                    id=str(artifact["_id"]),
                    artifact_code=artifact["artifact_code"],
                    name=artifact["name"],
                    description=artifact["description"],
                    category=artifact["category"],
                    origin=artifact.get("origin"),
                    historical_period=artifact.get("historical_period"),
                    material=artifact.get("material"),
                    dimensions=artifact.get("dimensions"),
                    condition=artifact.get("condition"),
                    primary_image_url=image_url_for_path(base_url, artifact.get("primary_image_path")),
                ),
                similarity_score=round(best_hit.score, 4),
                matched_image_path=best_hit.matched_image_path,
                supporting_image_hits=len(group.hits),
            ),
        )

    def _drop_stale_hits(self, group: ArtifactHitGroup) -> None:
        for hit in group.hits:
            if not hit.point_id:
                continue
            try:
                self.vector_repository.delete_point(hit.point_id)
            except Exception:
                logger.exception("Failed to delete stale vector point %s for artifact %s", hit.point_id, group.artifact_id)

    def _parse_hits(self, raw_hits: Any) -> list[VectorHit]:
        if raw_hits is None:
            return []
        candidate = raw_hits
        for attr in ("points", "result"):
            value = getattr(candidate, attr, None)
            if value is not None:
                candidate = value
                break
        if isinstance(candidate, dict):
            candidate = candidate.get("points") or candidate.get("result") or []
        if not isinstance(candidate, list):
            return []

        parsed: list[VectorHit] = []
        for hit in candidate:
            payload = self._hit_value(hit, "payload") or {}
            if not isinstance(payload, dict):
                continue
            artifact_id = str(payload.get("artifact_id") or "")
            score_value = self._hit_value(hit, "score")
            try:
                score = float(score_value)
            except (TypeError, ValueError):
                continue
            point_id = self._hit_value(hit, "id")
            parsed.append(
                VectorHit(
                    point_id=str(point_id) if point_id is not None else None,
                    artifact_id=artifact_id,
                    score=score,
                    matched_image_path=payload.get("image_path"),
                    payload=payload,
                )
            )
        return parsed

    def _hit_value(self, hit: Any, key: str) -> Any:
        if isinstance(hit, dict):
            return hit.get(key)
        return getattr(hit, key, None)

    def _validate_image_bytes(self, image_bytes: bytes, content_type: str | None) -> None:
        if content_type not in ALLOWED_MIME_TYPES:
            raise RecognitionInputError("Only JPEG, PNG, and WEBP images can be recognized.", 415)
        max_bytes = self.settings.max_image_size_mb * 1024 * 1024
        if not image_bytes:
            raise RecognitionInputError("Uploaded image is empty.", 422)
        if len(image_bytes) > max_bytes:
            raise RecognitionInputError(f"Image exceeds the {self.settings.max_image_size_mb} MB size limit.", 413)
        try:
            with Image.open(BytesIO(image_bytes)) as image:
                image.verify()
                image_format = image.format
        except (UnidentifiedImageError, OSError) as exc:
            raise RecognitionInputError("Uploaded file is not a valid image.", 415) from exc
        if image_format not in FORMAT_TO_EXTENSION or content_type not in FORMAT_TO_MIME_TYPES.get(image_format or "", set()):
            raise RecognitionInputError("Image content does not match an allowed image type.", 415)

    def _safe_limit(self, limit: int | None) -> int:
        configured = self.settings.ai_recognition_max_results
        if limit is None:
            return configured
        return min(max(limit, 1), min(configured, 20))
