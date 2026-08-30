from __future__ import annotations

import re
from typing import Any

from bson import ObjectId
from pymongo import ASCENDING
from pymongo.collection import Collection
from pymongo.database import Database

from app.utils import utc_now


UNCATEGORIZED = "Uncategorized"


def collection(database: Database) -> Collection:
    return database.artifact_categories


def normalize_name(name: str) -> str:
    return " ".join(name.strip().split()).lower()


def clean_name(name: str) -> str:
    cleaned = " ".join(name.strip().split())
    if not cleaned:
        raise ValueError("Category name is required.")
    if any(ord(character) < 32 or ord(character) == 127 for character in cleaned):
        raise ValueError("Category name contains unsupported control characters.")
    if len(cleaned) > 100:
        raise ValueError("Category name must be 100 characters or fewer.")
    return cleaned


def list_categories(database: Database, *, include_inactive: bool = False) -> list[dict]:
    query: dict[str, Any] = {} if include_inactive else {"is_active": True}
    return list(collection(database).find(query).sort([("name", ASCENDING)]))


def find_by_name(database: Database, name: str) -> dict | None:
    return collection(database).find_one({"normalized_name": normalize_name(name)})


def create_category(
    database: Database,
    *,
    name: str,
    suggested_fields: list[dict[str, Any]] | None = None,
    reactivate_existing: bool = True,
) -> dict:
    cleaned_name = clean_name(name)
    normalized_name = normalize_name(cleaned_name)
    now = utc_now()

    existing = collection(database).find_one({"normalized_name": normalized_name})
    if existing is not None:
        if reactivate_existing and not existing.get("is_active", True):
            collection(database).update_one(
                {"_id": existing["_id"]},
                {
                    "$set": {
                        "name": cleaned_name,
                        "is_active": True,
                        "suggested_fields": suggested_fields or existing.get("suggested_fields", []),
                        "updated_at": now,
                    }
                },
            )
            return collection(database).find_one({"_id": existing["_id"]}) or existing
        return existing

    document = {
        "name": cleaned_name,
        "normalized_name": normalized_name,
        "is_active": True,
        "suggested_fields": suggested_fields or [],
        "created_at": now,
        "updated_at": now,
    }
    result = collection(database).insert_one(document)
    document["_id"] = result.inserted_id
    return document


def ensure_category(database: Database, name: str | None) -> dict | None:
    if not name or normalize_name(name) == normalize_name(UNCATEGORIZED):
        return None
    return create_category(database, name=name, reactivate_existing=False)


def update_category(
    database: Database,
    category_id: ObjectId,
    *,
    name: str | None = None,
    is_active: bool | None = None,
    suggested_fields: list[dict[str, Any]] | None = None,
) -> dict | None:
    existing = collection(database).find_one({"_id": category_id})
    if existing is None:
        return None

    updates: dict[str, Any] = {"updated_at": utc_now()}
    old_name = existing.get("name")
    new_name = None
    if name is not None:
        new_name = clean_name(name)
        updates["name"] = new_name
        updates["normalized_name"] = normalize_name(new_name)
    if is_active is not None:
        updates["is_active"] = bool(is_active)
    if suggested_fields is not None:
        updates["suggested_fields"] = suggested_fields

    collection(database).update_one({"_id": category_id}, {"$set": updates})
    if old_name and new_name and normalize_name(old_name) != normalize_name(new_name):
        database.artifacts.update_many(
            {"category": {"$regex": f"^{re.escape(old_name)}$", "$options": "i"}},
            {"$set": {"category": new_name, "updated_at": utc_now()}},
        )
    return collection(database).find_one({"_id": category_id})


def deactivate_category(database: Database, category_id: ObjectId) -> dict | None:
    return update_category(database, category_id, is_active=False)


def count_active_categories(database: Database) -> int:
    return collection(database).count_documents({"is_active": True})
