from __future__ import annotations

from pydantic import ValidationError
from pymongo.errors import PyMongoError

from app.config import Settings, get_settings
from app.database.mongodb import MongoConnectionError, mongo_manager


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


def load_settings(reporter: Reporter) -> Settings | None:
    try:
        get_settings.cache_clear()
        return get_settings()
    except ValidationError as exc:
        reporter.fail(f"Environment configuration is invalid: {exc}")
        return None
    except Exception as exc:
        reporter.fail(f"Environment configuration could not be loaded: {exc}")
        return None


def report_counts(settings: Settings, reporter: Reporter) -> None:
    try:
        database = mongo_manager.connect(settings)
        artifact_count = database.artifacts.count_documents({})
        user_count = database.users.count_documents({})
    except MongoConnectionError as exc:
        reporter.fail(str(exc))
        return
    except PyMongoError:
        reporter.fail("MongoDB query failed while counting artifacts/users.")
        return

    if artifact_count > 0:
        reporter.ok(f"Artifact records present: {artifact_count}")
    else:
        reporter.warn("No artifact records found (expected if this is a brand-new install with no restored data).")

    if user_count > 0:
        reporter.ok(f"User/admin records present: {user_count}")
    else:
        reporter.warn("No user records found (expected if this is a brand-new install with no restored data).")


def report_image_files(settings: Settings, reporter: Reporter) -> None:
    upload_dir = settings.upload_path
    if not upload_dir.exists():
        reporter.warn(f"Upload directory does not exist yet: {upload_dir}")
        return

    image_files = [
        path
        for path in upload_dir.iterdir()
        if path.is_file() and path.name != ".gitkeep"
    ]
    if image_files:
        reporter.ok(f"Restored/managed artifact image files present: {len(image_files)} in {upload_dir}")
    else:
        reporter.warn(f"No artifact image files found in {upload_dir} (expected if this is a brand-new install).")


def main() -> int:
    reporter = Reporter()
    settings = load_settings(reporter)
    if settings is None:
        return 1

    report_counts(settings, reporter)
    report_image_files(settings, reporter)
    mongo_manager.close()
    return 1 if reporter.failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
