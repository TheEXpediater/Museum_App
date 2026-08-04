from __future__ import annotations

from datetime import datetime
from typing import Any

from bson import ObjectId
from pymongo import DESCENDING
from pymongo.database import Database

from app.repositories import artifact_repository
from app.utils import utc_now


def _published_query() -> dict[str, Any]:
    return {"is_published": True}


def _active_announcement_query(now: datetime | None = None) -> dict[str, Any]:
    now = now or utc_now()
    return {
        "is_active": True,
        "$and": [
            {"$or": [{"starts_at": {"$exists": False}}, {"starts_at": None}, {"starts_at": {"$lte": now}}]},
            {"$or": [{"expires_at": {"$exists": False}}, {"expires_at": None}, {"expires_at": {"$gt": now}}]},
        ],
    }


def list_news(database: Database, *, limit: int | None = None) -> list[dict]:
    cursor = database.news.find(_published_query()).sort([("published_at", DESCENDING), ("created_at", DESCENDING)])
    if limit is not None:
        cursor = cursor.limit(limit)
    return list(cursor)


def get_published_news(database: Database, news_id: ObjectId) -> dict | None:
    return database.news.find_one({"_id": news_id, **_published_query()})


def list_announcements(database: Database, *, limit: int | None = None) -> list[dict]:
    cursor = database.announcements.find(_active_announcement_query()).sort([("starts_at", DESCENDING), ("created_at", DESCENDING)])
    if limit is not None:
        cursor = cursor.limit(limit)
    return list(cursor)


def list_articles(database: Database, *, search: str | None = None, category: str | None = None, limit: int | None = None) -> list[dict]:
    query: dict[str, Any] = _published_query()
    if category:
        query["category"] = {"$regex": f"^{category}$", "$options": "i"}
    if search:
        query["$or"] = [
            {"title": {"$regex": search, "$options": "i"}},
            {"summary": {"$regex": search, "$options": "i"}},
        ]
    cursor = database.museum_articles.find(query).sort([("published_at", DESCENDING), ("created_at", DESCENDING)])
    if limit is not None:
        cursor = cursor.limit(limit)
    return list(cursor)


def get_published_article(database: Database, article_id: ObjectId) -> dict | None:
    return database.museum_articles.find_one({"_id": article_id, **_published_query()})


def get_museum_information(database: Database) -> dict | None:
    return database.museum_information.find_one({}, sort=[("updated_at", DESCENDING)])


def list_programs(database: Database) -> list[dict]:
    return list(database.programs.find({"active": True}).sort([("name", 1)]))


def list_featured_artifacts(database: Database, *, limit: int = 5) -> list[dict]:
    featured = list(database.artifacts.find({"is_featured": True}).sort([("updated_at", DESCENDING)]).limit(limit))
    if featured:
        return featured
    return artifact_repository.list_recent_artifacts(database, limit=limit)
