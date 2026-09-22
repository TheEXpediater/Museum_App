from functools import lru_cache
from pathlib import Path
from typing import Any

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


BACKEND_DIR = Path(__file__).resolve().parents[1]
ENV_FILE = BACKEND_DIR / ".env"
# tools/triposr lives at the repository root (sibling of backend/), not under BACKEND_DIR - it is
# a deliberately isolated runtime, never inside the FastAPI/OpenCLIP venv.
REPO_ROOT_DIR = BACKEND_DIR.parent


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
    guest_session_expire_hours: int = Field(default=24, alias="GUEST_SESSION_EXPIRE_HOURS")
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
    ai_warmup_on_startup: bool = Field(default=False, alias="AI_WARMUP_ON_STARTUP")
    ai_recognition_strong_threshold: float = Field(default=0.45, alias="AI_RECOGNITION_STRONG_THRESHOLD")
    ai_recognition_possible_threshold: float = Field(default=0.32, alias="AI_RECOGNITION_POSSIBLE_THRESHOLD")
    ai_recognition_max_results: int = Field(default=5, alias="AI_RECOGNITION_MAX_RESULTS")
    ai_recognition_vector_candidates: int = Field(default=25, alias="AI_RECOGNITION_VECTOR_CANDIDATES")

    model_3d_enabled: bool = Field(default=True, alias="MODEL_3D_ENABLED")
    colmap_bin: str = Field(default="colmap", alias="COLMAP_BIN")
    colmap_use_gpu: bool = Field(default=False, alias="COLMAP_USE_GPU")
    reconstruction_directory: str = Field(default="uploads/reconstruction", alias="RECONSTRUCTION_DIRECTORY")
    model_3d_directory: str = Field(default="uploads/models3d", alias="MODEL_3D_DIRECTORY")
    model_3d_min_source_images: int = Field(default=8, alias="MODEL_3D_MIN_SOURCE_IMAGES")
    model_3d_min_registered_ratio: float = Field(default=0.70, alias="MODEL_3D_MIN_REGISTERED_RATIO")
    # Low-resource preview profile defaults: this system targets an ~8GB RAM CPU-only laptop.
    # A real test on such a machine (7.8GB RAM, 4 cores) crashed COLMAP outright at full
    # resolution with automatic threading, so working copies are downscaled and threading is
    # capped rather than assuming a workstation-class machine.
    model_3d_max_input_dimension: int = Field(default=1280, alias="MODEL_3D_MAX_INPUT_DIMENSION")
    model_3d_cpu_threads: int = Field(default=1, alias="MODEL_3D_CPU_THREADS")
    model_3d_max_features: int = Field(default=4096, alias="MODEL_3D_MAX_FEATURES")
    model_3d_simplify_ratio: float = Field(default=0.25, alias="MODEL_3D_SIMPLIFY_RATIO")
    model_3d_max_glb_mb: int = Field(default=15, alias="MODEL_3D_MAX_GLB_MB")

    # AI multi-view 3D fallback (see app/services/model3d/ai_provider.py). Entirely optional -
    # when disabled or unconfigured, COLMAP-only operation is unaffected: existing museum
    # features, existing published models, and the COLMAP pipeline all keep working.
    ai_3d_enabled: bool = Field(default=False, alias="AI_3D_ENABLED")
    ai_3d_provider: str = Field(default="meshy", alias="AI_3D_PROVIDER")
    ai_3d_api_key: str | None = Field(default=None, alias="AI_3D_API_KEY")

    # Local, free, single-image AI 3D preview (TripoSR pretrained inference). Independent of the
    # paid ai_3d_* fallback above - this is the DEFAULT "Quick AI 3D Preview" path and does not
    # require an API key or COLMAP. Runs as an isolated subprocess (tools/triposr/), never inside
    # the FastAPI/OpenCLIP process or venv. See app/services/model3d/triposr_provider.py.
    local_ai_3d_enabled: bool = Field(default=True, alias="LOCAL_AI_3D_ENABLED")
    local_ai_3d_provider: str = Field(default="triposr", alias="LOCAL_AI_3D_PROVIDER")
    triposr_root: str = Field(default="tools/triposr", alias="TRIPOSR_ROOT")
    triposr_python: str = Field(default="", alias="TRIPOSR_PYTHON")
    triposr_model_path: str = Field(default="stabilityai/TripoSR", alias="TRIPOSR_MODEL_PATH")
    triposr_device: str = Field(default="auto", alias="TRIPOSR_DEVICE")
    triposr_chunk_size: int = Field(default=2048, alias="TRIPOSR_CHUNK_SIZE")
    triposr_mc_resolution: int = Field(default=192, alias="TRIPOSR_MC_RESOLUTION")
    triposr_texture_resolution: int = Field(default=1024, alias="TRIPOSR_TEXTURE_RESOLUTION")
    triposr_bake_texture: bool = Field(default=True, alias="TRIPOSR_BAKE_TEXTURE")
    triposr_cpu_fallback: bool = Field(default=True, alias="TRIPOSR_CPU_FALLBACK")
    triposr_timeout_seconds: int = Field(default=1200, alias="TRIPOSR_TIMEOUT_SECONDS")
    # When set, TripoSR generation is delegated to an isolated triposr-worker container over the
    # internal Docker network instead of a local subprocess (see
    # app/services/model3d/triposr_worker_client.py) - used on the VPS, where the heavy
    # PyTorch/transformers stack must stay out of the backend image. Empty (default) preserves the
    # existing local-subprocess behavior unchanged, so local development needs no changes at all.
    triposr_worker_url: str = Field(default="", alias="TRIPOSR_WORKER_URL")
    # Only meaningful in worker mode: a directory that resolves to the SAME shared Docker volume
    # in both the backend and triposr-worker containers, used to hand off the generated GLB
    # without streaming file bytes over HTTP (see triposr_worker_client.py). Must be an absolute
    # container path (e.g. /triposr-jobs), not the local tools/triposr/ layout.
    triposr_worker_jobs_dir: str = Field(default="", alias="TRIPOSR_WORKER_JOBS_DIR")
    # Background removal (rembg) downloads/loads a ~1GB ONNX segmentation model alongside
    # TripoSR's own model - proven by a real OOM kill (dmesg, anon-rss ~7.7GB) on this VPS's 8GB
    # RAM/4GB swap to be the tipping point over the safe ceiling. True (matching infer.py's own
    # default) everywhere except where a deployment's real measured memory requires disabling it.
    triposr_remove_background: bool = Field(default=True, alias="TRIPOSR_REMOVE_BACKGROUND")

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

    @field_validator("qdrant_api_key", "ai_3d_api_key", mode="before")
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

    @field_validator("ai_recognition_strong_threshold", "ai_recognition_possible_threshold")
    @classmethod
    def validate_recognition_threshold_range(cls, value: float) -> float:
        if value < -1.0 or value > 1.0:
            raise ValueError("recognition thresholds must be between -1.0 and 1.0")
        return value

    @field_validator("ai_recognition_max_results", "ai_recognition_vector_candidates")
    @classmethod
    def recognition_limits_must_be_positive(cls, value: int) -> int:
        if value <= 0:
            raise ValueError("recognition result limits must be greater than zero")
        return value

    @field_validator("ai_recognition_possible_threshold")
    @classmethod
    def validate_recognition_threshold_order(cls, value: float, info) -> float:
        strong = info.data.get("ai_recognition_strong_threshold")
        if strong is not None and strong <= value:
            raise ValueError("AI_RECOGNITION_STRONG_THRESHOLD must be greater than AI_RECOGNITION_POSSIBLE_THRESHOLD")
        return value

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
    def positive_integer_setting(cls, value: int) -> int:
        if value <= 0:
            raise ValueError("numeric configuration values must be greater than zero")
        return value

    @field_validator("jwt_access_token_expire_minutes")
    @classmethod
    def token_expiry_must_be_positive(cls, value: int) -> int:
        if value <= 0:
            raise ValueError("JWT_ACCESS_TOKEN_EXPIRE_MINUTES must be greater than zero")
        return value

    @field_validator("guest_session_expire_hours")
    @classmethod
    def guest_session_expiry_must_be_positive(cls, value: int) -> int:
        if value <= 0:
            raise ValueError("GUEST_SESSION_EXPIRE_HOURS must be greater than zero")
        return value

    @field_validator("model_3d_min_source_images")
    @classmethod
    def min_source_images_must_be_positive(cls, value: int) -> int:
        if value <= 0:
            raise ValueError("MODEL_3D_MIN_SOURCE_IMAGES must be greater than zero")
        return value

    @field_validator("model_3d_min_registered_ratio")
    @classmethod
    def registered_ratio_must_be_fractional(cls, value: float) -> float:
        if not 0.0 < value <= 1.0:
            raise ValueError("MODEL_3D_MIN_REGISTERED_RATIO must be between 0 and 1")
        return value

    @field_validator("model_3d_max_input_dimension", "model_3d_max_glb_mb", "model_3d_cpu_threads", "model_3d_max_features")
    @classmethod
    def model_3d_positive_integer(cls, value: int) -> int:
        if value <= 0:
            raise ValueError("value must be greater than zero")
        return value

    @field_validator("model_3d_simplify_ratio")
    @classmethod
    def simplify_ratio_must_be_fractional(cls, value: float) -> float:
        if not 0.0 < value <= 1.0:
            raise ValueError("MODEL_3D_SIMPLIFY_RATIO must be between 0 and 1")
        return value

    @field_validator("ai_3d_provider")
    @classmethod
    def normalize_ai_3d_provider(cls, value: str) -> str:
        normalized = (value or "").strip().lower()
        return normalized or "meshy"

    @field_validator("local_ai_3d_provider")
    @classmethod
    def normalize_local_ai_3d_provider(cls, value: str) -> str:
        normalized = (value or "").strip().lower()
        return normalized or "triposr"

    @field_validator("triposr_device")
    @classmethod
    def validate_triposr_device(cls, value: str) -> str:
        normalized = (value or "").strip().lower()
        if normalized not in {"auto", "cpu", "cuda"}:
            raise ValueError("TRIPOSR_DEVICE must be one of: auto, cpu, cuda")
        return normalized

    @field_validator("triposr_chunk_size", "triposr_mc_resolution", "triposr_texture_resolution", "triposr_timeout_seconds")
    @classmethod
    def triposr_positive_integer(cls, value: int) -> int:
        if value <= 0:
            raise ValueError("value must be greater than zero")
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

    def _resolved_backend_path(self, raw_path: str) -> Path:
        path = Path(raw_path).expanduser()
        if not path.is_absolute():
            path = BACKEND_DIR / path
        return path.resolve()

    @property
    def reconstruction_root_path(self) -> Path:
        return self._resolved_backend_path(self.reconstruction_directory)

    @property
    def model_3d_root_path(self) -> Path:
        return self._resolved_backend_path(self.model_3d_directory)

    @property
    def triposr_root_path(self) -> Path:
        path = Path(self.triposr_root).expanduser()
        if not path.is_absolute():
            path = REPO_ROOT_DIR / path
        return path.resolve()

    @property
    def triposr_worker_jobs_path(self) -> Path | None:
        """The shared job-output volume mount, worker mode only. None (not a fabricated default)
        when unset, so a misconfiguration fails loudly instead of silently writing/reading the
        wrong directory - see triposr_worker_client.py."""
        if not self.triposr_worker_jobs_dir:
            return None
        return Path(self.triposr_worker_jobs_dir).expanduser().resolve()

    @property
    def triposr_python_path(self) -> Path | None:
        """Resolves TRIPOSR_PYTHON if configured, else the venv Python conventionally created at
        <TRIPOSR_ROOT>/.venv/Scripts/python.exe. Returns None (not a fabricated path) if neither
        exists - see triposr_provider.detect()."""
        if self.triposr_python:
            path = Path(self.triposr_python).expanduser()
            if not path.is_absolute():
                path = REPO_ROOT_DIR / path
            return path.resolve()
        candidate = self.triposr_root_path / ".venv" / "Scripts" / "python.exe"
        return candidate if candidate.is_file() else None


@lru_cache
def get_settings() -> Settings:
    return Settings()
