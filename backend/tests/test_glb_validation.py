from __future__ import annotations

import trimesh

import pytest

from app.services.model3d.glb_validation import GlbValidationError, validate_external_glb


def _textured_box_glb_bytes() -> bytes:
    """A GLB that already has a real, explicit material - representative of what an AI provider
    (which textures its output) actually returns. Used to prove validate_external_glb() does not
    touch/re-export a GLB that is already correct."""
    mesh = trimesh.creation.box(extents=(1, 1, 1))
    mesh.visual = trimesh.visual.texture.TextureVisuals(
        material=trimesh.visual.material.PBRMaterial(
            baseColorFactor=[200, 150, 100, 255], metallicFactor=0.0, roughnessFactor=0.8
        )
    )
    _ = mesh.vertex_normals  # populate the cache so the export includes NORMAL, like a real complete asset
    return mesh.export(file_type="glb")


def _materialless_box_glb_bytes() -> bytes:
    """A GLB with real geometry but no material at all - the exact shape of the bug this
    validator repairs for COLMAP output, reproduced here to prove the repair path is also
    applied (defensively) to external GLBs that happen to arrive in this state."""
    mesh = trimesh.creation.box(extents=(1, 1, 1))
    return mesh.export(file_type="glb")


def test_properly_textured_glb_is_accepted_without_modification(tmp_path):
    path = tmp_path / "model.glb"
    path.write_bytes(_textured_box_glb_bytes())
    original_bytes = path.read_bytes()

    result = validate_external_glb(path)

    assert result.vertex_count > 0
    assert result.face_count > 0
    assert len(result.sha256) == 64
    assert result.repaired_material_or_normals is False
    # The file on disk must be byte-for-byte untouched - never damage a properly textured AI GLB.
    assert path.read_bytes() == original_bytes


def test_glb_missing_material_is_repaired_and_still_validates(tmp_path):
    path = tmp_path / "model.glb"
    path.write_bytes(_materialless_box_glb_bytes())

    result = validate_external_glb(path)

    assert result.vertex_count > 0
    assert result.face_count > 0
    assert result.repaired_material_or_normals is True
    # Re-opening the repaired file must show a real material now, not just cached in memory.
    reloaded = trimesh.load(str(path), file_type="glb", force="mesh", process=False)
    assert getattr(reloaded.visual, "material", None) is not None


def test_missing_file_is_rejected(tmp_path):
    with pytest.raises(GlbValidationError):
        validate_external_glb(tmp_path / "does-not-exist.glb")


def test_empty_file_is_rejected(tmp_path):
    path = tmp_path / "empty.glb"
    path.write_bytes(b"")
    with pytest.raises(GlbValidationError):
        validate_external_glb(path)


def test_garbage_bytes_are_rejected(tmp_path):
    path = tmp_path / "garbage.glb"
    path.write_bytes(b"this is not a glb file at all, just some bytes")
    with pytest.raises(GlbValidationError):
        validate_external_glb(path)
