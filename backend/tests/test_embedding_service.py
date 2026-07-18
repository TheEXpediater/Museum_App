from __future__ import annotations

from io import BytesIO
from types import SimpleNamespace

import pytest
from PIL import Image

from app.ai.embedding_service import EmbeddingError, OpenCLIPEmbeddingService
from app.config import Settings


JWT_SECRET = "test-secret-key-that-is-long-enough"


class FakePrepared:
    def unsqueeze(self, _: int):
        return self

    def to(self, _: str):
        return self


class FakeModel:
    def __init__(self, vector):
        self.vector = vector
        self.calls = 0

    def encode_image(self, _):
        self.calls += 1
        return self.vector


class FakeManager:
    def __init__(self, vector):
        self.model = FakeModel(vector)
        self.loaded = SimpleNamespace(
            model=self.model,
            preprocess=self.preprocess,
            device="cpu",
            model_name="fake",
            pretrained="fake",
            embedding_dimension=None,
        )
        self.dimension = None

    def preprocess(self, image):
        assert image.mode == "RGB"
        return FakePrepared()

    def get_model(self):
        return self.loaded

    def set_embedding_dimension(self, dimension: int) -> None:
        self.dimension = dimension


def make_settings() -> Settings:
    return Settings(
        mongodb_url="mongodb://localhost:27017",
        mongodb_database="museum_guide_test",
        jwt_secret_key=JWT_SECRET,
        _env_file=None,
    )


def image_bytes(format_name: str = "PNG", mode: str = "RGB") -> bytes:
    buffer = BytesIO()
    Image.new(mode, (16, 16), color=120).save(buffer, format=format_name)
    return buffer.getvalue()


def test_valid_image_generates_normalized_embedding():
    manager = FakeManager([3.0, 4.0])
    result = OpenCLIPEmbeddingService(make_settings(), manager).embed_image(image_bytes())
    assert result.dimension == 2
    assert result.vector == pytest.approx([0.6, 0.8])
    assert manager.dimension == 2


def test_empty_image_is_rejected():
    with pytest.raises(EmbeddingError):
        OpenCLIPEmbeddingService(make_settings(), FakeManager([1.0])).embed_image(b"")


def test_invalid_image_is_rejected():
    with pytest.raises(EmbeddingError):
        OpenCLIPEmbeddingService(make_settings(), FakeManager([1.0])).embed_image(b"not an image")


def test_image_is_converted_to_rgb():
    result = OpenCLIPEmbeddingService(make_settings(), FakeManager([1.0, 0.0])).embed_image(
        image_bytes(format_name="PNG", mode="L")
    )
    assert result.dimension == 2


def test_nonfinite_embedding_is_rejected():
    with pytest.raises(EmbeddingError):
        OpenCLIPEmbeddingService(make_settings(), FakeManager([1.0, float("nan")])).embed_image(image_bytes())


def test_model_manager_is_reused_for_service(monkeypatch):
    manager = FakeManager([1.0, 0.0])
    calls = {"count": 0}

    def fake_factory(_settings):
        calls["count"] += 1
        return manager

    monkeypatch.setattr("app.ai.embedding_service.get_model_manager", fake_factory)
    service = OpenCLIPEmbeddingService(make_settings())
    service.embed_image(image_bytes())
    service.embed_image(image_bytes())
    assert calls["count"] == 1
    assert manager.model.calls == 2
