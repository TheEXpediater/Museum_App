from __future__ import annotations

import pytest

from app.config import Settings


JWT_SECRET = "test-secret-key-that-is-long-enough"


def make_settings(**overrides) -> Settings:
    values = {
        "mongodb_url": "mongodb://localhost:27017",
        "mongodb_database": "museum_guide_test",
        "jwt_secret_key": JWT_SECRET,
        "_env_file": None,
    }
    values.update(overrides)
    return Settings(**values)


def test_ai_settings_load_defaults():
    settings = make_settings()
    assert settings.ai_enabled is True
    assert settings.qdrant_url == "http://localhost:6333"
    assert settings.qdrant_collection == "artifact_images"
    assert settings.qdrant_distance == "cosine"
    assert settings.openclip_model_name == "ViT-B-32"
    assert settings.openclip_device == "auto"


def test_ai_device_values_are_validated():
    assert make_settings(openclip_device="CPU").openclip_device == "cpu"
    assert make_settings(openclip_device="cuda").openclip_device == "cuda"
    with pytest.raises(ValueError):
        make_settings(openclip_device="metal")


def test_invalid_distance_is_rejected():
    with pytest.raises(ValueError):
        make_settings(qdrant_distance="banana")


def test_blank_collection_name_is_rejected():
    with pytest.raises(ValueError):
        make_settings(qdrant_collection=" ")


def test_blank_qdrant_api_key_becomes_none():
    assert make_settings(qdrant_api_key="").qdrant_api_key is None
