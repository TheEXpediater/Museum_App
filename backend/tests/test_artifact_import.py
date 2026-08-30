from __future__ import annotations

from io import BytesIO
from pathlib import Path
from types import SimpleNamespace
import zipfile

import mongomock
import pytest
from PIL import Image

from app.config import Settings
from scripts.import_artifact_zips import build_report, candidate_for_zip, discover_zips, import_candidate


JWT_SECRET = "test-secret-key-that-is-long-enough"


def make_settings(tmp_path: Path) -> Settings:
    return Settings(
        app_name="Museum Guide Import Test",
        app_env="test",
        mongodb_url="mongodb://localhost:27017",
        mongodb_database="museum_guide_test",
        jwt_secret_key=JWT_SECRET,
        upload_directory=str(tmp_path / "uploads" / "images"),
        max_image_size_mb=1,
        ai_enabled=False,
        _env_file=None,
    )


def image_bytes(format_name: str = "JPEG", color: tuple[int, int, int] = (120, 80, 40)) -> bytes:
    buffer = BytesIO()
    Image.new("RGB", (32, 32), color=color).save(buffer, format=format_name)
    return buffer.getvalue()


def image_entries(count: int) -> dict[str, bytes]:
    return {
        f"photo-{index:02d}.jpg": image_bytes(color=((index * 37) % 256, (index * 71) % 256, (index * 109) % 256))
        for index in range(1, count + 1)
    }


def write_zip(path: Path, entries: dict[str, bytes]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as archive:
        for name, data in entries.items():
            archive.writestr(name, data)


def args(**overrides):
    values = {"dry_run": False, "update_existing": False, "index_ai": False}
    values.update(overrides)
    return SimpleNamespace(**values)


def test_one_zip_creates_one_draft_artifact_with_category_and_deterministic_primary(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()[settings.mongodb_database]
    source = tmp_path / "artifact_import_source"
    zip_path = source / "Agricultural Tools" / "Sample Wooden Plow.zip"
    write_zip(
        zip_path,
        {
            "IMG_002.jpg": image_bytes(color=(10, 20, 30)),
            "IMG_001.jpg": image_bytes(color=(40, 50, 60)),
            "notes.txt": b"ignored",
            "IMG_003.jpg": image_bytes(color=(70, 80, 90)),
        },
    )

    candidate = candidate_for_zip(source, zip_path)
    row = import_candidate(candidate, settings, database, args())

    assert row.import_result == "imported"
    assert database.artifacts.count_documents({}) == 1
    artifact = database.artifacts.find_one()
    assert artifact["name"] == "Sample Wooden Plow"
    assert artifact["category"] == "Agricultural Tools"
    assert artifact["status"] == "draft"
    assert artifact["artifact_code"].startswith("DRAFT-SAMPLE-WOODEN-PLOW-")
    assert len(artifact["image_paths"]) == 3
    assert artifact["primary_image_path"] in artifact["image_paths"]
    assert artifact["primary_image_selection_method"] == "deterministic_natural_sort"
    assert artifact["primary_image_needs_review"] is True
    assert zip_path.exists()


def test_explicit_main_image_is_primary_without_review_flag(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()[settings.mongodb_database]
    source = tmp_path / "artifact_import_source"
    zip_path = source / "Rice Mortar.zip"
    write_zip(zip_path, {"z.jpg": image_bytes(), "main.jpg": image_bytes(color=(1, 2, 3))})

    row = import_candidate(candidate_for_zip(source, zip_path), settings, database, args())
    artifact = database.artifacts.find_one()

    assert row.import_result == "imported"
    assert artifact["primary_image_selection_method"] == "explicit_filename"
    assert artifact["primary_image_needs_review"] is False


def test_root_zip_uses_uncategorized_and_dry_run_makes_no_changes(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()[settings.mongodb_database]
    source = tmp_path / "artifact_import_source"
    zip_path = source / "Hand Sickle.zip"
    write_zip(zip_path, {"01_main.jpg": image_bytes()})

    candidates = discover_zips(source)
    row = import_candidate(candidates[0], settings, database, args(dry_run=True))

    assert row.import_result == "would_import"
    assert row.artifact_name == "Hand Sickle"
    assert row.category == "Uncategorized"
    assert database.artifacts.count_documents({}) == 0
    assert not settings.upload_path.exists()


def test_duplicate_import_is_skipped_by_provenance(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()[settings.mongodb_database]
    source = tmp_path / "artifact_import_source"
    zip_path = source / "Clay Jar.zip"
    write_zip(zip_path, {"main.jpg": image_bytes()})
    candidate = candidate_for_zip(source, zip_path)

    first = import_candidate(candidate, settings, database, args())
    second = import_candidate(candidate, settings, database, args())

    assert first.import_result == "imported"
    assert second.import_result == "skipped_existing"
    assert database.artifacts.count_documents({}) == 1


def test_zip_slip_is_rejected_without_creating_artifact(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()[settings.mongodb_database]
    source = tmp_path / "artifact_import_source"
    zip_path = source / "Unsafe.zip"
    write_zip(zip_path, {"../escape.jpg": image_bytes()})

    row = import_candidate(candidate_for_zip(source, zip_path), settings, database, args())

    assert row.import_result == "failed"
    assert "Unsafe ZIP entry path" in row.error
    assert database.artifacts.count_documents({}) == 0


def test_corrupt_image_is_rejected(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()[settings.mongodb_database]
    source = tmp_path / "artifact_import_source"
    zip_path = source / "Corrupt.zip"
    write_zip(zip_path, {"main.jpg": b"not an image"})

    row = import_candidate(candidate_for_zip(source, zip_path), settings, database, args())

    assert row.import_result == "failed"
    assert "Invalid image" in row.error
    assert database.artifacts.count_documents({}) == 0


@pytest.mark.parametrize("image_count", [6, 20])
def test_zip_importer_accepts_more_than_the_old_five_image_limit(tmp_path, image_count):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()[settings.mongodb_database]
    source = tmp_path / "artifact_import_source"
    zip_path = source / f"Artifact With {image_count} Images.zip"
    write_zip(zip_path, image_entries(image_count))

    row = import_candidate(candidate_for_zip(source, zip_path), settings, database, args())

    assert row.import_result == "imported"
    artifact = database.artifacts.find_one()
    assert len(artifact["image_paths"]) == image_count
    assert len(set(artifact["image_paths"])) == image_count
    assert artifact["primary_image_path"] in artifact["image_paths"]
    assert artifact["image_paths"].count(artifact["primary_image_path"]) == 1


def test_zip_importer_imports_all_42_valid_images_without_discarding(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()[settings.mongodb_database]
    source = tmp_path / "artifact_import_source"
    zip_path = source / "Large Photo Set.zip"
    write_zip(zip_path, image_entries(42))

    row = import_candidate(candidate_for_zip(source, zip_path), settings, database, args())

    assert row.import_result == "imported"
    assert row.image_count == 42
    artifact = database.artifacts.find_one()
    assert len(artifact["image_paths"]) == 42
    assert len(set(artifact["image_paths"])) == 42
    assert artifact["primary_image_path"] in artifact["image_paths"]
    assert artifact["image_paths"].count(artifact["primary_image_path"]) == 1
    assert artifact["primary_image_selection_method"] == "deterministic_natural_sort"
    assert artifact["primary_image_needs_review"] is True


def test_dry_run_reports_many_image_zip_counts_without_writing(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()[settings.mongodb_database]
    source = tmp_path / "artifact_import_source"
    zip_path = source / "Large Dry Run Set.zip"
    write_zip(zip_path, image_entries(42))

    row = import_candidate(candidate_for_zip(source, zip_path), settings, database, args(dry_run=True))
    report = build_report([row], found_zip_files=1, dry_run=True)

    assert row.import_result == "would_import"
    assert row.image_count == 42
    assert database.artifacts.count_documents({}) == 0
    assert report.valid_zip_files == 1
    assert report.failed == 0
    assert report.images_would_be_imported == 42
    assert report.largest_artifact_image_count == 42
    assert report.valid_images_discarded is False


def test_nested_archive_is_rejected_without_discarding_valid_images(tmp_path):
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()[settings.mongodb_database]
    source = tmp_path / "artifact_import_source"
    zip_path = source / "Nested Archive.zip"
    write_zip(zip_path, {"main.jpg": image_bytes(), "extra.zip": b"not imported"})

    row = import_candidate(candidate_for_zip(source, zip_path), settings, database, args())

    assert row.import_result == "failed"
    assert "Nested archive files are not allowed" in row.error
    assert database.artifacts.count_documents({}) == 0


def test_archive_uncompressed_size_limit_remains_active(tmp_path, monkeypatch):
    monkeypatch.setattr("scripts.import_artifact_zips.MAX_UNCOMPRESSED_BYTES", 10)
    settings = make_settings(tmp_path)
    database = mongomock.MongoClient()[settings.mongodb_database]
    source = tmp_path / "artifact_import_source"
    zip_path = source / "Huge Archive.zip"
    write_zip(zip_path, {"main.jpg": image_bytes()})

    row = import_candidate(candidate_for_zip(source, zip_path), settings, database, args())

    assert row.import_result == "failed"
    assert "ZIP uncompressed size" in row.error
    assert database.artifacts.count_documents({}) == 0
