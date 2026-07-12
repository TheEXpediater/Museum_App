from __future__ import annotations

import hashlib
import shutil
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path, PurePosixPath
from uuid import uuid4

from fastapi import HTTPException, UploadFile, status
from PIL import Image, UnidentifiedImageError

from app.config import Settings


ALLOWED_MIME_TYPES = {"image/jpeg", "image/png", "image/webp"}
FORMAT_TO_EXTENSION = {"JPEG": ".jpg", "PNG": ".png", "WEBP": ".webp"}
FORMAT_TO_MIME_TYPES = {
    "JPEG": {"image/jpeg"},
    "PNG": {"image/png"},
    "WEBP": {"image/webp"},
}


@dataclass(frozen=True)
class StoredImage:
    image_path: str
    filename: str
    digest: str


def ensure_upload_directory(settings: Settings) -> None:
    settings.upload_path.mkdir(parents=True, exist_ok=True)
    settings.upload_root_path.mkdir(parents=True, exist_ok=True)


def image_url_for_path(base_url: str, image_path: str | None) -> str | None:
    if not image_path:
        return None
    return f"{base_url.rstrip('/')}/{image_path.lstrip('/')}"


async def save_uploads(files: list[UploadFile] | None, settings: Settings) -> list[StoredImage]:
    if not files:
        return []

    ensure_upload_directory(settings)
    stored: list[StoredImage] = []
    seen_digests: set[str] = set()
    for upload in files:
        stored_image = await save_one_upload(upload, settings)
        if stored_image.digest in seen_digests:
            safe_delete_image(stored_image.image_path, settings)
            cleanup_images([image.image_path for image in stored], settings)
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Duplicate image uploads are not allowed in the same request.",
            )
        seen_digests.add(stored_image.digest)
        stored.append(stored_image)
    return stored


async def save_one_upload(upload: UploadFile, settings: Settings) -> StoredImage:
    if upload.content_type not in ALLOWED_MIME_TYPES:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="Only JPEG, JPG, PNG, and WEBP images are allowed.",
        )

    max_bytes = settings.max_image_size_mb * 1024 * 1024
    data = await upload.read(max_bytes + 1)
    await upload.close()
    if not data:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Uploaded image is empty.")
    if len(data) > max_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"Image exceeds the {settings.max_image_size_mb} MB size limit.",
        )

    try:
        with Image.open(BytesIO(data)) as image:
            image.verify()
            image_format = image.format
    except (UnidentifiedImageError, OSError) as exc:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="Uploaded file is not a valid image.",
        ) from exc

    extension = FORMAT_TO_EXTENSION.get(image_format or "")
    if extension is None or upload.content_type not in FORMAT_TO_MIME_TYPES.get(image_format or "", set()):
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="Image content does not match an allowed image type.",
        )

    digest = hashlib.sha256(data).hexdigest()
    for _ in range(10):
        filename = f"{uuid4().hex}{extension}"
        destination = settings.upload_path / filename
        if not destination.exists():
            break
    else:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Could not allocate image filename.")

    destination.write_bytes(data)
    image_path = PurePosixPath("uploads", "images", filename).as_posix()
    return StoredImage(image_path=image_path, filename=filename, digest=digest)


def cleanup_images(image_paths: list[str], settings: Settings) -> None:
    for image_path in image_paths:
        safe_delete_image(image_path, settings)


def safe_delete_image(image_path: str, settings: Settings) -> None:
    filename = Path(image_path).name
    if not filename:
        return
    destination = (settings.upload_path / filename).resolve()
    upload_root = settings.upload_path.resolve()
    try:
        destination.relative_to(upload_root)
    except ValueError:
        return
    if destination.exists() and destination.is_file():
        destination.unlink()


def reset_upload_directory(settings: Settings) -> None:
    """Test helper that preserves the directory but removes stored files safely."""
    ensure_upload_directory(settings)
    for item in settings.upload_path.iterdir():
        if item.is_file() and item.name != ".gitkeep":
            item.unlink()
        elif item.is_dir():
            shutil.rmtree(item)
