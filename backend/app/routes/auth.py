from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Request, status

from app.auth.dependencies import get_current_user
from app.auth.jwt_handler import create_access_token
from app.auth.password import verify_password
from app.repositories.user_repository import find_user_by_email
from app.schemas.auth import AuthUser, LoginRequest, LoginResponse


router = APIRouter(prefix="/auth", tags=["Authentication"])


def user_response(user: dict) -> AuthUser:
    return AuthUser(
        id=str(user.get("id") or user["_id"]),
        email=user["email"],
        full_name=user["full_name"],
        role=user["role"],
    )


@router.post("/login", response_model=LoginResponse)
def login(payload: LoginRequest, request: Request) -> LoginResponse:
    database = request.app.state.database
    settings = request.app.state.settings
    user = find_user_by_email(database, payload.email)
    if user is None or not user.get("is_active", False) or not verify_password(payload.password, user.get("password_hash", "")):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid email or password.")

    token, expires_in = create_access_token(str(user["_id"]), user["email"], user["role"], settings)
    user["id"] = str(user["_id"])
    return LoginResponse(access_token=token, expires_in=expires_in, user=user_response(user))


@router.get("/me", response_model=AuthUser)
def current_admin(current_user: dict = Depends(get_current_user)) -> AuthUser:
    return user_response(current_user)
