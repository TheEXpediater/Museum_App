from __future__ import annotations

import argparse
import hashlib
import json
import logging
import re
import zipfile
from dataclasses import asdict, dataclass
from pathlib import Path, PurePosixPath, PureWindowsPath
from typing import Any

from fastapi import HTTPException
from pydantic import ValidationError
from pymongo.errors import DuplicateKeyError, PyMongoError

from app.config import Settings, get_settings
from app.database.mongodb import MongoConnectionError, mongo_manager
from app.repositories import artifact_repository, category_repository
from app.services.artifact_indexing_service import ArtifactIndexingService
from app.services.artifact_validation import UNCATEGORIZED
from app.services.image_storage import cleanup_images, save_image_bytes, validate_image_bytes


logger = logging.getLogger(__name__)

ROOT_DIR = Path(__file__).resolve().parents[2]
DEFAULT_SOURCE = ROOT_DIR / "artifact_image_source"
DEFAULT_REPORT = ROOT_DIR / "artifact_import_report.json"

MAX_ZIP_ENTRIES = 1000
MAX_UNCOMPRESSED_BYTES = 250 * 1024 * 1024
SUPPORTED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}
ARCHIVE_EXTENSIONS = {".zip", ".rar", ".7z", ".tar", ".gz", ".bz2", ".xz"}
IGNORED_ENTRY_NAMES = {".ds_store", "thumbs.db"}
EXPLICIT_PRIMARY_NAMES = {
    "main.jpg",
    "main.jpeg",
    "main.png",
    "main.webp",
    "primary.jpg",
    "primary.jpeg",
    "primary.png",
    "primary.webp",
    "cover.jpg",
    "cover.jpeg",
    "cover.png",
    "cover.webp",
}
EXPLICIT_PRIMARY_PREFIXES = ("01_main", "01_primary", "01_cover")


@dataclass(frozen=True)
class CandidateZip:
    path: Path
    artifact_name: str
    category: str
    import_source_name: str


@dataclass
class ValidImage:
    entry_name: str
    display_name: str
    data: bytes
    extension: str
    digest: str


@dataclass
class ImportRow:
    zip_filename: str
    artifact_name: str
    created_artifact_id: str | None
    generated_artifact_code: str | None
    category: str
    image_count: int
    primary_image: str | None
    primary_image_selection_method: str | None
    draft_status: str | None
    import_result: str
    error: str | None = None


@dataclass
class ImportReport:
    found_zip_files: int = 0
    valid_zip_files: int = 0
    imported: int = 0
    skipped_existing: int = 0
    failed: int = 0
    images_imported: int = 0
    images_would_be_imported: int = 0
    largest_artifact_image_count: int = 0
    valid_images_discarded: bool = False
    draft_artifacts_created: int = 0
    primary_images_assigned_automatically: int = 0
    explicit_primary_images_detected: int = 0
    rows: list[ImportRow] | None = None

    def __post_init__(self) -> None:
        if self.rows is None:
            self.rows = []


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
    parser = argparse.ArgumentParser(description="Bulk import artifact ZIP files as draft artifacts.")
    parser.add_argument("--source", default=str(DEFAULT_SOURCE), help="Folder containing artifact ZIP files.")
    parser.add_argument("--dry-run", action="store_true", help="Report imports without changing MongoDB or image storage.")
    parser.add_argument("--skip-existing", action="store_true", help="Skip already imported ZIP files. This is the default behavior.")
    parser.add_argument("--update-existing", action="store_true", help="Replace images for matching imported artifacts.")
    parser.add_argument("--index-ai", action="store_true", help="Index imported artifact images after saving records.")
    parser.add_argument("--report-file", default=str(DEFAULT_REPORT), help="Write a JSON report to this path.")
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


def discover_zips(source: Path) -> list[CandidateZip]:
    if not source.exists():
        return []
    if not source.is_dir():
        raise ValueError(f"Import source is not a directory: {source}")

    candidates: list[CandidateZip] = []
    for path in sorted(source.glob("*.zip"), key=natural_path_key):
        candidates.append(candidate_for_zip(source, path))
    for child in sorted([item for item in source.iterdir() if item.is_dir()], key=lambda item: natural_path_key(item)):
        for path in sorted(child.glob("*.zip"), key=natural_path_key):
            candidates.append(candidate_for_zip(source, path))
    return candidates


def candidate_for_zip(source: Path, path: Path) -> CandidateZip:
    artifact_name = path.stem.strip()
    if not artifact_name:
        raise ValueError(f"ZIP filename does not contain an artifact name: {path.name}")
    category = path.parent.name.strip() if path.parent != source else UNCATEGORIZED
    if not category:
        category = UNCATEGORIZED
    return CandidateZip(
        path=path,
        artifact_name=artifact_name,
        category=category,
        import_source_name=path.relative_to(source).as_posix(),
    )


def zip_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def inspect_zip(candidate: CandidateZip, settings: Settings) -> tuple[list[ValidImage], ValidImage, str, bool]:
    valid_images: list[ValidImage] = []
    try:
        with zipfile.ZipFile(candidate.path) as archive:
            infos = archive.infolist()
            if len(infos) > MAX_ZIP_ENTRIES:
                raise ValueError(f"ZIP contains {len(infos)} entries; the limit is {MAX_ZIP_ENTRIES}.")

            total_size = sum(max(info.file_size, 0) for info in infos)
            if total_size > MAX_UNCOMPRESSED_BYTES:
                raise ValueError(
                    f"ZIP uncompressed size is {total_size} bytes; the limit is {MAX_UNCOMPRESSED_BYTES} bytes."
                )

            for info in infos:
                if info.is_dir():
                    continue
                if is_zip_symlink(info):
                    raise ValueError(f"ZIP symbolic links are not allowed: {info.filename}")
                entry_name = info.filename
                if is_ignored_entry(entry_name):
                    continue
                assert_safe_entry_name(entry_name)

                suffix = Path(entry_name).suffix.lower()
                if suffix in ARCHIVE_EXTENSIONS:
                    raise ValueError(f"Nested archive files are not allowed: {entry_name}")
                if suffix not in SUPPORTED_IMAGE_EXTENSIONS:
                    continue

                data = archive.read(info)
                try:
                    extension, digest = validate_image_bytes(data, settings)
                except HTTPException as exc:
                    raise ValueError(f"Invalid image `{entry_name}`: {exc.detail}") from exc
                valid_images.append(
                    ValidImage(
                        entry_name=entry_name,
                        display_name=Path(entry_name).name,
                        data=data,
                        extension=extension,
                        digest=digest,
                    )
                )
    except zipfile.BadZipFile as exc:
        raise ValueError("ZIP file is corrupt or unreadable.") from exc

    if not valid_images:
        raise ValueError("No valid image was found.")

    ordered_images = sorted(valid_images, key=lambda image: natural_string_key(image.entry_name))
    primary, method, needs_review = choose_primary_image(ordered_images)
    return ordered_images, primary, method, needs_review


def is_ignored_entry(entry_name: str) -> bool:
    normalized = entry_name.replace("\\", "/")
    parts = [part for part in normalized.split("/") if part]
    if not parts:
        return True
    if any(part == "__MACOSX" for part in parts):
        return True
    basename = parts[-1].lower()
    return basename in IGNORED_ENTRY_NAMES or basename.startswith("._") or basename.startswith(".")


def assert_safe_entry_name(entry_name: str) -> None:
    normalized = entry_name.replace("\\", "/")
    posix = PurePosixPath(normalized)
    windows = PureWindowsPath(entry_name)
    if posix.is_absolute() or windows.is_absolute() or windows.drive:
        raise ValueError(f"Unsafe ZIP entry path: {entry_name}")
    if any(part in {"", ".", ".."} for part in posix.parts):
        raise ValueError(f"Unsafe ZIP entry path: {entry_name}")


def is_zip_symlink(info: zipfile.ZipInfo) -> bool:
    unix_file_type = (info.external_attr >> 16) & 0o170000
    return unix_file_type == 0o120000


def choose_primary_image(images: list[ValidImage]) -> tuple[ValidImage, str, bool]:
    by_exact = [
        image
        for image in images
        if image.display_name.lower() in EXPLICIT_PRIMARY_NAMES
    ]
    if by_exact:
        return sorted(by_exact, key=lambda image: natural_string_key(image.entry_name))[0], "explicit_filename", False

    by_prefix = [
        image
        for image in images
        if any(Path(image.display_name).stem.lower().startswith(prefix) for prefix in EXPLICIT_PRIMARY_PREFIXES)
    ]
    if by_prefix:
        return sorted(by_prefix, key=lambda image: natural_string_key(image.entry_name))[0], "explicit_prefix", False

    return images[0], "deterministic_natural_sort", True


def generated_artifact_code(name: str, source_hash: str, database) -> str:
    slug = re.sub(r"[^A-Z0-9]+", "-", name.upper()).strip("-") or "ARTIFACT"
    slug = slug[:40].strip("-") or "ARTIFACT"
    base = f"DRAFT-{slug}-{source_hash[:6].upper()}"
    candidate = base
    suffix = 2
    while artifact_repository.find_by_code(database, candidate):
        candidate = f"{base}-{suffix}"
        suffix += 1
    return candidate


def import_candidate(candidate: CandidateZip, settings: Settings, database, args: argparse.Namespace) -> ImportRow:
    source_hash = zip_sha256(candidate.path)
    existing = (
        artifact_repository.find_by_import_source_hash(database, source_hash)
        or database.artifacts.find_one({"import_source_name": candidate.import_source_name})
    )

    if existing is not None and not args.update_existing and not args.dry_run:
        return ImportRow(
            zip_filename=candidate.import_source_name,
            artifact_name=existing.get("name") or candidate.artifact_name,
            created_artifact_id=str(existing.get("_id")),
            generated_artifact_code=existing.get("artifact_code"),
            category=existing.get("category") or candidate.category,
            image_count=len(existing.get("image_paths") or []),
            primary_image=existing.get("primary_image_path"),
            primary_image_selection_method=existing.get("primary_image_selection_method"),
            draft_status=existing.get("status") or "published",
            import_result="skipped_existing",
            error=None,
        )

    try:
        images, primary, method, needs_review = inspect_zip(candidate, settings)
    except (HTTPException, ValueError) as exc:
        logger.exception("Artifact ZIP validation failed for %s", candidate.import_source_name)
        detail = exc.detail if isinstance(exc, HTTPException) else str(exc)
        return failed_row(candidate, detail)

    if args.dry_run:
        if existing is not None and not args.update_existing:
            import_result = "would_skip_existing"
        elif existing is not None:
            import_result = "would_update"
        else:
            import_result = "would_import"
        return ImportRow(
            zip_filename=candidate.import_source_name,
            artifact_name=candidate.artifact_name,
            created_artifact_id=str(existing.get("_id")) if existing else None,
            generated_artifact_code=existing.get("artifact_code") if existing else generated_artifact_code(candidate.artifact_name, source_hash, database),
            category=candidate.category,
            image_count=len(images),
            primary_image=primary.display_name,
            primary_image_selection_method=method,
            draft_status=existing.get("status") if existing else "draft",
            import_result=import_result,
            error=None,
        )

    stored_paths: list[str] = []
    try:
        stored_by_entry: dict[str, str] = {}
        for image in images:
            stored = save_image_bytes(
                image.data,
                settings,
                extension=image.extension,
                digest=image.digest,
                source_filename=image.display_name,
            )
            stored_paths.append(stored.image_path)
            stored_by_entry[image.entry_name] = stored.image_path

        primary_path = stored_by_entry[primary.entry_name]
        category_repository.ensure_category(database, candidate.category)

        if existing is not None:
            previous_paths = list(existing.get("image_paths") or [])
            updated = artifact_repository.update_artifact(
                database,
                existing["_id"],
                {
                    "image_paths": stored_paths,
                    "primary_image_path": primary_path,
                    "primary_image_needs_review": needs_review,
                    "primary_image_selection_method": method,
                    "import_source_name": candidate.import_source_name,
                    "import_source_hash": source_hash,
                    "status": "draft",
                    "ai_index_status": "not_indexed",
                    "ai_indexed_image_count": 0,
                    "ai_index_error": None,
                },
            )
            cleanup_images(previous_paths, settings)
        else:
            artifact_code = generated_artifact_code(candidate.artifact_name, source_hash, database)
            updated = artifact_repository.create_artifact(
                database,
                {
                    "artifact_code": artifact_code,
                    "name": candidate.artifact_name,
                    "description": None,
                    "category": candidate.category,
                    "origin": None,
                    "historical_period": None,
                    "material": None,
                    "dimensions": None,
                    "condition": None,
                    "custom_fields": [],
                    "image_paths": stored_paths,
                    "primary_image_path": primary_path,
                    "primary_image_needs_review": needs_review,
                    "primary_image_selection_method": method,
                    "status": "draft",
                    "created_by": "bulk-import",
                    "import_source_name": candidate.import_source_name,
                    "import_source_hash": source_hash,
                    "ai_index_status": "not_indexed",
                    "ai_indexed_image_count": 0,
                    "ai_index_error": None,
                },
            )

        if args.index_ai:
            try:
                ArtifactIndexingService.from_settings(settings).index_artifact(database, updated)
                updated = artifact_repository.get_artifact(database, updated["_id"]) or updated
            except Exception:
                logger.exception("AI indexing failed after import for %s", candidate.import_source_name)

        return ImportRow(
            zip_filename=candidate.import_source_name,
            artifact_name=updated.get("name") or candidate.artifact_name,
            created_artifact_id=str(updated.get("_id")),
            generated_artifact_code=updated.get("artifact_code"),
            category=updated.get("category") or candidate.category,
            image_count=len(stored_paths),
            primary_image=primary_path,
            primary_image_selection_method=method,
            draft_status=updated.get("status") or "draft",
            import_result="updated" if existing else "imported",
            error=None,
        )
    except (DuplicateKeyError, PyMongoError, HTTPException, ValueError) as exc:
        cleanup_images(stored_paths, settings)
        logger.exception("Artifact ZIP import failed for %s", candidate.import_source_name)
        detail = exc.detail if isinstance(exc, HTTPException) else str(exc)
        return failed_row(candidate, detail)
    except Exception as exc:
        cleanup_images(stored_paths, settings)
        logger.exception("Unexpected artifact ZIP import failure for %s", candidate.import_source_name)
        return failed_row(candidate, str(exc) or "Unexpected import failure.")


def failed_row(candidate: CandidateZip, message: str) -> ImportRow:
    return ImportRow(
        zip_filename=candidate.import_source_name,
        artifact_name=candidate.artifact_name,
        created_artifact_id=None,
        generated_artifact_code=None,
        category=candidate.category,
        image_count=0,
        primary_image=None,
        primary_image_selection_method=None,
        draft_status=None,
        import_result="failed",
        error=message,
    )


def natural_path_key(path: Path) -> list[Any]:
    return natural_string_key(path.as_posix())


def natural_string_key(value: str) -> list[Any]:
    return [int(part) if part.isdigit() else part.lower() for part in re.split(r"(\d+)", value)]


def build_report(rows: list[ImportRow], found_zip_files: int, *, dry_run: bool) -> ImportReport:
    report = ImportReport(found_zip_files=found_zip_files, rows=rows)
    for row in rows:
        if row.import_result in {"imported", "updated", "would_import", "would_update", "would_skip_existing"}:
            report.valid_zip_files += 1
            report.largest_artifact_image_count = max(report.largest_artifact_image_count, row.image_count)
            if dry_run:
                if row.import_result in {"would_import", "would_update"}:
                    report.images_would_be_imported += row.image_count
                elif row.import_result == "would_skip_existing":
                    report.skipped_existing += 1
            else:
                report.imported += 1
                report.images_imported += row.image_count
                report.draft_artifacts_created += 1 if row.import_result == "imported" else 0
            if row.primary_image_selection_method == "deterministic_natural_sort":
                report.primary_images_assigned_automatically += 1
            elif row.primary_image_selection_method:
                report.explicit_primary_images_detected += 1
        elif row.import_result == "skipped_existing":
            report.valid_zip_files += 1
            report.largest_artifact_image_count = max(report.largest_artifact_image_count, row.image_count)
            report.skipped_existing += 1
        elif row.import_result == "failed":
            report.failed += 1
    return report


def print_report(report: ImportReport) -> None:
    print()
    print("Artifact ZIP Import")
    print()
    print(f"Found ZIP files: {report.found_zip_files}")
    print(f"Valid ZIP files: {report.valid_zip_files}")
    print(f"Imported: {report.imported}")
    print(f"Skipped existing: {report.skipped_existing}")
    print(f"Failed: {report.failed}")
    print(f"Images imported: {report.images_imported}")
    print(f"Total images that would be imported: {report.images_would_be_imported}")
    print(f"Largest artifact image count: {report.largest_artifact_image_count}")
    print(f"Valid images discarded: {'Yes' if report.valid_images_discarded else 'No'}")
    print(f"Draft artifacts created: {report.draft_artifacts_created}")
    print(f"Primary images assigned automatically: {report.primary_images_assigned_automatically}")
    print(f"Explicit primary images detected: {report.explicit_primary_images_detected}")
    failures = [row for row in report.rows or [] if row.import_result == "failed"]
    if failures:
        print()
        for row in failures:
            print(row.zip_filename)
            print(f"Reason: {row.error or 'Import failed.'}")


def write_report(report: ImportReport, report_file: Path) -> None:
    report_file.parent.mkdir(parents=True, exist_ok=True)
    report_file.write_text(
        json.dumps(asdict(report), indent=2, ensure_ascii=False),
        encoding="utf-8",
    )


def main() -> int:
    args = parse_args()
    reporter = Reporter()
    settings = load_settings(reporter)
    if settings is None:
        return 1

    source = Path(args.source).expanduser()
    if not source.is_absolute():
        source = (Path.cwd() / source).resolve()
    try:
        candidates = discover_zips(source)
    except ValueError as exc:
        reporter.fail(str(exc))
        return 1

    if not candidates:
        reporter.warn(f"No ZIP files found in {source}")
    else:
        reporter.info(f"Found {len(candidates)} ZIP file(s) in {source}")

    rows: list[ImportRow] = []
    database = None
    try:
        database = mongo_manager.connect(settings)
        reporter.ok("MongoDB connected")
        for candidate in candidates:
            try:
                rows.append(import_candidate(candidate, settings, database, args))
            except Exception as exc:
                logger.exception("Unhandled import failure for %s", candidate.import_source_name)
                rows.append(failed_row(candidate, str(exc) or "Unexpected import failure."))
    except MongoConnectionError as exc:
        reporter.fail(str(exc))
    finally:
        mongo_manager.close()

    report = build_report(rows, len(candidates), dry_run=args.dry_run)
    print_report(report)
    if args.report_file:
        report_file = Path(args.report_file).expanduser()
        if not report_file.is_absolute():
            report_file = (Path.cwd() / report_file).resolve()
        write_report(report, report_file)
        reporter.ok(f"Report written: {report_file}")

    return 1 if reporter.failures or report.failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
