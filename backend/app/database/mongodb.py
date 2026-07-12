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


mongo_manager = MongoManager()
