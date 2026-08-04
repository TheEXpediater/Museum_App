from __future__ import annotations

from datetime import datetime
import re

from pydantic import BaseModel, EmailStr, Field, field_validator, model_validator


GUEST_RELATIONSHIPS = {
    "Alumni or Former Student",
    "Current Employee",
    "Former Employee",
    "General Visitor",
    "Other",
}

YEAR_LEVELS = {
    "First Year",
    "Second Year",
    "Third Year",
    "Fourth Year",
    "Fifth Year",
    "Graduate Student",
}

CONTROL_CHARACTER_PATTERN = re.compile(r"[\x00-\x1f\x7f]")
REPEATED_WHITESPACE_PATTERN = re.compile(r"\s+")


def clean_text(value: str | None, *, max_length: int, required: bool = False) -> str | None:
    if value is None:
        if required:
            raise ValueError("This field is required.")
        return None
    cleaned = REPEATED_WHITESPACE_PATTERN.sub(" ", value.strip())
    if required and not cleaned:
        raise ValueError("This field is required.")
    if CONTROL_CHARACTER_PATTERN.search(cleaned):
        raise ValueError("Control characters are not allowed.")
    if len(cleaned) > max_length:
        raise ValueError(f"Must be {max_length} characters or fewer.")
    return cleaned or None


class GuestSessionRequest(BaseModel):
    first_name: str = Field(min_length=1, max_length=80)
    last_name: str = Field(min_length=1, max_length=80)
    relationship_type: str = Field(min_length=1, max_length=80)
    relationship_detail: str | None = Field(default=None, max_length=120)
    batch_or_graduation_year: str | None = Field(default=None, max_length=40)
    office_or_department: str | None = Field(default=None, max_length=120)
    device_session_id: str | None = Field(default=None, max_length=120)

    @field_validator("first_name", "last_name")
    @classmethod
    def validate_required_text(cls, value: str) -> str:
        return clean_text(value, max_length=80, required=True) or ""

    @field_validator("relationship_type")
    @classmethod
    def validate_relationship(cls, value: str) -> str:
        cleaned = clean_text(value, max_length=80, required=True) or ""
        if cleaned not in GUEST_RELATIONSHIPS:
            raise ValueError("Select a valid PSAU relationship.")
        return cleaned

    @field_validator("relationship_detail", "batch_or_graduation_year", "office_or_department", "device_session_id")
    @classmethod
    def validate_optional_text(cls, value: str | None) -> str | None:
        return clean_text(value, max_length=120)

    @model_validator(mode="after")
    def validate_conditional_detail(self):
        if self.relationship_type == "Other" and not self.relationship_detail:
            raise ValueError("Please specify your PSAU relationship.")
        if self.relationship_type not in {"Alumni or Former Student"}:
            self.batch_or_graduation_year = None
        if self.relationship_type not in {"Current Employee", "Former Employee"}:
            self.office_or_department = None
        if self.relationship_type != "Other":
            self.relationship_detail = None
        return self


class GuestProfile(BaseModel):
    id: str
    first_name: str
    last_name: str
    display_name: str
    relationship_type: str
    relationship_detail: str | None = None
    batch_or_graduation_year: str | None = None
    office_or_department: str | None = None
    role: str = "guest"
    expires_at: datetime


class StudentRegisterRequest(BaseModel):
    student_id: str = Field(min_length=1, max_length=40)
    first_name: str = Field(min_length=1, max_length=80)
    middle_initial: str | None = Field(default=None, max_length=1)
    last_name: str = Field(min_length=1, max_length=80)
    year_level: str = Field(min_length=1, max_length=40)
    course: str = Field(min_length=1, max_length=120)
    email: EmailStr
    password: str = Field(min_length=8, max_length=256)
    confirm_password: str = Field(min_length=8, max_length=256)

    @field_validator("student_id")
    @classmethod
    def validate_student_id(cls, value: str) -> str:
        return (clean_text(value, max_length=40, required=True) or "").upper()

    @field_validator("first_name", "last_name")
    @classmethod
    def validate_student_required_name(cls, value: str) -> str:
        return clean_text(value, max_length=80, required=True) or ""

    @field_validator("middle_initial")
    @classmethod
    def validate_middle_initial(cls, value: str | None) -> str | None:
        cleaned = clean_text(value, max_length=1)
        if cleaned is not None and not cleaned.isalpha():
            raise ValueError("Middle initial must be one letter.")
        return cleaned.upper() if cleaned else None

    @field_validator("year_level")
    @classmethod
    def validate_year_level(cls, value: str) -> str:
        cleaned = clean_text(value, max_length=40, required=True) or ""
        if cleaned not in YEAR_LEVELS:
            raise ValueError("Select a valid year level.")
        return cleaned

    @field_validator("course")
    @classmethod
    def validate_course(cls, value: str) -> str:
        return clean_text(value, max_length=120, required=True) or ""

    @field_validator("email")
    @classmethod
    def normalize_email(cls, value: EmailStr) -> str:
        return str(value).lower().strip()

    @model_validator(mode="after")
    def validate_passwords(self):
        if self.password != self.confirm_password:
            raise ValueError("Passwords do not match.")
        if not re.search(r"[A-Z]", self.password):
            raise ValueError("Password must include one uppercase letter.")
        if not re.search(r"[a-z]", self.password):
            raise ValueError("Password must include one lowercase letter.")
        if not re.search(r"\d", self.password):
            raise ValueError("Password must include one number.")
        return self


class StudentLoginRequest(BaseModel):
    identifier: str = Field(min_length=1, max_length=120)
    password: str = Field(min_length=1, max_length=256)

    @field_validator("identifier")
    @classmethod
    def validate_identifier(cls, value: str) -> str:
        return clean_text(value, max_length=120, required=True) or ""


class StudentProfile(BaseModel):
    id: str
    student_id: str
    first_name: str
    middle_initial: str | None = None
    last_name: str
    display_name: str
    year_level: str
    course: str
    email: EmailStr
    role: str = "student"


class VisitorTokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_in: int
    account_type: str
    profile: GuestProfile | StudentProfile


class VisitorMeResponse(BaseModel):
    account_type: str
    profile: GuestProfile | StudentProfile


class LogoutResponse(BaseModel):
    message: str
