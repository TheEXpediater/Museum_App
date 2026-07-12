from __future__ import annotations

from pymongo.database import Database

from app.utils import utc_now


def find_user_by_email(database: Database, email: str) -> dict | None:
    return database.users.find_one({"email": email.lower().strip()})


def create_admin_user(database: Database, email: str, full_name: str, password_hash: str) -> str:
    now = utc_now()
    result = database.users.insert_one(
        {
            "email": email.lower().strip(),
            "full_name": full_name.strip(),
            "password_hash": password_hash,
            "role": "admin",
            "is_active": True,
            "created_at": now,
            "updated_at": now,
        }
    )
    return str(result.inserted_id)
