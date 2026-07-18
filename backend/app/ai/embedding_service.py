from __future__ import annotations

import math
from contextlib import nullcontext
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
from typing import Any

from PIL import Image, UnidentifiedImageError

from app.ai.model_manager import OpenCLIPModelManager, get_model_manager
from app.config import Settings
from app.services.image_storage import FORMAT_TO_EXTENSION


class EmbeddingError(ValueError):
    pass


@dataclass(frozen=True)
class EmbeddingResult:
    vector: list[float]
    dimension: int


class OpenCLIPEmbeddingService:
    def __init__(self, settings: Settings, model_manager: OpenCLIPModelManager | None = None) -> None:
        self.settings = settings
        self.model_manager = model_manager or get_model_manager(settings)

    def embed_image(self, image_source: str | Path | bytes) -> EmbeddingResult:
        image = self._load_image(image_source)
        try:
            loaded = self.model_manager.get_model()
            prepared = loaded.preprocess(image)
            if hasattr(prepared, "unsqueeze"):
                prepared = prepared.unsqueeze(0)
            if hasattr(prepared, "to"):
                prepared = prepared.to(loaded.device)

            with self._inference_context():
                encoded = loaded.model.encode_image(prepared)

            vector = self._normalize(self._to_float_vector(encoded))
            result = EmbeddingResult(vector=vector, dimension=len(vector))
            self.model_manager.set_embedding_dimension(result.dimension)
            return result
        finally:
            image.close()

    def _load_image(self, image_source: str | Path | bytes) -> Image.Image:
        try:
            if isinstance(image_source, bytes):
                if not image_source:
                    raise EmbeddingError("Image bytes are empty.")
                with Image.open(BytesIO(image_source)) as image:
                    if image.format not in FORMAT_TO_EXTENSION:
                        raise EmbeddingError("Only JPEG, PNG, and WEBP images are supported.")
                    return image.convert("RGB")
            else:
                path = Path(image_source)
                if not path.is_file():
                    raise EmbeddingError(f"Image file was not found: {path}")
                if path.stat().st_size == 0:
                    raise EmbeddingError("Image file is empty.")
                with Image.open(path) as image:
                    if image.format not in FORMAT_TO_EXTENSION:
                        raise EmbeddingError("Only JPEG, PNG, and WEBP images are supported.")
                    return image.convert("RGB")
        except EmbeddingError:
            raise
        except (UnidentifiedImageError, OSError) as exc:
            raise EmbeddingError("Image could not be decoded.") from exc

    def _inference_context(self):
        try:
            import torch
        except ImportError:
            return nullcontext()
        return torch.inference_mode()

    def _to_float_vector(self, encoded: Any) -> list[float]:
        value = encoded
        for method_name in ("detach", "cpu", "flatten"):
            method = getattr(value, method_name, None)
            if callable(method):
                value = method()
        tolist = getattr(value, "tolist", None)
        if callable(tolist):
            value = tolist()
        if isinstance(value, list) and len(value) == 1 and isinstance(value[0], list):
            value = value[0]
        if not isinstance(value, (list, tuple)):
            raise EmbeddingError("OpenCLIP output could not be converted to a vector.")

        vector: list[float] = []
        for item in value:
            if isinstance(item, (list, tuple)):
                vector.extend(float(child) for child in item)
            else:
                vector.append(float(item))
        if not vector:
            raise EmbeddingError("OpenCLIP returned an empty embedding.")
        return vector

    def _normalize(self, vector: list[float]) -> list[float]:
        if not all(math.isfinite(value) for value in vector):
            raise EmbeddingError("Embedding contains non-finite values.")

        magnitude = math.sqrt(sum(value * value for value in vector))
        if not math.isfinite(magnitude) or magnitude <= 0:
            raise EmbeddingError("Embedding magnitude is invalid.")

        normalized = [float(value / magnitude) for value in vector]
        normalized_magnitude = math.sqrt(sum(value * value for value in normalized))
        if abs(normalized_magnitude - 1.0) > 1e-5:
            raise EmbeddingError("Embedding could not be normalized.")
        return normalized
