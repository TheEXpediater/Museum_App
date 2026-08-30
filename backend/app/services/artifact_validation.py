from __future__ import annotations

import json
from decimal import Decimal, InvalidOperation
from typing import Any
from uuid import uuid4

from fastapi import HTTPException, status


FIELD_LIMITS = {
    "artifact_code": 50,
    "name": 150,
    "description": 5000,
    "category": 100,
    "origin": 150,
    "historical_period": 150,
    "material": 250,
    "dimensions": 150,
    "condition": 100,
}

CORE_REQUIRED_FIELDS = {"artifact_code", "name"}
CUSTOM_FIELD_TYPES = {"text", "number", "long_text", "date"}
CUSTOM_FIELD_LABEL_LIMIT = 80
CUSTOM_FIELD_VALUE_LIMIT = 2000
CUSTOM_FIELD_UNIT_LIMIT = 32
UNCATEGORIZED = "Uncategorized"


def clean_artifact_fields(values: dict[str, str | None], *, partial: bool) -> dict[str, str | None]:
    cleaned: dict[str, str | None] = {}
    for field, limit in FIELD_LIMITS.items():
        value = values.get(field)
        if value is None:
            if not partial and field in CORE_REQUIRED_FIELDS:
                raise field_error(f"{field} is required.")
            continue

        stripped = value.strip()
        if not stripped:
            if field in CORE_REQUIRED_FIELDS:
                raise field_error(f"{field} is required.")
            cleaned[field] = None
            continue

        reject_control_characters(stripped, field)
        if len(stripped) > limit:
            raise field_error(f"{field} must be {limit} characters or fewer.")
        cleaned[field] = stripped

    return cleaned


def normalize_artifact_status(value: str | None, *, default: str = "draft") -> str:
    normalized = (value or default).strip().lower()
    if normalized not in {"draft", "published"}:
        raise field_error("status must be either draft or published.")
    return normalized


def persisted_status(document: dict[str, Any]) -> str:
    return normalize_artifact_status(document.get("status"), default="published")


def is_published(document: dict[str, Any]) -> bool:
    return persisted_status(document) == "published"


def validate_publishable(document: dict[str, Any]) -> None:
    missing: list[str] = []
    if not str(document.get("artifact_code") or "").strip():
        missing.append("Artifact code")
    if not str(document.get("name") or "").strip():
        missing.append("Name")

    category = str(document.get("category") or "").strip()
    if not category or category.lower() == UNCATEGORIZED.lower():
        missing.append("Category")

    image_paths = list(document.get("image_paths") or [])
    primary_image_path = document.get("primary_image_path")
    if not primary_image_path or primary_image_path not in image_paths:
        missing.append("Primary image")

    if missing:
        raise field_error("Complete these fields before publishing: " + ", ".join(missing))


def parse_remove_image_paths(values: list[str] | None) -> list[str]:
    if not values:
        return []
    parsed: list[str] = []
    for value in values:
        if not value:
            continue
        candidate = value.strip()
        if not candidate:
            continue
        if candidate.startswith("["):
            try:
                decoded = json.loads(candidate)
            except json.JSONDecodeError:
                raise field_error("remove_image_paths must be a JSON array or repeated form field.")
            if not isinstance(decoded, list) or not all(isinstance(item, str) for item in decoded):
                raise field_error("remove_image_paths must contain image path strings.")
            parsed.extend(decoded)
        elif "," in candidate:
            parsed.extend([item.strip() for item in candidate.split(",") if item.strip()])
        else:
            parsed.append(candidate)
    return list(dict.fromkeys(parsed))


def select_paths_by_name_or_path(existing_paths: list[str], requested_paths: list[str]) -> list[str]:
    selected: list[str] = []
    existing_by_name = {path.rsplit("/", 1)[-1]: path for path in existing_paths}
    for requested in requested_paths:
        if requested in existing_paths:
            selected.append(requested)
            continue
        by_name = existing_by_name.get(requested.rsplit("/", 1)[-1])
        if by_name:
            selected.append(by_name)
            continue
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Image was not found on this artifact.")
    return list(dict.fromkeys(selected))


def parse_custom_fields(raw_value: str | None, *, partial: bool) -> list[dict[str, str | None]] | None:
    if raw_value is None:
        return None if partial else []

    stripped = raw_value.strip()
    if not stripped:
        return []

    try:
        decoded = json.loads(stripped)
    except json.JSONDecodeError as exc:
        raise field_error("custom_fields must be a JSON array.") from exc

    if not isinstance(decoded, list):
        raise field_error("custom_fields must be a JSON array.")

    normalized_fields: list[dict[str, str | None]] = []
    seen_labels: set[str] = set()
    for index, item in enumerate(decoded, start=1):
        if not isinstance(item, dict):
            raise field_error(f"custom_fields item {index} must be an object.")

        label = clean_custom_field_text(item.get("label"), "custom field label", CUSTOM_FIELD_LABEL_LIMIT, required=True)
        label_key = label.lower()
        if label_key in seen_labels:
            raise field_error("custom_fields cannot contain duplicate labels on the same artifact.")
        seen_labels.add(label_key)

        field_type = normalize_custom_field_type(item.get("type"))
        value = clean_custom_field_text(item.get("value"), "custom field value", CUSTOM_FIELD_VALUE_LIMIT, required=False)
        unit = clean_custom_field_text(item.get("unit"), "custom field unit", CUSTOM_FIELD_UNIT_LIMIT, required=False)

        if field_type == "number" and value:
            validate_custom_number(value)
        elif field_type != "number":
            unit = None

        field_id = clean_custom_field_id(item.get("id"))
        normalized_fields.append(
            {
                "id": field_id,
                "label": label,
                "value": value or "",
                "unit": unit,
                "type": field_type,
            }
        )

    return normalized_fields


def public_custom_fields(fields: list[dict[str, Any]] | None) -> list[dict[str, str | None]]:
    public_fields: list[dict[str, str | None]] = []
    for field in fields or []:
        value = str(field.get("value") or "").strip()
        label = str(field.get("label") or "").strip()
        if not label or not value:
            continue
        try:
            field_type = normalize_custom_field_type(field.get("type"), fallback="text")
        except HTTPException:
            field_type = "text"
        public_fields.append(
            {
                "label": label,
                "value": value,
                "unit": str(field.get("unit")).strip() if field.get("unit") else None,
                "type": field_type,
            }
        )
    return public_fields


def clean_custom_field_text(value: Any, field_name: str, limit: int, *, required: bool) -> str:
    if value is None:
        if required:
            raise field_error(f"{field_name} is required.")
        return ""
    if not isinstance(value, str):
        value = str(value)
    stripped = value.strip()
    if required and not stripped:
        raise field_error(f"{field_name} is required.")
    if stripped:
        reject_control_characters(stripped, field_name)
    if len(stripped) > limit:
        raise field_error(f"{field_name} must be {limit} characters or fewer.")
    return stripped


def normalize_custom_field_type(value: Any, *, fallback: str | None = None) -> str:
    normalized = str(value or fallback or "text").strip().lower().replace("-", "_").replace(" ", "_")
    if normalized not in CUSTOM_FIELD_TYPES:
        raise field_error("custom field type must be one of: text, number, long_text, date.")
    return normalized


def clean_custom_field_id(value: Any) -> str:
    candidate = str(value or "").strip()
    if not candidate:
        return uuid4().hex
    reject_control_characters(candidate, "custom field id")
    if len(candidate) > 80:
        raise field_error("custom field id must be 80 characters or fewer.")
    return candidate


def validate_custom_number(value: str) -> None:
    try:
        Decimal(value)
    except InvalidOperation as exc:
        raise field_error("number custom field values must be valid numbers.") from exc


def reject_control_characters(value: str, field_name: str) -> None:
    if any(ord(character) < 32 or ord(character) == 127 for character in value):
        raise field_error(f"{field_name} contains unsupported control characters.")


def field_error(message: str) -> HTTPException:
    return HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=message)


def omit_none(values: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in values.items() if value is not None}
