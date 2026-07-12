from __future__ import annotations

from datetime import timedelta
from typing import Any

import jwt
from jwt import ExpiredSignatureError, InvalidTokenError

from app.config import Settings
from app.utils import utc_now


class TokenError(RuntimeError):
    pass


class TokenExpiredError(TokenError):
    pass


def create_access_token(user_id: str, email: str, role: str, settings: Settings) -> tuple[str, int]:
    expires_delta = timedelta(minutes=settings.jwt_access_token_expire_minutes)
    expires_at = utc_now() + expires_delta
    payload: dict[str, Any] = {
        "sub": user_id,
        "email": email,
        "role": role,
        "exp": expires_at,
        "iat": utc_now(),
        "type": "access",
    }
    token = jwt.encode(payload, settings.jwt_secret_key, algorithm=settings.jwt_algorithm)
    return token, int(expires_delta.total_seconds())


def decode_access_token(token: str, settings: Settings) -> dict[str, Any]:
    try:
        payload = jwt.decode(token, settings.jwt_secret_key, algorithms=[settings.jwt_algorithm])
    except ExpiredSignatureError as exc:
        raise TokenExpiredError("Token has expired.") from exc
    except InvalidTokenError as exc:
        raise TokenError("Token is invalid.") from exc

    if payload.get("type") != "access" or not payload.get("sub"):
        raise TokenError("Token is invalid.")
    return payload
