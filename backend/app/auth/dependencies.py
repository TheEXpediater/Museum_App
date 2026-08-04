from __future__ import annotations

from datetime import datetime, timezone

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.auth.jwt_handler import TokenError, TokenExpiredError, decode_access_token
from app.config import Settings
from app.utils import utc_now
from app.utils import to_object_id


bearer_scheme = HTTPBearer(auto_error=False)


def get_request_settings(request: Request) -> Settings:
    return request.app.state.settings


def get_database(request: Request):
    return request.app.state.database


def credentials_exception() -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Invalid or expired authentication token.",
        headers={"WWW-Authenticate": "Bearer"},
    )


def _attach_common_identity(document: dict, role: str) -> dict:
    document["id"] = str(document["_id"])
    document["role"] = role
    return document


def _guest_session_is_valid(guest_session: dict) -> bool:
    expires_at = guest_session.get("expires_at")
    if isinstance(expires_at, datetime) and _as_aware_utc(expires_at) <= utc_now():
        return False
    return guest_session.get("role") == "guest"


def _as_aware_utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def get_current_principal(
    request: Request,
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
) -> dict:
    if credentials is None or credentials.scheme.lower() != "bearer" or not credentials.credentials:
        raise credentials_exception()

    settings = get_request_settings(request)
    try:
        payload = decode_access_token(credentials.credentials, settings)
    except (TokenExpiredError, TokenError):
        raise credentials_exception()

    user_id = payload.get("sub")
    object_id = to_object_id(user_id)
    if object_id is None:
        raise credentials_exception()

    database = get_database(request)
    role = payload.get("role")
    if role == "admin":
        user = database.users.find_one({"_id": object_id})
        if user is None or not user.get("is_active", False) or user.get("role") != "admin":
            raise credentials_exception()
        return _attach_common_identity(user, "admin")

    if role == "student":
        student = database.students.find_one({"_id": object_id})
        if student is None or not student.get("is_active", False) or student.get("role") != "student":
            raise credentials_exception()
        return _attach_common_identity(student, "student")

    if role == "guest":
        guest_session = database.guest_sessions.find_one({"_id": object_id})
        if guest_session is None or not _guest_session_is_valid(guest_session):
            raise credentials_exception()
        database.guest_sessions.update_one({"_id": object_id}, {"$set": {"last_seen_at": utc_now()}})
        return _attach_common_identity(guest_session, "guest")

    legacy_user = database.users.find_one({"_id": object_id})
    if legacy_user is not None and legacy_user.get("is_active", False):
        return _attach_common_identity(legacy_user, legacy_user.get("role", role or ""))

    raise credentials_exception()


def get_current_user(current_principal: dict = Depends(get_current_principal)) -> dict:
    return current_principal


def require_admin(current_principal: dict = Depends(get_current_principal)) -> dict:
    if current_principal.get("role") != "admin":
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Admin role is required.")
    return current_principal


def require_student(current_principal: dict = Depends(get_current_principal)) -> dict:
    if current_principal.get("role") != "student":
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Student role is required.")
    return current_principal


def require_visitor(current_principal: dict = Depends(get_current_principal)) -> dict:
    if current_principal.get("role") not in {"student", "guest"}:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Visitor role is required.")
    return current_principal


def require_authenticated_app_user(current_principal: dict = Depends(get_current_principal)) -> dict:
    if current_principal.get("role") not in {"admin", "student", "guest"}:
        raise credentials_exception()
    return current_principal
