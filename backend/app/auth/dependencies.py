from __future__ import annotations

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.auth.jwt_handler import TokenError, TokenExpiredError, decode_access_token
from app.config import Settings
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


def get_current_user(
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

    user = get_database(request).users.find_one({"_id": object_id})
    if user is None or not user.get("is_active", False):
        raise credentials_exception()
    user["id"] = str(user["_id"])
    return user


def require_admin(current_user: dict = Depends(get_current_user)) -> dict:
    if current_user.get("role") != "admin":
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Admin role is required.")
    return current_user
