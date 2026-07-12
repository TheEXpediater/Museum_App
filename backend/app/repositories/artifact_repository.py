from __future__ import annotations

from typing import Any

from bson import ObjectId
from pymongo import ASCENDING, DESCENDING
from pymongo.collection import Collection
from pymongo.database import Database

from app.utils import utc_now


SORTS = {
    "newest": [("created_at", DESCENDING)],
    "oldest": [("created_at", ASCENDING)],
    "name_asc": [("name", ASCENDING)],
    "name_desc": [("name", DESCENDING)],
}


def collection(database: Database) -> Collection:
    return database.artifacts


def create_artifact(database: Database, data: dict[str, Any]) -> dict:
    now = utc_now()
    document = {
        **data,
        "created_at": now,
        "updated_at": now,
    }
    result = collection(database).insert_one(document)
    document["_id"] = result.inserted_id
    return document


def get_artifact(database: Database, artifact_id: ObjectId) -> dict | None:
    return collection(database).find_one({"_id": artifact_id})


def find_by_code(database: Database, artifact_code: str) -> dict | None:
    return collection(database).find_one({"artifact_code": artifact_code})


def list_artifacts(
    database: Database,
    *,
    page: int,
    page_size: int,
    search: str | None,
    category: str | None,
    sort: str,
) -> tuple[list[dict], int]:
    query: dict[str, Any] = {}
    if search:
        query["$or"] = [
            {"name": {"$regex": search, "$options": "i"}},
            {"artifact_code": {"$regex": search, "$options": "i"}},
        ]
    if category:
        query["category"] = {"$regex": f"^{category}$", "$options": "i"}

    total = collection(database).count_documents(query)
    cursor = (
        collection(database)
        .find(query)
        .sort(SORTS.get(sort, SORTS["newest"]))
        .skip((page - 1) * page_size)
        .limit(page_size)
    )
    return list(cursor), total


def update_artifact(database: Database, artifact_id: ObjectId, updates: dict[str, Any]) -> dict | None:
    updates["updated_at"] = utc_now()
    collection(database).update_one({"_id": artifact_id}, {"$set": updates})
    return get_artifact(database, artifact_id)


def delete_artifact(database: Database, artifact_id: ObjectId) -> dict | None:
    return collection(database).find_one_and_delete({"_id": artifact_id})
