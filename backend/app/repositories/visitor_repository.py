from __future__ import annotations

from datetime import datetime, timedelta
import re
from typing import Any

from bson import ObjectId
from pymongo import DESCENDING
from pymongo.database import Database

from app.utils import utc_now


def build_display_name(first_name: str, middle_initial: str | None, last_name: str) -> str:
    parts = [first_name.strip()]
    if middle_initial:
        parts.append(f"{middle_initial.strip().upper()}.")
    parts.append(last_name.strip())
    return " ".join(part for part in parts if part)


def create_guest_session(
    database: Database,
    *,
    first_name: str,
    last_name: str,
    relationship_type: str,
    relationship_detail: str | None,
    batch_or_graduation_year: str | None,
    office_or_department: str | None,
    device_session_id: str | None,
    expires_delta: timedelta,
) -> dict:
    now = utc_now()
    document: dict[str, Any] = {
        "first_name": first_name,
        "last_name": last_name,
        "display_name": build_display_name(first_name, None, last_name),
        "relationship_type": relationship_type,
        "relationship_detail": relationship_detail,
        "batch_or_graduation_year": batch_or_graduation_year,
        "office_or_department": office_or_department,
        "role": "guest",
        "created_at": now,
        "expires_at": now + expires_delta,
        "last_seen_at": now,
        "device_session_id": device_session_id,
    }
    result = database.guest_sessions.insert_one(document)
    document["_id"] = result.inserted_id
    return document


def find_student_by_id_or_email(database: Database, identifier: str) -> dict | None:
    normalized = identifier.strip()
    return database.students.find_one(
        {
            "$or": [
                {"student_id_normalized": normalized.upper()},
                {"email_normalized": normalized.lower()},
            ]
        }
    )


def create_student(database: Database, data: dict[str, Any]) -> dict:
    now = utc_now()
    document = {
        **data,
        "student_id_normalized": data["student_id"].upper().strip(),
        "email_normalized": data["email"].lower().strip(),
        "display_name": build_display_name(data["first_name"], data.get("middle_initial"), data["last_name"]),
        "role": "student",
        "account_status": "pending",
        "is_active": False,
        "approved_at": None,
        "approved_by": None,
        "created_at": now,
        "updated_at": now,
        "last_login_at": None,
    }
    result = database.students.insert_one(document)
    document["_id"] = result.inserted_id
    return document


def update_student_last_login(database: Database, student_id: ObjectId) -> None:
    database.students.update_one(
        {"_id": student_id},
        {"$set": {"last_login_at": utc_now(), "updated_at": utc_now()}},
    )


def student_duplicate_field(database: Database, *, student_id: str, email: str) -> str | None:
    if database.students.find_one({"student_id_normalized": student_id.upper().strip()}):
        return "student_id"
    if database.students.find_one({"email_normalized": email.lower().strip()}):
        return "email"
    return None


def normalize_account_status(student: dict) -> str:
    status = student.get("account_status")
    if status in {"pending", "active", "inactive"}:
        return status
    return "active" if student.get("is_active", False) else "inactive"


def find_student_by_id(database: Database, student_id: ObjectId) -> dict | None:
    return database.students.find_one({"_id": student_id})


def list_students(database: Database, *, status: str | None = None, search: str | None = None) -> list[dict]:
    query: dict[str, Any] = {}
    students = list(database.students.find(query).sort([("created_at", DESCENDING)]))

    if status and status != "all":
        students = [student for student in students if normalize_account_status(student) == status]

    cleaned_search = (search or "").strip()
    if cleaned_search:
        pattern = re.compile(re.escape(cleaned_search), re.IGNORECASE)
        students = [
            student
            for student in students
            if pattern.search(student.get("student_id", ""))
            or pattern.search(student.get("display_name", ""))
            or pattern.search(student.get("email", ""))
        ]

    return students


def update_student_status(
    database: Database,
    student_id: ObjectId,
    *,
    account_status: str,
    approved_by: str | None,
) -> dict | None:
    existing = database.students.find_one({"_id": student_id})
    if existing is None:
        return None

    now = utc_now()
    updates: dict[str, Any] = {
        "account_status": account_status,
        "is_active": account_status == "active",
        "updated_at": now,
    }
    if account_status == "active" and normalize_account_status(existing) == "pending":
        updates["approved_at"] = now
        updates["approved_by"] = approved_by

    database.students.update_one({"_id": student_id}, {"$set": updates})
    return database.students.find_one({"_id": student_id})
