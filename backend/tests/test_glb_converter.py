from __future__ import annotations

import numpy as np
import pytest
import trimesh
from PIL import Image

from app.services.model3d.glb_converter import GlbConversionError, convert_to_glb


def _colored_box() -> trimesh.Trimesh:
    mesh = trimesh.creation.box(extents=(1, 1, 1))
    colors = np.tile([200, 50, 50, 255], (len(mesh.vertices), 1)).astype("uint8")
    mesh.visual.vertex_colors = colors
    return mesh


def test_vertex_colored_ply_converts_to_valid_glb(tmp_path):
    output_dir = tmp_path / "output"
    output_dir.mkdir()
    _colored_box().export(output_dir / "mesh.ply")

    destination = tmp_path / "model-v1.glb"
    result = convert_to_glb(output_dir, destination, simplify_ratio=None, max_glb_mb=50)

    assert destination.is_file()
    assert destination.stat().st_size == result.size_bytes
    assert result.size_bytes > 0
    assert result.vertex_count > 0
    assert result.face_count > 0
    assert result.has_texture is True
    assert len(result.sha256) == 64

    reloaded = trimesh.load(str(destination), file_type="glb", force="mesh", process=False)
    assert len(reloaded.vertices) == result.vertex_count
    assert len(reloaded.faces) == result.face_count


def test_uv_textured_obj_converts_to_valid_glb(tmp_path):
    output_dir = tmp_path / "output"
    output_dir.mkdir()

    mesh = trimesh.creation.box(extents=(1, 1, 1))
    uv = np.random.default_rng(0).random((len(mesh.vertices), 2))
    image = Image.new("RGB", (4, 4), color=(10, 200, 30))
    mesh.visual = trimesh.visual.TextureVisuals(uv=uv, image=image)
    mesh.export(output_dir / "mesh.obj")

    # trimesh's OBJ exporter names the material file after the material, not "texture.png" -
    # normalize it to the input shape this converter expects (mesh.obj + texture.png).
    mtl_path = output_dir / "material.mtl"
    mtl_text = mtl_path.read_text().replace("material_0.png", "texture.png")
    mtl_path.write_text(mtl_text)
    (output_dir / "material_0.png").rename(output_dir / "texture.png")

    destination = tmp_path / "model-v1.glb"
    result = convert_to_glb(output_dir, destination, simplify_ratio=None, max_glb_mb=50)

    assert destination.is_file()
    assert result.vertex_count > 0
    assert result.face_count > 0


_EMPTY_PLY = """ply
format ascii 1.0
element vertex 0
property float x
property float y
property float z
element face 0
property list uchar int vertex_indices
end_header
"""


def test_zero_geometry_mesh_is_rejected(tmp_path):
    output_dir = tmp_path / "output"
    output_dir.mkdir()
    (output_dir / "mesh.ply").write_text(_EMPTY_PLY)

    with pytest.raises(GlbConversionError):
        convert_to_glb(output_dir, tmp_path / "model-v1.glb", simplify_ratio=None, max_glb_mb=50)


def test_missing_mesh_output_raises(tmp_path):
    output_dir = tmp_path / "output"
    output_dir.mkdir()

    with pytest.raises(GlbConversionError):
        convert_to_glb(output_dir, tmp_path / "model-v1.glb", simplify_ratio=None, max_glb_mb=50)


def test_simplification_reduces_face_count(tmp_path):
    output_dir = tmp_path / "output"
    output_dir.mkdir()
    mesh = trimesh.creation.icosphere(subdivisions=2)
    colors = np.tile([100, 150, 200, 255], (len(mesh.vertices), 1)).astype("uint8")
    mesh.visual.vertex_colors = colors
    original_face_count = len(mesh.faces)
    mesh.export(output_dir / "mesh.ply")

    result = convert_to_glb(output_dir, tmp_path / "model-v1.glb", simplify_ratio=0.25, max_glb_mb=50)

    assert result.face_count < original_face_count
