from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath
from typing import Iterable

from bson import ObjectId
from pymongo.database import Database

from app.ai.embedding_service import EmbeddingError, OpenCLIPEmbeddingService
from app.ai.model_manager import AIModelError
from app.config import Settings
from app.repositories import artifact_repository
from app.utils import to_object_id
from app.vector.artifact_vector_repository import ArtifactImagePayload, ArtifactVectorRepository
from app.vector.qdrant_manager import (
    CollectionCompatibilityError,
    QdrantManager,
    QdrantSetupError,
    get_qdrant_manager,
)


logger = logging.getLogger(__name__)

SAFE_INDEXING_UNAVAILABLE = "AI indexing is temporarily unavailable. The artifact was saved and can be indexed later."
SAFE_IMAGE_INDEXING_FAILED = "One or more artifact images could not be indexed."
SAFE_PATH_REJECTED = "A stored image path could not be indexed safely."


@dataclass(frozen=True)
class ImageIndexFailure:
    image_path: str
    message: str


@dataclass
class ArtifactIndexingResult:
    artifact_id: str | None
    total_images: int = 0
    indexed_images: int = 0
    failed_images: int = 0
    skipped_images: int = 0
    deleted_vectors: int = 0
    failures: list[ImageIndexFailure] = field(default_factory=list)
    messages: list[str] = field(default_factory=list)
    duration: float = 0.0
    ai_index_status: str = "not_indexed"

    @property
    def errors(self) -> list[str]:
        seen: set[str] = set()
        errors: list[str] = []
        for failure in self.failures:
            if failure.message not in seen:
                errors.append(failure.message)
                seen.add(failure.message)
        return errors

    @property
    def safe_error(self) -> str | None:
        if self.ai_index_status not in {"failed", "partial"}:
            return None
        if not self.errors:
            return SAFE_IMAGE_INDEXING_FAILED
        return " ".join(self.errors[:2])


class ArtifactIndexingService:
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
    def from_settings(cls, settings: Settings) -> "ArtifactIndexingService":
        return cls(settings)

    def mark_pending(self, database: Database, artifact: dict) -> dict:
        artifact_id = self._object_id(artifact)
        if artifact_id is None:
            return artifact
        try:
            updated = artifact_repository.update_ai_index_state(
                database,
                artifact_id,
                status="pending",
                indexed_image_count=int(artifact.get("ai_indexed_image_count") or 0),
                error=None,
            )
            return updated or artifact
        except Exception:
            logger.exception("Failed to mark AI index status pending for artifact %s", artifact_id)
            return artifact

    def index_artifact(
        self,
        database: Database,
        artifact: dict,
        *,
        image_paths: Iterable[str] | None = None,
        update_status: bool = True,
        dry_run: bool = False,
    ) -> ArtifactIndexingResult:
        started = time.perf_counter()
        artifact_id = str(artifact.get("_id", ""))
        paths = list(image_paths if image_paths is not None else artifact.get("image_paths", []))
        result = ArtifactIndexingResult(artifact_id=artifact_id or None, total_images=len(paths))

        if not self.settings.ai_enabled:
            result.skipped_images = len(paths)
            result.messages.append("AI indexing is disabled.")
            result.ai_index_status = "not_indexed"
            result.duration = self._elapsed(started)
            self._store_status(database, artifact, result, update_status and not dry_run)
            return result

        if not paths:
            result.messages.append("Artifact has no images to index.")
            result.ai_index_status = "not_indexed"
            result.duration = self._elapsed(started)
            self._store_status(database, artifact, result, update_status and not dry_run)
            return result

        if update_status and not dry_run:
            artifact = self.mark_pending(database, artifact)
            self.delete_artifact_vectors(artifact_id)

        for image_path in paths:
            try:
                resolved = self.resolve_stored_image_path(image_path)
                if dry_run:
                    result.skipped_images += 1
                    continue
                embedding = self.embedding_service.embed_image(resolved)
                self.qdrant_manager.ensure_collection(embedding.dimension)
                self.vector_repository.upsert_image_vector(
                    embedding.vector,
                    ArtifactImagePayload(
                        artifact_id=artifact_id,
                        artifact_code=str(artifact.get("artifact_code", "")),
                        artifact_name=str(artifact.get("name", "")),
                        category=str(artifact.get("category", "")),
                        image_path=image_path,
                    ),
                )
                result.indexed_images += 1
            except (AIModelError, EmbeddingError, CollectionCompatibilityError, QdrantSetupError) as exc:
                logger.exception("AI indexing failed for artifact %s image %s", artifact_id, image_path)
                result.failed_images += 1
                result.failures.append(ImageIndexFailure(image_path=image_path, message=self._safe_failure_message(exc)))
            except ValueError:
                logger.warning(
                    "Rejected unsafe artifact image path during indexing for artifact %s: %s",
                    artifact_id,
                    image_path,
                    exc_info=True,
                )
                result.failed_images += 1
                result.failures.append(ImageIndexFailure(image_path=image_path, message=SAFE_PATH_REJECTED))
            except Exception as exc:
                logger.exception("Unexpected AI indexing failure for artifact %s image %s", artifact_id, image_path)
                result.failed_images += 1
                result.failures.append(ImageIndexFailure(image_path=image_path, message=SAFE_IMAGE_INDEXING_FAILED))

        result.ai_index_status = self._status_for_result(result)
        if result.indexed_images:
            result.messages.append(f"Indexed {result.indexed_images} artifact image(s).")
        if result.failed_images:
            result.messages.append(SAFE_IMAGE_INDEXING_FAILED)
        result.duration = self._elapsed(started)
        self._store_status(database, artifact, result, update_status and not dry_run)
        return result

    def synchronize_after_create(self, database: Database, artifact: dict) -> dict:
        return artifact

    def synchronize_after_update(self, database: Database, previous: dict, current: dict) -> dict:
        artifact_id = self._object_id(current)
        ai_relevant_changed = set(previous.get("image_paths") or []) != set(current.get("image_paths") or []) or any(
            previous.get(key) != current.get(key)
            for key in ("artifact_code", "name", "category")
        )
        if artifact_id is not None and previous.get("ai_index_status") == "indexed" and ai_relevant_changed:
            return artifact_repository.update_ai_index_state(
                database,
                artifact_id,
                status="stale",
                indexed_image_count=int(previous.get("ai_indexed_image_count") or 0),
                error=None,
            ) or current
        return current

    def delete_image_vectors(self, artifact_id: str, image_paths: Iterable[str]) -> None:
        if not self.settings.ai_enabled:
            return
        for image_path in image_paths:
            try:
                self.vector_repository.delete_image_vector(artifact_id, image_path)
            except QdrantSetupError:
                logger.exception("Qdrant image vector deletion failed for artifact %s image %s", artifact_id, image_path)
            except Exception:
                logger.exception("Unexpected image vector deletion failure for artifact %s image %s", artifact_id, image_path)

    def delete_artifact_vectors(self, artifact_id: str) -> bool:
        if not self.settings.ai_enabled:
            return False
        try:
            self.vector_repository.delete_artifact_vectors(artifact_id)
            return True
        except QdrantSetupError:
            logger.exception("Qdrant vector cleanup failed for deleted artifact %s", artifact_id)
        except Exception:
            logger.exception("Unexpected vector cleanup failure for deleted artifact %s", artifact_id)
        return False

    def index_all(self, database: Database, *, artifact_id: ObjectId | None = None, dry_run: bool = False) -> dict:
        started = time.perf_counter()
        artifacts = artifact_repository.list_all_artifacts(database, artifact_id=artifact_id)
        return self._index_artifacts(database, artifacts, started=started, dry_run=dry_run)

    def index_by_status(self, database: Database, statuses: list[str], *, dry_run: bool = False) -> dict:
        started = time.perf_counter()
        artifacts = artifact_repository.list_artifacts_by_ai_status(database, statuses)
        return self._index_artifacts(database, artifacts, started=started, dry_run=dry_run)

    def feed_pending_library(self, database: Database, *, dry_run: bool = False) -> dict:
        started = time.perf_counter()
        artifacts = artifact_repository.list_ai_library_pending_artifacts(database)
        return self._index_artifacts(database, artifacts, started=started, dry_run=dry_run)

    def _index_artifacts(self, database: Database, artifacts: list[dict], *, started: float, dry_run: bool) -> dict:
        totals = {
            "total_artifacts": len(artifacts),
            "total_images": 0,
            "indexed_images": 0,
            "failed_images": 0,
            "skipped_images": 0,
            "artifacts_processed": len(artifacts),
            "images_processed": 0,
            "successful_artifacts": 0,
            "failed_artifacts": 0,
            "duration": 0.0,
            "errors": [],
        }
        errors: list[str] = []
        for artifact in artifacts:
            try:
                result = self.index_artifact(database, artifact, dry_run=dry_run)
                totals["total_images"] += result.total_images
                totals["indexed_images"] += result.indexed_images
                totals["failed_images"] += result.failed_images
                totals["skipped_images"] += result.skipped_images
                totals["images_processed"] += result.total_images
                if result.ai_index_status == "indexed":
                    totals["successful_artifacts"] += 1
                elif result.ai_index_status == "failed" or result.failed_images:
                    totals["failed_artifacts"] += 1
                errors.extend(f"{result.artifact_id}: {error}" for error in result.errors)
            except Exception:
                artifact_label = str(artifact.get("_id", "unknown"))
                logger.exception("Unexpected failure while indexing artifact %s", artifact_label)
                totals["failed_artifacts"] += 1
                errors.append(f"{artifact_label}: {SAFE_INDEXING_UNAVAILABLE}")
        totals["duration"] = self._elapsed(started)
        totals["errors"] = errors
        return totals

    def resolve_stored_image_path(self, image_path: str) -> Path:
        pure_path = PurePosixPath(image_path)
        if pure_path.is_absolute() or any(part in {"", ".", ".."} for part in pure_path.parts):
            raise ValueError("Stored image path is unsafe.")
        filename = Path(pure_path.name).name
        if not filename or filename != pure_path.name:
            raise ValueError("Stored image path has an unsafe filename.")
        resolved = (self.settings.upload_path / filename).resolve()
        upload_root = self.settings.upload_path.resolve()
        try:
            resolved.relative_to(upload_root)
        except ValueError as exc:
            raise ValueError("Stored image path escapes the upload directory.") from exc
        if not resolved.is_file():
            raise ValueError("Stored image file does not exist.")
        return resolved

    def _store_status(
        self,
        database: Database,
        artifact: dict,
        result: ArtifactIndexingResult,
        update_status: bool,
    ) -> None:
        if not update_status:
            return
        artifact_id = self._object_id(artifact)
        if artifact_id is None:
            return
        try:
            artifact_repository.update_ai_index_state(
                database,
                artifact_id,
                status=result.ai_index_status,
                indexed_image_count=result.indexed_images,
                error=result.safe_error,
            )
        except Exception:
            logger.exception("Failed to persist AI index status for artifact %s", artifact_id)

    def _status_for_result(self, result: ArtifactIndexingResult) -> str:
        if result.total_images == 0:
            return "not_indexed"
        if result.indexed_images == result.total_images and result.failed_images == 0:
            return "indexed"
        if result.indexed_images > 0:
            return "partial"
        if result.failed_images > 0:
            return "failed"
        return "not_indexed"

    def _safe_failure_message(self, exc: Exception) -> str:
        if isinstance(exc, (AIModelError, EmbeddingError)):
            return SAFE_INDEXING_UNAVAILABLE
        if isinstance(exc, (CollectionCompatibilityError, QdrantSetupError)):
            return SAFE_INDEXING_UNAVAILABLE
        return SAFE_IMAGE_INDEXING_FAILED

    def _latest_artifact(self, database: Database, artifact: dict) -> dict | None:
        artifact_id = self._object_id(artifact)
        if artifact_id is None:
            return None
        return artifact_repository.get_artifact(database, artifact_id)

    def _object_id(self, artifact: dict) -> ObjectId | None:
        value = artifact.get("_id")
        if isinstance(value, ObjectId):
            return value
        return to_object_id(str(value)) if value else None

    def _elapsed(self, started: float) -> float:
        return round(time.perf_counter() - started, 3)
