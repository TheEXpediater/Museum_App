from __future__ import annotations

from datetime import datetime, timezone

from bson import ObjectId


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def is_valid_object_id(value: str) -> bool:
    return ObjectId.is_valid(value)


def to_object_id(value: str) -> ObjectId | None:
    if not ObjectId.is_valid(value):
        return None
    return ObjectId(value)


def stringify_object_id(document: dict | None) -> dict | None:
    if document is None:
        return None
    converted = dict(document)
    if "_id" in converted:
        converted["id"] = str(converted.pop("_id"))
    return converted
