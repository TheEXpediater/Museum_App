from __future__ import annotations

import argparse
import math
import tempfile
from pathlib import Path

from PIL import Image

from app.ai.embedding_service import OpenCLIPEmbeddingService
from app.ai.model_manager import get_model_manager
from app.config import get_settings


def create_test_image() -> Path:
    handle = tempfile.NamedTemporaryFile(delete=False, suffix=".png")
    handle.close()
    path = Path(handle.name)
    Image.new("RGB", (32, 32), color=(130, 80, 160)).save(path, format="PNG")
    return path


def magnitude(vector: list[float]) -> float:
    return math.sqrt(sum(value * value for value in vector))


def max_delta(first: list[float], second: list[float]) -> float:
    return max(abs(left - right) for left, right in zip(first, second))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate an OpenCLIP image embedding safely.")
    parser.add_argument("image", nargs="?", help="Optional path to a JPEG, PNG, or WEBP image.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    settings = get_settings()
    manager = get_model_manager(settings)
    service = OpenCLIPEmbeddingService(settings, model_manager=manager)

    temporary_image: Path | None = None
    image_path = Path(args.image) if args.image else None
    if image_path is None:
        temporary_image = create_test_image()
        image_path = temporary_image

    try:
        first = service.embed_image(image_path)
        print(f"[OK] Model: {settings.openclip_model_name}")
        print(f"[OK] Pretrained: {settings.openclip_pretrained}")
        print(f"[OK] Device: {manager.actual_device}")
        print(f"[OK] Embedding dimension: {first.dimension}")
        print(f"[OK] Embedding magnitude: {magnitude(first.vector):.6f}")

        if args.image:
            print("[OK] Embedding generated for supplied image")
            return 0

        second = service.embed_image(image_path)
        if first.dimension != second.dimension:
            print("[FAIL] Embedding dimensions did not match.")
            return 1
        if abs(magnitude(first.vector) - 1.0) > 1e-5 or abs(magnitude(second.vector) - 1.0) > 1e-5:
            print("[FAIL] Embeddings are not normalized.")
            return 1
        if max_delta(first.vector, second.vector) > 1e-5:
            print("[FAIL] Repeated embeddings were not numerically stable.")
            return 1
        print("[OK] Repeated embeddings are consistent")
        return 0
    finally:
        if temporary_image is not None:
            temporary_image.unlink(missing_ok=True)


if __name__ == "__main__":
    raise SystemExit(main())
