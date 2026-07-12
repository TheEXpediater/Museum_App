from __future__ import annotations

import json
from typing import Any

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

REQUIRED_FIELDS = {"artifact_code", "name", "description", "category"}


def clean_artifact_fields(values: dict[str, str | None], *, partial: bool) -> dict[str, str | None]:
    cleaned: dict[str, str | None] = {}
    for field, limit in FIELD_LIMITS.items():
        value = values.get(field)
        if value is None:
            if not partial and field in REQUIRED_FIELDS:
                raise field_error(f"{field} is required.")
            continue

        stripped = value.strip()
        if not stripped:
            if field in REQUIRED_FIELDS:
                raise field_error(f"{field} is required.")
            cleaned[field] = None
            continue

        if len(stripped) > limit:
            raise field_error(f"{field} must be {limit} characters or fewer.")
        cleaned[field] = stripped

    return cleaned


def validate_image_count(count: int) -> None:
    if count > 5:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="An artifact can have at most five images.",
        )


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


def field_error(message: str) -> HTTPException:
    return HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=message)


def omit_none(values: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in values.items() if value is not None}
