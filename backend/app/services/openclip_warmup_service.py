from __future__ import annotations

import logging
import threading
import time
from concurrent.futures import Executor, Future, ThreadPoolExecutor
from dataclasses import dataclass
from datetime import datetime, timezone
from io import BytesIO

from PIL import Image

from app.ai.embedding_service import OpenCLIPEmbeddingService
from app.ai.model_manager import OpenCLIPModelManager, get_model_manager
from app.config import Settings


logger = logging.getLogger(__name__)

WARMUP_IDLE = "idle"
WARMUP_LOADING = "loading"
WARMUP_LOADED = "loaded"
WARMUP_FAILED = "failed"

SAFE_WARMUP_FAILURE = "OpenCLIP could not be loaded. Check the backend logs and AI setup, then retry."


@dataclass(frozen=True)
class OpenCLIPWarmupSnapshot:
    state: str
    message: str
    model_name: str
    pretrained: str
    device: str | None
    embedding_dimension: int | None
    started_at: datetime | None = None
    completed_at: datetime | None = None
    duration_seconds: float | None = None
    error: str | None = None


class OpenCLIPWarmupService:
    def __init__(
        self,
        settings: Settings,
        *,
        model_manager: OpenCLIPModelManager | None = None,
        embedding_service_factory=OpenCLIPEmbeddingService,
        executor: Executor | None = None,
    ) -> None:
        self.settings = settings
        self.model_manager = model_manager or get_model_manager(settings)
        self.embedding_service_factory = embedding_service_factory
        self._executor = executor or ThreadPoolExecutor(max_workers=1, thread_name_prefix="openclip-warmup")
        self._lock = threading.Lock()
        self._future: Future | None = None
        self._state = WARMUP_LOADED if self.model_manager.is_loaded else WARMUP_IDLE
        self._started_at: datetime | None = None
        self._completed_at: datetime | None = None
        self._duration_seconds: float | None = None
        self._error: str | None = None

    def start(self) -> OpenCLIPWarmupSnapshot:
        should_submit = False
        with self._lock:
            if not self.settings.ai_enabled:
                self._state = WARMUP_IDLE
                return self._snapshot_locked(message="AI recognition is disabled.")

            if self.model_manager.is_loaded:
                self._mark_loaded_locked(
                    started_at=self._started_at,
                    completed_at=self._completed_at or utc_now(),
                    duration_seconds=self._duration_seconds,
                )
                return self._snapshot_locked()

            if self._state == WARMUP_LOADING:
                return self._snapshot_locked()

            self._state = WARMUP_LOADING
            self._started_at = utc_now()
            self._completed_at = None
            self._duration_seconds = None
            self._error = None
            should_submit = True

        if should_submit:
            future = self._executor.submit(self._run_warmup)
            with self._lock:
                self._future = future
        return self.status()

    def status(self) -> OpenCLIPWarmupSnapshot:
        with self._lock:
            if self.model_manager.is_loaded and self._state != WARMUP_LOADING:
                self._mark_loaded_locked(
                    started_at=self._started_at,
                    completed_at=self._completed_at or utc_now(),
                    duration_seconds=self._duration_seconds,
                )
            return self._snapshot_locked()

    def _run_warmup(self) -> None:
        started_perf = time.perf_counter()
        started_at = self._started_at or utc_now()
        try:
            image_bytes = self._create_in_memory_image_bytes()
            service = self.embedding_service_factory(self.settings, model_manager=self.model_manager)
            embedding = service.embed_image(image_bytes)
            completed_at = utc_now()
            duration_seconds = round(time.perf_counter() - started_perf, 3)
            with self._lock:
                self.model_manager.set_embedding_dimension(embedding.dimension)
                self._mark_loaded_locked(
                    started_at=started_at,
                    completed_at=completed_at,
                    duration_seconds=duration_seconds,
                )
        except Exception:
            logger.exception("OpenCLIP warmup failed.")
            completed_at = utc_now()
            duration_seconds = round(time.perf_counter() - started_perf, 3)
            with self._lock:
                self._state = WARMUP_FAILED
                self._completed_at = completed_at
                self._duration_seconds = duration_seconds
                self._error = SAFE_WARMUP_FAILURE

    def _mark_loaded_locked(
        self,
        *,
        started_at: datetime | None,
        completed_at: datetime,
        duration_seconds: float | None,
    ) -> None:
        self._state = WARMUP_LOADED
        self._started_at = started_at
        self._completed_at = completed_at
        self._duration_seconds = duration_seconds
        self._error = None

    def _snapshot_locked(self, message: str | None = None) -> OpenCLIPWarmupSnapshot:
        loaded = self.model_manager.loaded_model
        dimension = self.model_manager.embedding_dimension
        if dimension is None and loaded is not None:
            dimension = loaded.embedding_dimension

        return OpenCLIPWarmupSnapshot(
            state=self._state,
            message=message or self._message_for_state(),
            model_name=self.settings.openclip_model_name,
            pretrained=self.settings.openclip_pretrained,
            device=loaded.device if loaded is not None else self.settings.openclip_device,
            embedding_dimension=dimension,
            started_at=self._started_at,
            completed_at=self._completed_at,
            duration_seconds=self._duration_seconds,
            error=self._error,
        )

    def _message_for_state(self) -> str:
        if self._state == WARMUP_LOADING:
            return "OpenCLIP is loading."
        if self._state == WARMUP_LOADED:
            return "OpenCLIP is ready."
        if self._state == WARMUP_FAILED:
            return SAFE_WARMUP_FAILURE
        return "OpenCLIP is ready to load."

    def _create_in_memory_image_bytes(self) -> bytes:
        buffer = BytesIO()
        with Image.new("RGB", (32, 32), color=(128, 128, 128)) as image:
            image.save(buffer, format="PNG")
        return buffer.getvalue()


_warmup_cache: dict[tuple[str, str, str, bool], OpenCLIPWarmupService] = {}
_warmup_cache_lock = threading.Lock()


def get_openclip_warmup_service(settings: Settings) -> OpenCLIPWarmupService:
    key = (
        settings.openclip_model_name,
        settings.openclip_pretrained,
        settings.openclip_device,
        settings.ai_model_download_allowed,
    )
    with _warmup_cache_lock:
        service = _warmup_cache.get(key)
        if service is None:
            service = OpenCLIPWarmupService(settings)
            _warmup_cache[key] = service
        return service


def utc_now() -> datetime:
    return datetime.now(timezone.utc)
