from __future__ import annotations

import json
from decimal import Decimal, InvalidOperation
from typing import Any
from uuid import uuid4

from fastapi import HTTPException, status


ARTIFACT_CODE_LIMIT = 50
ARTIFACT_NAME_LIMIT = 255
CATEGORY_NAME_LIMIT = 150
DESCRIPTION_LIMIT = 10000
SHORT_METADATA_VALUE_LIMIT = 1000
LONG_METADATA_VALUE_LIMIT = 10000
METADATA_LABEL_LIMIT = 150
METADATA_SECTION_TITLE_LIMIT = 150
METADATA_ID_LIMIT = 80
METADATA_UNIT_LIMIT = 32
VISITOR_GALLERY_ADDITIONAL_IMAGE_LIMIT = 5
SAFE_FORMATTING_CONTROLS = {9, 10, 13}

FIELD_LIMITS = {
    "artifact_code": ARTIFACT_CODE_LIMIT,
    "name": ARTIFACT_NAME_LIMIT,
    "description": DESCRIPTION_LIMIT,
    "category": CATEGORY_NAME_LIMIT,
    "origin": LONG_METADATA_VALUE_LIMIT,
    "historical_period": SHORT_METADATA_VALUE_LIMIT,
    "material": SHORT_METADATA_VALUE_LIMIT,
    "dimensions": SHORT_METADATA_VALUE_LIMIT,
    "condition": LONG_METADATA_VALUE_LIMIT,
}

CORE_REQUIRED_FIELDS = {"artifact_code", "name"}
CUSTOM_FIELD_TYPES = {"text", "number", "long_text", "date"}
UNCATEGORIZED = "Uncategorized"
SYSTEM_METADATA_SECTION_IDS = {"historical_details", "physical_details"}


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
            raise field_error(text_limit_message(field))
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


def parse_image_path_list(raw_value: str | None, *, partial: bool, field_name: str) -> list[str] | None:
    if raw_value is None:
        return None if partial else []

    stripped = raw_value.strip()
    if not stripped:
        return []

    try:
        decoded = json.loads(stripped)
    except json.JSONDecodeError:
        decoded = [item.strip() for item in stripped.split(",") if item.strip()]

    if not isinstance(decoded, list) or not all(isinstance(item, str) for item in decoded):
        raise field_error(f"{field_name} must contain image path strings.")

    paths: list[str] = []
    for item in decoded:
        path = item.strip()
        if not path:
            continue
        reject_control_characters(path, field_name)
        paths.append(path)
    return list(dict.fromkeys(paths))


def reconcile_visitor_gallery_paths(
    requested_paths: list[str] | None,
    existing_paths: list[str] | None,
    image_paths: list[str],
    primary_image_path: str | None,
    *,
    strict_membership: bool,
    configured: bool,
) -> list[str]:
    if not configured:
        return default_visitor_gallery_paths(image_paths, primary_image_path)

    source_paths = requested_paths if requested_paths is not None else list(existing_paths or [])

    available = set(image_paths)
    selected: list[str] = []
    for path in source_paths:
        if path == primary_image_path:
            continue
        if path not in available:
            if strict_membership:
                raise field_error("Visitor gallery images must belong to this artifact.")
            continue
        selected.append(path)

    unique_selected = list(dict.fromkeys(selected))
    if len(unique_selected) > VISITOR_GALLERY_ADDITIONAL_IMAGE_LIMIT:
        raise field_error(f"Select up to {VISITOR_GALLERY_ADDITIONAL_IMAGE_LIMIT} additional visitor images.")
    return unique_selected


def default_visitor_gallery_paths(image_paths: list[str], primary_image_path: str | None) -> list[str]:
    return [
        path
        for path in image_paths
        if path and path != primary_image_path
    ][:VISITOR_GALLERY_ADDITIONAL_IMAGE_LIMIT]


def effective_visitor_gallery_paths(document: dict[str, Any]) -> list[str]:
    image_paths = list(document.get("image_paths") or [])
    primary_image_path = document.get("primary_image_path")
    configured = bool(document.get("visitor_gallery_configured", False))
    return reconcile_visitor_gallery_paths(
        None,
        document.get("visitor_gallery_image_paths"),
        image_paths,
        primary_image_path,
        strict_membership=False,
        configured=configured,
    )


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

        label = clean_metadata_text(item.get("label"), "custom field label", METADATA_LABEL_LIMIT, required=True)
        label_key = label.lower()
        if label_key in seen_labels:
            raise field_error("custom_fields cannot contain duplicate labels on the same artifact.")
        seen_labels.add(label_key)

        field_type = normalize_custom_field_type(item.get("type"))
        value = clean_metadata_text(item.get("value"), "custom field value", value_limit_for_type(field_type), required=False)
        unit = clean_metadata_text(item.get("unit"), "custom field unit", METADATA_UNIT_LIMIT, required=False)

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


def parse_metadata_sections(raw_value: str | None, *, partial: bool) -> list[dict[str, Any]] | None:
    if raw_value is None:
        return None if partial else []

    stripped = raw_value.strip()
    if not stripped:
        return []

    try:
        decoded = json.loads(stripped)
    except json.JSONDecodeError as exc:
        raise field_error("metadata_sections must be a JSON array.") from exc

    if not isinstance(decoded, list):
        raise field_error("metadata_sections must be a JSON array.")

    sections: list[dict[str, Any]] = []
    seen_section_ids: set[str] = set()
    for section_index, item in enumerate(decoded):
        if not isinstance(item, dict):
            raise field_error(f"metadata_sections item {section_index + 1} must be an object.")

        section_id = clean_metadata_id(item.get("id"), "metadata section id")
        if section_id in seen_section_ids:
            raise field_error("metadata_sections cannot contain duplicate section ids.")
        seen_section_ids.add(section_id)

        title = clean_metadata_text(item.get("title"), "metadata section title", METADATA_SECTION_TITLE_LIMIT, required=True)
        fields_value = item.get("fields", [])
        if not isinstance(fields_value, list):
            raise field_error("metadata section fields must be a JSON array.")

        normalized_fields: list[dict[str, Any]] = []
        seen_field_ids: set[str] = set()
        for field_index, field in enumerate(fields_value):
            if not isinstance(field, dict):
                raise field_error(f"metadata field {field_index + 1} must be an object.")
            field_id = clean_metadata_id(field.get("id"), "metadata field id")
            if field_id in seen_field_ids:
                raise field_error("metadata section fields cannot contain duplicate ids.")
            seen_field_ids.add(field_id)

            field_type = normalize_custom_field_type(field.get("type"))
            label = clean_metadata_text(field.get("label"), "metadata field label", METADATA_LABEL_LIMIT, required=False)
            value = clean_metadata_text(field.get("value"), "metadata field value", value_limit_for_type(field_type), required=False)
            if value and not label:
                raise field_error("metadata field label is required when a value is provided.")
            unit = clean_metadata_text(field.get("unit"), "metadata field unit", METADATA_UNIT_LIMIT, required=False)
            if field_type == "number" and value:
                validate_custom_number(value)
            elif field_type != "number":
                unit = None

            normalized_fields.append(
                {
                    "id": field_id,
                    "label": label,
                    "value": value or "",
                    "type": field_type,
                    "unit": unit,
                    "order": normalized_order(field.get("order"), field_index),
                }
            )

        sections.append(
            {
                "id": section_id,
                "title": title,
                "order": normalized_order(item.get("order"), section_index),
                "fields": normalized_fields,
            }
        )

    return sorted(sections, key=lambda section: int(section.get("order", 0)))


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


def public_metadata_sections(document: dict[str, Any]) -> list[dict[str, Any]]:
    persisted_sections = sorted(
        list(document.get("metadata_sections") or []),
        key=lambda section: normalized_order(section.get("order"), 0),
    )
    sections_by_id = {str(section.get("id") or ""): section for section in persisted_sections}
    public_sections: list[dict[str, Any]] = []

    historical_fields = [
        public_section_field("Origin", document.get("origin")),
        public_section_field("Historical Period", document.get("historical_period")),
        *public_fields_for_section(sections_by_id.get("historical_details")),
    ]
    append_public_section(public_sections, "Historical Details", historical_fields)

    physical_fields = [
        public_section_field("Material", document.get("material")),
        public_section_field("Dimensions", document.get("dimensions")),
        public_section_field("Condition", document.get("condition")),
        *public_fields_for_section(sections_by_id.get("physical_details")),
    ]
    append_public_section(public_sections, "Physical Details", physical_fields)

    for section in persisted_sections:
        section_id = str(section.get("id") or "")
        if section_id in SYSTEM_METADATA_SECTION_IDS:
            continue
        append_public_section(public_sections, str(section.get("title") or "").strip(), public_fields_for_section(section))

    custom_fields = public_custom_fields(document.get("custom_fields"))
    append_public_section(
        public_sections,
        "Additional Information",
        [
            {
                "label": field["label"],
                "value": field["value"],
                "unit": field.get("unit"),
                "type": field["type"],
            }
            for field in custom_fields
        ],
    )
    return public_sections


def public_section_field(label: str, value: Any, *, field_type: str = "text", unit: str | None = None) -> dict[str, str | None]:
    return {"label": label, "value": str(value or "").strip(), "unit": unit, "type": field_type}


def public_fields_for_section(section: dict[str, Any] | None) -> list[dict[str, str | None]]:
    fields: list[dict[str, str | None]] = []
    for field in sorted(list((section or {}).get("fields") or []), key=lambda item: normalized_order(item.get("order"), 0)):
        label = str(field.get("label") or "").strip()
        value = str(field.get("value") or "").strip()
        if not label or not value:
            continue
        try:
            field_type = normalize_custom_field_type(field.get("type"), fallback="text")
        except HTTPException:
            field_type = "text"
        fields.append(
            {
                "label": label,
                "value": value,
                "unit": str(field.get("unit")).strip() if field.get("unit") else None,
                "type": field_type,
            }
        )
    return fields


def append_public_section(sections: list[dict[str, Any]], title: str, fields: list[dict[str, str | None]]) -> None:
    clean_fields = [field for field in fields if str(field.get("label") or "").strip() and str(field.get("value") or "").strip()]
    clean_title = title.strip()
    if clean_title and clean_fields:
        sections.append({"title": clean_title, "fields": clean_fields})


def clean_metadata_text(value: Any, field_name: str, limit: int, *, required: bool) -> str:
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
        raise field_error(text_limit_message(field_name))
    return stripped


def normalize_custom_field_type(value: Any, *, fallback: str | None = None) -> str:
    normalized = str(value or fallback or "text").strip().lower().replace("-", "_").replace(" ", "_")
    if normalized not in CUSTOM_FIELD_TYPES:
        raise field_error("custom field type must be one of: text, number, long_text, date.")
    return normalized


def clean_custom_field_id(value: Any) -> str:
    return clean_metadata_id(value, "custom field id")


def clean_metadata_id(value: Any, field_name: str) -> str:
    candidate = str(value or "").strip()
    if not candidate:
        return uuid4().hex
    reject_control_characters(candidate, field_name)
    if len(candidate) > METADATA_ID_LIMIT:
        raise field_error(text_limit_message(field_name))
    return candidate


def normalized_order(value: Any, fallback: int) -> int:
    if isinstance(value, bool):
        return fallback
    try:
        return int(value)
    except (TypeError, ValueError):
        return fallback


def value_limit_for_type(field_type: str) -> int:
    return LONG_METADATA_VALUE_LIMIT if field_type == "long_text" else SHORT_METADATA_VALUE_LIMIT


def text_limit_message(field_name: str) -> str:
    return f"{human_field_name(field_name)} contains more text than the supported limit."


def human_field_name(field_name: str) -> str:
    overrides = {
        "artifact_code": "Artifact code",
        "name": "Artifact name",
        "description": "Description",
        "category": "Category",
        "origin": "Origin",
        "historical_period": "Historical period",
        "material": "Material",
        "dimensions": "Dimensions",
        "condition": "Condition",
    }
    return overrides.get(field_name, field_name.replace("_", " ").capitalize())


def validate_custom_number(value: str) -> None:
    try:
        Decimal(value)
    except InvalidOperation as exc:
        raise field_error("number custom field values must be valid numbers.") from exc


def reject_control_characters(value: str, field_name: str) -> None:
    if any(
        (ord(character) < 32 and ord(character) not in SAFE_FORMATTING_CONTROLS)
        or ord(character) == 127
        or 128 <= ord(character) <= 159
        for character in value
    ):
        raise field_error(f"{field_name} contains unsupported control characters.")


def field_error(message: str) -> HTTPException:
    return HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=message)


def omit_none(values: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in values.items() if value is not None}
