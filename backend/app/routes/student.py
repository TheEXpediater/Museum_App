from __future__ import annotations

from fastapi import APIRouter, HTTPException, Request, status
from pymongo.errors import DuplicateKeyError

from app.auth.jwt_handler import create_access_token
from app.auth.password import hash_password, verify_password
from app.repositories import visitor_repository
from app.schemas.visitor import StudentLoginRequest, StudentProfile, StudentRegisterRequest, VisitorTokenResponse


router = APIRouter(prefix="/student", tags=["Student"])


def student_profile(document: dict) -> StudentProfile:
    return StudentProfile(
        id=str(document.get("id") or document["_id"]),
        student_id=document["student_id"],
        first_name=document["first_name"],
        middle_initial=document.get("middle_initial"),
        last_name=document["last_name"],
        display_name=document["display_name"],
        year_level=document["year_level"],
        course=document["course"],
        email=document["email"],
        role="student",
    )


def _ensure_course_allowed(request: Request, course: str) -> None:
    configured_programs = list(request.app.state.database.programs.find({"active": True}, {"name": 1}))
    if not configured_programs:
        return
    allowed = {program.get("name", "").strip().casefold() for program in configured_programs}
    if course.strip().casefold() not in allowed:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Select a configured course or program.")


def _duplicate_response(field: str) -> HTTPException:
    if field == "student_id":
        return HTTPException(status_code=status.HTTP_409_CONFLICT, detail="A student account with this Student ID already exists.")
    return HTTPException(status_code=status.HTTP_409_CONFLICT, detail="A student account with this email already exists.")


@router.post("/register", response_model=VisitorTokenResponse, status_code=status.HTTP_201_CREATED)
def register_student(payload: StudentRegisterRequest, request: Request) -> VisitorTokenResponse:
    _ensure_course_allowed(request, payload.course)
    duplicate = visitor_repository.student_duplicate_field(
        request.app.state.database,
        student_id=payload.student_id,
        email=payload.email,
    )
    if duplicate:
        raise _duplicate_response(duplicate)

    try:
        student = visitor_repository.create_student(
            request.app.state.database,
            {
                "student_id": payload.student_id,
                "first_name": payload.first_name,
                "middle_initial": payload.middle_initial,
                "last_name": payload.last_name,
                "year_level": payload.year_level,
                "course": payload.course,
                "email": payload.email,
                "password_hash": hash_password(payload.password),
            },
        )
    except DuplicateKeyError as exc:
        duplicate = visitor_repository.student_duplicate_field(
            request.app.state.database,
            student_id=payload.student_id,
            email=payload.email,
        )
        raise _duplicate_response(duplicate or "email") from exc

    token, expires_in = create_access_token(str(student["_id"]), student["email"], "student", request.app.state.settings)
    return VisitorTokenResponse(
        access_token=token,
        expires_in=expires_in,
        account_type="student",
        profile=student_profile(student),
    )


@router.post("/login", response_model=VisitorTokenResponse)
def login_student(payload: StudentLoginRequest, request: Request) -> VisitorTokenResponse:
    student = visitor_repository.find_student_by_id_or_email(request.app.state.database, payload.identifier)
    if (
        student is None
        or not student.get("is_active", False)
        or student.get("role") != "student"
        or not verify_password(payload.password, student.get("password_hash", ""))
    ):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid student ID, email, or password.")

    visitor_repository.update_student_last_login(request.app.state.database, student["_id"])
    student = request.app.state.database.students.find_one({"_id": student["_id"]}) or student
    token, expires_in = create_access_token(str(student["_id"]), student["email"], "student", request.app.state.settings)
    return VisitorTokenResponse(
        access_token=token,
        expires_in=expires_in,
        account_type="student",
        profile=student_profile(student),
    )
