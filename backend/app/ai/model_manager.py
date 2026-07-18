from __future__ import annotations

import importlib.util
import os
import threading
from dataclasses import dataclass
from typing import Any

from app.config import Settings


class AIModelError(RuntimeError):
    pass


@dataclass(frozen=True)
class LoadedOpenCLIPModel:
    model: Any
    preprocess: Any
    device: str
    model_name: str
    pretrained: str
    embedding_dimension: int | None = None


def dependencies_available() -> bool:
    return importlib.util.find_spec("torch") is not None and importlib.util.find_spec("open_clip") is not None


class OpenCLIPModelManager:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self._lock = threading.Lock()
        self._loaded: LoadedOpenCLIPModel | None = None

    @property
    def is_loaded(self) -> bool:
        return self._loaded is not None

    @property
    def loaded_model(self) -> LoadedOpenCLIPModel | None:
        return self._loaded

    @property
    def actual_device(self) -> str | None:
        return self._loaded.device if self._loaded else None

    @property
    def embedding_dimension(self) -> int | None:
        return self._loaded.embedding_dimension if self._loaded else None

    def set_embedding_dimension(self, dimension: int) -> None:
        if self._loaded is None:
            return
        if self._loaded.embedding_dimension == dimension:
            return
        self._loaded = LoadedOpenCLIPModel(
            model=self._loaded.model,
            preprocess=self._loaded.preprocess,
            device=self._loaded.device,
            model_name=self._loaded.model_name,
            pretrained=self._loaded.pretrained,
            embedding_dimension=dimension,
        )

    def get_model(self) -> LoadedOpenCLIPModel:
        if self._loaded is not None:
            return self._loaded

        with self._lock:
            if self._loaded is None:
                self._loaded = self._load_model()
        return self._loaded

    def _load_model(self) -> LoadedOpenCLIPModel:
        try:
            import open_clip
            import torch
        except ImportError as exc:
            raise AIModelError(
                "OpenCLIP dependencies are not installed. Run `python start_backend.py --setup-ai` first."
            ) from exc

        requested_device = self.settings.openclip_device
        device = self._select_device(torch, requested_device)
        if not self.settings.ai_model_download_allowed:
            os.environ.setdefault("HF_HUB_OFFLINE", "1")

        print(
            f"[INFO] Loading OpenCLIP model {self.settings.openclip_model_name} "
            f"({self.settings.openclip_pretrained}) on {device}.",
            flush=True,
        )
        if self.settings.ai_model_download_allowed:
            print("[INFO] The first model load may download weights into the normal user cache.", flush=True)

        try:
            model, _, preprocess = open_clip.create_model_and_transforms(
                self.settings.openclip_model_name,
                pretrained=self.settings.openclip_pretrained,
                device=device,
            )
            model.eval()
        except Exception as exc:
            if requested_device == "auto" and device == "cuda":
                print("[WARN] CUDA model load failed. Falling back to CPU.", flush=True)
                return self._load_model_on_cpu(open_clip)
            message = "OpenCLIP model could not be loaded."
            if not self.settings.ai_model_download_allowed:
                message += " Downloads are disabled, so verify that the model weights are already cached."
            raise AIModelError(message) from exc

        return LoadedOpenCLIPModel(
            model=model,
            preprocess=preprocess,
            device=device,
            model_name=self.settings.openclip_model_name,
            pretrained=self.settings.openclip_pretrained,
            embedding_dimension=self._read_embedding_dimension(model),
        )

    def _load_model_on_cpu(self, open_clip_module: Any) -> LoadedOpenCLIPModel:
        try:
            model, _, preprocess = open_clip_module.create_model_and_transforms(
                self.settings.openclip_model_name,
                pretrained=self.settings.openclip_pretrained,
                device="cpu",
            )
            model.eval()
        except Exception as exc:
            raise AIModelError("OpenCLIP model could not be loaded on CPU.") from exc

        return LoadedOpenCLIPModel(
            model=model,
            preprocess=preprocess,
            device="cpu",
            model_name=self.settings.openclip_model_name,
            pretrained=self.settings.openclip_pretrained,
            embedding_dimension=self._read_embedding_dimension(model),
        )

    def _select_device(self, torch_module: Any, requested_device: str) -> str:
        if requested_device == "cpu":
            return "cpu"
        cuda_available = bool(torch_module.cuda.is_available())
        if requested_device == "cuda":
            if not cuda_available:
                raise AIModelError("OPENCLIP_DEVICE=cuda was requested, but CUDA is not available.")
            return "cuda"
        return "cuda" if cuda_available else "cpu"

    def _read_embedding_dimension(self, model: Any) -> int | None:
        for source in (model, getattr(model, "visual", None)):
            if source is None:
                continue
            for attr in ("output_dim", "embed_dim"):
                value = getattr(source, attr, None)
                if isinstance(value, int) and value > 0:
                    return value
        projection = getattr(model, "text_projection", None)
        shape = getattr(projection, "shape", None)
        if shape and len(shape) >= 2 and int(shape[-1]) > 0:
            return int(shape[-1])
        return None


_manager_cache: dict[tuple[str, str, str, bool], OpenCLIPModelManager] = {}
_manager_cache_lock = threading.Lock()


def get_model_manager(settings: Settings) -> OpenCLIPModelManager:
    key = (
        settings.openclip_model_name,
        settings.openclip_pretrained,
        settings.openclip_device,
        settings.ai_model_download_allowed,
    )
    with _manager_cache_lock:
        manager = _manager_cache.get(key)
        if manager is None:
            manager = OpenCLIPModelManager(settings)
            _manager_cache[key] = manager
        return manager
