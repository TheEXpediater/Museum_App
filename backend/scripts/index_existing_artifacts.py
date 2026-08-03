from __future__ import annotations

import argparse
from typing import Any

from pydantic import ValidationError

from app.ai.model_manager import dependencies_available as openclip_dependencies_available
from app.config import Settings, get_settings
from app.database.mongodb import MongoConnectionError, mongo_manager
from app.services.artifact_indexing_service import ArtifactIndexingService
from app.utils import to_object_id
from app.vector.qdrant_manager import QdrantSetupError, dependency_available as qdrant_dependency_available, get_qdrant_manager


class Reporter:
    def __init__(self) -> None:
        self.failures = 0

    def ok(self, message: str) -> None:
        print(f"[OK] {message}")

    def info(self, message: str) -> None:
        print(f"[INFO] {message}")

    def warn(self, message: str) -> None:
        print(f"[WARN] {message}")

    def fail(self, message: str) -> None:
        self.failures += 1
        print(f"[FAIL] {message}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Index existing artifact images into Qdrant.")
    parser.add_argument("--artifact-id", help="Index a single MongoDB artifact ID.")
    parser.add_argument("--rebuild", action="store_true", help="Recreate only the configured Qdrant artifact collection first.")
    parser.add_argument("--dry-run", action="store_true", help="Validate artifacts and image paths without writing vectors or statuses.")
    parser.add_argument("--force", action="store_true", help="Skip rebuild confirmation.")
    return parser.parse_args()


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
    return settings


def verify_ai(settings: Settings, reporter: Reporter) -> bool:
    if not settings.ai_enabled:
        reporter.warn("AI is disabled. No indexing was performed.")
        return False
    if not openclip_dependencies_available():
        reporter.fail("PyTorch or OpenCLIP is not installed. Run `python start_backend.py --setup-ai` first.")
        return False
    reporter.ok("OpenCLIP dependencies available")
    if not qdrant_dependency_available():
        reporter.fail("qdrant-client is not installed. Run `python start_backend.py --setup-ai` first.")
        return False
    try:
        get_qdrant_manager(settings).ping()
        reporter.ok("Qdrant connected")
    except QdrantSetupError as exc:
        reporter.fail(str(exc))
        return False
    return True


def confirm_rebuild(settings: Settings, force: bool) -> bool:
    if force:
        return True
    print(
        "This will delete and recreate only the configured Qdrant collection "
        f"`{settings.qdrant_collection}`. MongoDB records and artifact images will not be deleted."
    )
    confirmation = input("Type REBUILD to continue: ").strip()
    return confirmation == "REBUILD"


def maybe_rebuild_collection(settings: Settings, args: argparse.Namespace, reporter: Reporter) -> bool:
    if not args.rebuild:
        return True
    if args.dry_run:
        reporter.info(f"Dry run: collection `{settings.qdrant_collection}` would be rebuilt.")
        return True
    if not confirm_rebuild(settings, args.force):
        reporter.fail("Collection rebuild was not confirmed.")
        return False
    try:
        deleted = get_qdrant_manager(settings).delete_collection_if_exists()
        reporter.ok(
            f"Collection `{settings.qdrant_collection}` deleted."
            if deleted
            else f"Collection `{settings.qdrant_collection}` did not exist."
        )
        return True
    except QdrantSetupError as exc:
        reporter.fail(str(exc))
        return False


def print_totals(result: dict[str, Any], reporter: Reporter) -> None:
    reporter.info(f"Total artifacts: {result['total_artifacts']}")
    reporter.info(f"Total images: {result['total_images']}")
    reporter.info(f"Indexed images: {result['indexed_images']}")
    reporter.info(f"Failed images: {result['failed_images']}")
    reporter.info(f"Skipped images: {result['skipped_images']}")
    reporter.info(f"Duration seconds: {result['duration']}")
    for error in result["errors"]:
        reporter.fail(error)


def main() -> int:
    args = parse_args()
    reporter = Reporter()
    settings = load_settings(reporter)
    if settings is None:
        return 1
    if not verify_ai(settings, reporter):
        return 1 if reporter.failures else 0
    if not maybe_rebuild_collection(settings, args, reporter):
        return 1

    artifact_id = None
    if args.artifact_id:
        artifact_id = to_object_id(args.artifact_id)
        if artifact_id is None:
            reporter.fail("Artifact ID is invalid.")
            return 1

    try:
        database = mongo_manager.connect(settings)
        reporter.ok("MongoDB connected")
        service = ArtifactIndexingService.from_settings(settings)
        result = service.index_all(database, artifact_id=artifact_id, dry_run=args.dry_run)
        print_totals(result, reporter)
    except MongoConnectionError as exc:
        reporter.fail(str(exc))
    finally:
        mongo_manager.close()

    return 1 if reporter.failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
