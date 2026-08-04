from __future__ import annotations

from datetime import timedelta

from fastapi import APIRouter, Depends, Request

from app.auth.dependencies import require_visitor
from app.auth.jwt_handler import create_access_token
from app.repositories import visitor_repository
from app.schemas.visitor import GuestProfile, GuestSessionRequest, LogoutResponse, VisitorMeResponse, VisitorTokenResponse


router = APIRouter(prefix="/visitor", tags=["Visitor"])


def guest_profile(document: dict) -> GuestProfile:
    return GuestProfile(
        id=str(document.get("id") or document["_id"]),
        first_name=document["first_name"],
        last_name=document["last_name"],
        display_name=document["display_name"],
        relationship_type=document["relationship_type"],
        relationship_detail=document.get("relationship_detail"),
        batch_or_graduation_year=document.get("batch_or_graduation_year"),
        office_or_department=document.get("office_or_department"),
        role="guest",
        expires_at=document["expires_at"],
    )


@router.post("/guest-session", response_model=VisitorTokenResponse)
def create_guest_session(payload: GuestSessionRequest, request: Request) -> VisitorTokenResponse:
    settings = request.app.state.settings
    expires_delta = timedelta(hours=settings.guest_session_expire_hours)
    guest = visitor_repository.create_guest_session(
        request.app.state.database,
        first_name=payload.first_name,
        last_name=payload.last_name,
        relationship_type=payload.relationship_type,
        relationship_detail=payload.relationship_detail,
        batch_or_graduation_year=payload.batch_or_graduation_year,
        office_or_department=payload.office_or_department,
        device_session_id=payload.device_session_id,
        expires_delta=expires_delta,
    )
    token, expires_in = create_access_token(str(guest["_id"]), "", "guest", settings, expires_delta=expires_delta)
    return VisitorTokenResponse(
        access_token=token,
        expires_in=expires_in,
        account_type="guest",
        profile=guest_profile(guest),
    )


@router.get("/me", response_model=VisitorMeResponse)
def visitor_me(current_visitor: dict = Depends(require_visitor)) -> VisitorMeResponse:
    if current_visitor.get("role") == "guest":
        return VisitorMeResponse(account_type="guest", profile=guest_profile(current_visitor))

    from app.routes.student import student_profile

    return VisitorMeResponse(account_type="student", profile=student_profile(current_visitor))


@router.post("/logout", response_model=LogoutResponse)
def visitor_logout() -> LogoutResponse:
    return LogoutResponse(message="Visitor session cleared on this device.")
