from functools import lru_cache
from pathlib import Path
from typing import Any

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


BACKEND_DIR = Path(__file__).resolve().parents[1]
ENV_FILE = BACKEND_DIR / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=ENV_FILE,
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
    )

    app_name: str = Field(default="Museum Guide System", alias="APP_NAME")
    app_env: str = Field(default="development", alias="APP_ENV")
    mongodb_url: str = Field(alias="MONGODB_URL")
    mongodb_database: str = Field(alias="MONGODB_DATABASE")
    jwt_secret_key: str = Field(alias="JWT_SECRET_KEY")
    jwt_algorithm: str = Field(default="HS256", alias="JWT_ALGORITHM")
    jwt_access_token_expire_minutes: int = Field(default=480, alias="JWT_ACCESS_TOKEN_EXPIRE_MINUTES")
    upload_directory: str = Field(default="uploads/images", alias="UPLOAD_DIRECTORY")
    max_image_size_mb: int = Field(default=10, alias="MAX_IMAGE_SIZE_MB")
    cors_origins: str = Field(
        default="http://localhost,http://localhost:8080,http://10.0.2.2",
        alias="CORS_ORIGINS",
    )
    admin_email: str | None = Field(default=None, alias="ADMIN_EMAIL")
    admin_password: str | None = Field(default=None, alias="ADMIN_PASSWORD")
    admin_full_name: str = Field(default="Museum Administrator", alias="ADMIN_FULL_NAME")
    ai_enabled: bool = Field(default=True, alias="AI_ENABLED")
    qdrant_url: str = Field(default="http://localhost:6333", alias="QDRANT_URL")
    qdrant_api_key: str | None = Field(default=None, alias="QDRANT_API_KEY")
    qdrant_collection: str = Field(default="artifact_images", alias="QDRANT_COLLECTION")
    qdrant_distance: str = Field(default="cosine", alias="QDRANT_DISTANCE")
    openclip_model_name: str = Field(default="ViT-B-32", alias="OPENCLIP_MODEL_NAME")
    openclip_pretrained: str = Field(default="laion2b_s34b_b79k", alias="OPENCLIP_PRETRAINED")
    openclip_device: str = Field(default="auto", alias="OPENCLIP_DEVICE")
    ai_model_download_allowed: bool = Field(default=True, alias="AI_MODEL_DOWNLOAD_ALLOWED")

    @field_validator(
        "mongodb_url",
        "mongodb_database",
        "jwt_secret_key",
        "upload_directory",
        "qdrant_url",
        "qdrant_collection",
        "openclip_model_name",
        "openclip_pretrained",
    )
    @classmethod
    def required_non_empty(cls, value: str) -> str:
        if not value or not value.strip():
            raise ValueError("configuration value is required")
        return value.strip()

    @field_validator("qdrant_api_key", mode="before")
    @classmethod
    def blank_secret_to_none(cls, value: Any) -> str | None:
        if value is None:
            return None
        if isinstance(value, str) and not value.strip():
            return None
        return str(value).strip()

    @field_validator("qdrant_distance")
    @classmethod
    def validate_qdrant_distance(cls, value: str) -> str:
        normalized = value.strip().lower()
        if normalized not in {"cosine", "dot", "euclid"}:
            raise ValueError("QDRANT_DISTANCE must be one of: cosine, dot, euclid")
        return normalized

    @field_validator("openclip_device")
    @classmethod
    def validate_openclip_device(cls, value: str) -> str:
        normalized = value.strip().lower()
        if normalized not in {"auto", "cpu", "cuda"}:
            raise ValueError("OPENCLIP_DEVICE must be one of: auto, cpu, cuda")
        return normalized

    @field_validator("jwt_secret_key")
    @classmethod
    def secret_must_not_be_placeholder(cls, value: str) -> str:
        if value == "replace_with_a_long_random_secret":
            raise ValueError("JWT_SECRET_KEY must be changed from the example placeholder")
        if len(value) < 24:
            raise ValueError("JWT_SECRET_KEY must be at least 24 characters")
        return value

    @field_validator("max_image_size_mb")
    @classmethod
    def image_size_must_be_positive(cls, value: int) -> int:
        if value <= 0:
            raise ValueError("MAX_IMAGE_SIZE_MB must be greater than zero")
        return value

    @field_validator("jwt_access_token_expire_minutes")
    @classmethod
    def token_expiry_must_be_positive(cls, value: int) -> int:
        if value <= 0:
            raise ValueError("JWT_ACCESS_TOKEN_EXPIRE_MINUTES must be greater than zero")
        return value

    @property
    def parsed_cors_origins(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]

    @property
    def upload_path(self) -> Path:
        path = Path(self.upload_directory).expanduser()
        if not path.is_absolute():
            path = BACKEND_DIR / path
        return path.resolve()

    @property
    def upload_root_path(self) -> Path:
        upload_path = self.upload_path
        if upload_path.name == "images":
            return upload_path.parent
        return upload_path


@lru_cache
def get_settings() -> Settings:
    return Settings()
