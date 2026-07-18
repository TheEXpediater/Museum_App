from __future__ import annotations

import math
import tempfile
from pathlib import Path

from PIL import Image
from pydantic import ValidationError

from app.ai.embedding_service import EmbeddingError, OpenCLIPEmbeddingService
from app.ai.model_manager import AIModelError, dependencies_available, get_model_manager
from app.config import Settings, get_settings
from app.vector.qdrant_manager import (
    CollectionCompatibilityError,
    QdrantSetupError,
    dependency_available as qdrant_dependency_available,
    get_qdrant_manager,
)


class Reporter:
    def __init__(self) -> None:
        self.failures = 0

    def ok(self, message: str) -> None:
        print(f"[OK] {message}")

    def warn(self, message: str) -> None:
        print(f"[WARN] {message}")

    def fail(self, message: str) -> None:
        self.failures += 1
        print(f"[FAIL] {message}")


def create_test_image() -> Path:
    handle = tempfile.NamedTemporaryFile(delete=False, suffix=".jpg")
    handle.close()
    path = Path(handle.name)
    Image.new("RGB", (32, 32), color=(90, 130, 170)).save(path, format="JPEG")
    return path


def load_settings(reporter: Reporter) -> Settings | None:
    try:
        get_settings.cache_clear()
        settings = get_settings()
    except ValidationError as exc:
        reporter.fail(f"Environment configuration is invalid: {exc}")
        return None
    except Exception as exc:
        reporter.fail(f"Environment configuration could not be loaded: {exc}")
        return None

    reporter.ok("Environment configuration loaded")
    if settings.ai_enabled:
        reporter.ok("AI is enabled")
    else:
        reporter.warn("AI is disabled. No AI setup is required.")
    return settings


def check_embedding(settings: Settings, reporter: Reporter):
    if not dependencies_available():
        reporter.fail("PyTorch or OpenCLIP is not installed. Run `python start_backend.py --setup-ai`.")
        return None

    reporter.ok("PyTorch available")
    reporter.ok("OpenCLIP available")
    path = create_test_image()
    try:
        manager = get_model_manager(settings)
        service = OpenCLIPEmbeddingService(settings, model_manager=manager)
        result = service.embed_image(path)
        reporter.ok("OpenCLIP model loaded")
        reporter.ok(f"Device selected: {manager.actual_device.upper() if manager.actual_device else 'UNKNOWN'}")
        reporter.ok("Embedding generated")
        reporter.ok(f"Embedding dimension: {result.dimension}")
        if result.dimension <= 0:
            reporter.fail("Embedding dimension is invalid.")
        if not all(math.isfinite(value) for value in result.vector):
            reporter.fail("Embedding contains non-finite values.")
        magnitude = math.sqrt(sum(value * value for value in result.vector))
        if abs(magnitude - 1.0) > 1e-5:
            reporter.fail("Embedding is not normalized.")
        else:
            reporter.ok("Embedding normalized")
        return result
    except (AIModelError, EmbeddingError) as exc:
        reporter.fail(str(exc))
        return None
    finally:
        path.unlink(missing_ok=True)


def check_qdrant(settings: Settings, dimension: int, reporter: Reporter) -> None:
    if not qdrant_dependency_available():
        reporter.fail("qdrant-client is not installed. Run `python start_backend.py --setup-ai`.")
        return

    try:
        manager = get_qdrant_manager(settings)
        manager.ping()
        reporter.ok("Qdrant connected")
        status = manager.ensure_collection(dimension)
        reporter.ok("Collection ready")
        reporter.ok(f"Collection vector count: {status.points_count or 0}")
    except CollectionCompatibilityError as exc:
        reporter.fail(str(exc))
    except QdrantSetupError as exc:
        reporter.fail(str(exc))


def main() -> int:
    reporter = Reporter()
    settings = load_settings(reporter)
    if settings is None:
        return 1
    if not settings.ai_enabled:
        return 0

    embedding = check_embedding(settings, reporter)
    if embedding is not None:
        check_qdrant(settings, embedding.dimension, reporter)

    return 1 if reporter.failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
