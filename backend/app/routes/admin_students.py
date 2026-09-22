from __future__ import annotations

from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Request, status

from app.auth.dependencies import require_admin
from app.repositories import visitor_repository
from app.schemas.admin import StudentAccountDetail, StudentAccountListItem, StudentStatusUpdateRequest
from app.utils import to_object_id


router = APIRouter(prefix="/admin/students", tags=["Admin Students"], dependencies=[Depends(require_admin)])


def serialize_datetime(value) -> str | None:
    if value is None:
        return None
    if isinstance(value, datetime):
        return value.isoformat()
    return str(value)


def serialize_student_list_item(document: dict) -> StudentAccountListItem:
    return StudentAccountListItem(
        id=str(document["_id"]),
        student_id=document.get("student_id", ""),
        display_name=document.get("display_name", ""),
        email=document.get("email", ""),
        account_status=visitor_repository.normalize_account_status(document),
        created_at=serialize_datetime(document.get("created_at")) or "",
    )


def serialize_student_detail(document: dict) -> StudentAccountDetail:
    return StudentAccountDetail(
        id=str(document["_id"]),
        student_id=document.get("student_id", ""),
        first_name=document.get("first_name", ""),
        middle_initial=document.get("middle_initial"),
        last_name=document.get("last_name", ""),
        display_name=document.get("display_name", ""),
        email=document.get("email", ""),
        course=document.get("course", ""),
        year_level=document.get("year_level", ""),
        account_status=visitor_repository.normalize_account_status(document),
        created_at=serialize_datetime(document.get("created_at")) or "",
        updated_at=serialize_datetime(document.get("updated_at")) or "",
        approved_at=serialize_datetime(document.get("approved_at")),
        last_login_at=serialize_datetime(document.get("last_login_at")),
    )


@router.get("", response_model=list[StudentAccountListItem])
def list_students(request: Request, status: str = "pending", search: str | None = None) -> list[StudentAccountListItem]:
    students = visitor_repository.list_students(request.app.state.database, status=status, search=search)
    return [serialize_student_list_item(student) for student in students]


@router.get("/{student_id}", response_model=StudentAccountDetail)
def get_student(student_id: str, request: Request) -> StudentAccountDetail:
    object_id = to_object_id(student_id)
    if object_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Student account was not found.")
    student = visitor_repository.find_student_by_id(request.app.state.database, object_id)
    if student is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Student account was not found.")
    return serialize_student_detail(student)


@router.patch("/{student_id}/status", response_model=StudentAccountDetail)
def update_student_status(
    student_id: str,
    payload: StudentStatusUpdateRequest,
    request: Request,
    current_admin: dict = Depends(require_admin),
) -> StudentAccountDetail:
    object_id = to_object_id(student_id)
    if object_id is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Student account was not found.")
    student = visitor_repository.update_student_status(
        request.app.state.database,
        object_id,
        account_status=payload.account_status,
        approved_by=current_admin.get("id"),
    )
    if student is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Student account was not found.")
    return serialize_student_detail(student)
