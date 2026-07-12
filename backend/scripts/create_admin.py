from __future__ import annotations

import argparse
import getpass
import sys

from pydantic import EmailStr, TypeAdapter, ValidationError
from pymongo.errors import DuplicateKeyError, PyMongoError

from app.auth.password import hash_password
from app.config import get_settings
from app.database.mongodb import MongoConnectionError, ensure_indexes, mongo_manager
from app.repositories.user_repository import create_admin_user, find_user_by_email


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Create the initial Museum Guide admin account.")
    parser.add_argument("--email", help="Admin email address. Defaults to ADMIN_EMAIL.")
    parser.add_argument("--password", help="Admin password. Defaults to ADMIN_PASSWORD or secure prompt.")
    parser.add_argument("--full-name", help="Admin full name. Defaults to ADMIN_FULL_NAME.")
    return parser.parse_args()


def validate_email(email: str) -> str:
    try:
        return TypeAdapter(EmailStr).validate_python(email).lower()
    except ValidationError as exc:
        raise ValueError("Admin email is invalid.") from exc


def main() -> int:
    args = parse_args()
    try:
        settings = get_settings()
    except Exception as exc:
        print(f"Configuration error: {exc}", file=sys.stderr)
        return 1

    email = args.email or settings.admin_email or input("Admin email: ").strip()
    password = args.password or settings.admin_password
    if not password:
        password = getpass.getpass("Admin password: ")
    full_name = args.full_name or settings.admin_full_name

    try:
        email = validate_email(email)
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    if len(password) < 12:
        print("Admin password must be at least 12 characters.", file=sys.stderr)
        return 1

    try:
        database = mongo_manager.connect(settings)
        ensure_indexes(database)
        existing = find_user_by_email(database, email)
        if existing is not None:
            print(f"Admin account already exists for {email}. No changes made.")
            return 0
        create_admin_user(database, email, full_name, hash_password(password))
        print(f"Admin account created for {email}.")
        return 0
    except DuplicateKeyError:
        print(f"Admin account already exists for {email}. No changes made.")
        return 0
    except MongoConnectionError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    except PyMongoError:
        print("Could not create admin account due to a database error.", file=sys.stderr)
        return 1
    finally:
        mongo_manager.close()


if __name__ == "__main__":
    raise SystemExit(main())
