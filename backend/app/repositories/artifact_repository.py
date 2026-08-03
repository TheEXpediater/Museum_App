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


def list_all_artifacts(database: Database, artifact_id: ObjectId | None = None) -> list[dict]:
    query: dict[str, Any] = {"_id": artifact_id} if artifact_id is not None else {}
    return list(collection(database).find(query).sort([("created_at", DESCENDING)]))


def list_recent_artifacts(database: Database, *, limit: int = 5) -> list[dict]:
    return list(collection(database).find({}).sort([("created_at", DESCENDING)]).limit(limit))


def list_artifacts_by_ai_status(database: Database, statuses: list[str]) -> list[dict]:
    return list(collection(database).find({"ai_index_status": {"$in": statuses}}).sort([("updated_at", DESCENDING)]))


def update_ai_index_state(
    database: Database,
    artifact_id: ObjectId,
    *,
    status: str,
    indexed_image_count: int,
    error: str | None = None,
) -> dict | None:
    updates: dict[str, Any] = {
        "ai_index_status": status,
        "ai_indexed_image_count": max(indexed_image_count, 0),
        "ai_index_error": error,
        "updated_at": utc_now(),
    }
    updates["ai_indexed_at"] = utc_now() if status in {"indexed", "partial"} else None
    collection(database).update_one({"_id": artifact_id}, {"$set": updates})
    return get_artifact(database, artifact_id)


def count_artifacts(database: Database) -> int:
    return collection(database).count_documents({})


def count_total_images(database: Database) -> int:
    pipeline = [
        {"$project": {"image_count": {"$size": {"$ifNull": ["$image_paths", []]}}}},
        {"$group": {"_id": None, "total": {"$sum": "$image_count"}}},
    ]
    result = list(collection(database).aggregate(pipeline))
    return int(result[0]["total"]) if result else 0


def count_categories(database: Database) -> int:
    return len([category for category in collection(database).distinct("category") if category])


def count_ai_status(database: Database, statuses: list[str]) -> int:
    if "not_indexed" in statuses:
        return collection(database).count_documents(
            {
                "$or": [
                    {"ai_index_status": {"$in": statuses}},
                    {"ai_index_status": {"$exists": False}},
                    {"ai_index_status": None},
                ]
            }
        )
    return collection(database).count_documents({"ai_index_status": {"$in": statuses}})
