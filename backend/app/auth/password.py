from __future__ import annotations

import bcrypt


_SUPPORTED_PREFIXES = ("$2a$", "$2b$", "$2y$")


def _password_bytes(password: str) -> bytes:
    return password.encode("utf-8")


def _stored_hash_bytes(password_hash: str) -> bytes | None:
    stored = (password_hash or "").strip()
    if not stored.startswith(_SUPPORTED_PREFIXES):
        return None
    if stored.startswith("$2y$"):
        stored = "$2b$" + stored[4:]
    return stored.encode("utf-8")


def hash_password(password: str) -> str:
    return bcrypt.hashpw(_password_bytes(password), bcrypt.gensalt()).decode("utf-8")


def verify_password(plain_password: str, password_hash: str) -> bool:
    stored = _stored_hash_bytes(password_hash)
    if stored is None:
        return False
    try:
        return bcrypt.checkpw(_password_bytes(plain_password), stored)
    except (TypeError, ValueError):
        return False
