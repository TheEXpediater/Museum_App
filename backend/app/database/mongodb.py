from __future__ import annotations

from pymongo import ASCENDING, DESCENDING, MongoClient
from pymongo.database import Database
from pymongo.errors import PyMongoError, ServerSelectionTimeoutError

from app.config import Settings


class MongoConnectionError(RuntimeError):
    pass


class MongoManager:
    def __init__(self) -> None:
        self.client: MongoClient | None = None
        self.database: Database | None = None

    def connect(self, settings: Settings) -> Database:
        try:
            self.client = MongoClient(settings.mongodb_url, serverSelectionTimeoutMS=5000)
            self.client.admin.command("ping")
            self.database = self.client[settings.mongodb_database]
            ensure_indexes(self.database)
            return self.database
        except ServerSelectionTimeoutError as exc:
            raise MongoConnectionError("Unable to connect to MongoDB. Check MONGODB_URL and ensure MongoDB is running.") from exc
        except PyMongoError as exc:
            raise MongoConnectionError("MongoDB initialization failed.") from exc

    def close(self) -> None:
        if self.client is not None:
            self.client.close()
        self.client = None
        self.database = None


def ensure_indexes(database: Database) -> None:
    database.users.create_index([("email", ASCENDING)], unique=True, name="uniq_users_email")
    database.artifacts.create_index([("artifact_code", ASCENDING)], unique=True, name="uniq_artifact_code")
    database.artifacts.create_index([("name", ASCENDING)], name="idx_artifact_name")
    database.artifacts.create_index([("category", ASCENDING)], name="idx_artifact_category")
    database.artifacts.create_index([("created_at", DESCENDING)], name="idx_artifact_created_at")
    database.artifacts.create_index([("ai_index_status", ASCENDING)], name="idx_artifact_ai_index_status")

    database.students.create_index([("student_id_normalized", ASCENDING)], unique=True, name="uniq_students_student_id")
    database.students.create_index([("email_normalized", ASCENDING)], unique=True, name="uniq_students_email")
    database.students.create_index([("is_active", ASCENDING)], name="idx_students_is_active")
    database.students.create_index([("created_at", DESCENDING)], name="idx_students_created_at")

    database.guest_sessions.create_index([("expires_at", ASCENDING)], expireAfterSeconds=0, name="ttl_guest_sessions_expires_at")
    database.guest_sessions.create_index([("device_session_id", ASCENDING)], name="idx_guest_sessions_device_session_id")
    database.guest_sessions.create_index([("created_at", DESCENDING)], name="idx_guest_sessions_created_at")

    database.news.create_index([("published_at", DESCENDING)], name="idx_news_published_at")
    database.news.create_index([("is_published", ASCENDING)], name="idx_news_is_published")

    database.announcements.create_index([("starts_at", DESCENDING)], name="idx_announcements_starts_at")
    database.announcements.create_index([("expires_at", ASCENDING)], name="idx_announcements_expires_at")
    database.announcements.create_index([("is_active", ASCENDING)], name="idx_announcements_is_active")

    database.museum_articles.create_index([("published_at", DESCENDING)], name="idx_articles_published_at")
    database.museum_articles.create_index([("is_published", ASCENDING)], name="idx_articles_is_published")
    database.museum_articles.create_index([("category", ASCENDING)], name="idx_articles_category")

    database.programs.create_index([("name_normalized", ASCENDING)], unique=True, name="uniq_programs_name")
    database.programs.create_index([("active", ASCENDING)], name="idx_programs_active")


mongo_manager = MongoManager()
