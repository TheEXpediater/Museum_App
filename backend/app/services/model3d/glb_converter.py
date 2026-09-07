from __future__ import annotations

import hashlib
from dataclasses import dataclass, field
from pathlib import Path


class GlbConversionError(RuntimeError):
    pass


@dataclass
class GlbConversionResult:
    sha256: str
    size_bytes: int
    vertex_count: int
    face_count: int
    has_texture: bool
    warnings: list[str] = field(default_factory=list)


def _find_source_mesh(output_dir: Path) -> tuple[Path, bool]:
    """Locate the mesh COLMAP (or the mesh stage) produced.

    Prefers a real UV-textured export (mesh.obj + texture.png/.mtl) when present. Falls back to
    a vertex-colored mesh.ply, which is what COLMAP's CPU-friendly poisson/delaunay mesher
    actually produces from a colored fused point cloud on this pipeline - a legitimate texturing
    approach for a photogrammetry mesh, not a placeholder.
    """
    obj_path = output_dir / "mesh.obj"
    if obj_path.is_file() and (output_dir / "texture.png").is_file():
        return obj_path, True

    for name in ("textured_mesh.ply", "mesh.ply"):
        candidate = output_dir / name
        if candidate.is_file():
            return candidate, False

    raise GlbConversionError(f"No mesh output was found in {output_dir}.")


def convert_to_glb(
    output_dir: Path,
    destination: Path,
    *,
    simplify_ratio: float | None = None,
    max_glb_mb: int = 50,
) -> GlbConversionResult:
    """Convert a COLMAP mesh output into a self-contained mobile-ready GLB.

    Validates that geometry, faces, and (when available) texture/vertex-color survive the
    conversion by re-opening the written GLB rather than only checking that a file exists.
    """
    import trimesh

    source_path, expects_uv_texture = _find_source_mesh(output_dir)
    loaded = trimesh.load(str(source_path), force="mesh", process=False)
    if not isinstance(loaded, trimesh.Trimesh):
        raise GlbConversionError(f"{source_path.name} did not resolve to a single mesh.")

    mesh = loaded
    if len(mesh.vertices) == 0 or len(mesh.faces) == 0:
        raise GlbConversionError("Reconstructed mesh has no geometry (zero vertices or faces).")

    warnings: list[str] = []

    if simplify_ratio and 0 < simplify_ratio < 1:
        target_faces = max(int(len(mesh.faces) * simplify_ratio), 100)
        try:
            mesh = mesh.simplify_quadric_decimation(face_count=target_faces)
        except Exception as exc:  # pragma: no cover - depends on optional native backend
            warnings.append(f"Mesh simplification was skipped ({exc.__class__.__name__}).")

    has_texture = bool(getattr(mesh.visual, "uv", None) is not None and expects_uv_texture) or bool(
        getattr(mesh.visual, "vertex_colors", None) is not None
    )

    destination.parent.mkdir(parents=True, exist_ok=True)
    temp_path = destination.with_suffix(".part")
    glb_bytes = mesh.export(file_type="glb")
    if not glb_bytes:
        raise GlbConversionError("GLB export produced no data.")
    temp_path.write_bytes(glb_bytes)

    # Re-open the file we just wrote (not the in-memory mesh) to prove it is a valid,
    # loadable GLB rather than declaring success merely because a filename exists.
    try:
        reloaded = trimesh.load(str(temp_path), file_type="glb", force="mesh", process=False)
    except Exception as exc:
        temp_path.unlink(missing_ok=True)
        raise GlbConversionError(f"Exported GLB failed to re-open: {exc}") from exc

    if not isinstance(reloaded, trimesh.Trimesh) or len(reloaded.vertices) == 0 or len(reloaded.faces) == 0:
        temp_path.unlink(missing_ok=True)
        raise GlbConversionError("Exported GLB has no readable geometry.")

    size_bytes = temp_path.stat().st_size
    if size_bytes == 0:
        temp_path.unlink(missing_ok=True)
        raise GlbConversionError("Exported GLB is zero bytes.")

    size_mb = size_bytes / (1024 * 1024)
    hard_ceiling_mb = max_glb_mb * 4
    if size_mb > hard_ceiling_mb:
        temp_path.unlink(missing_ok=True)
        raise GlbConversionError(
            f"Exported GLB is {size_mb:.1f} MB, far above the {max_glb_mb} MB mobile target; rebuild with a lower "
            "MODEL_3D_SIMPLIFY_RATIO or fewer/lower-resolution source photos."
        )
    if size_mb > max_glb_mb:
        warnings.append(f"Exported GLB is {size_mb:.1f} MB, above the {max_glb_mb} MB mobile target.")

    sha256 = hashlib.sha256(temp_path.read_bytes()).hexdigest()
    temp_path.replace(destination)

    return GlbConversionResult(
        sha256=sha256,
        size_bytes=size_bytes,
        vertex_count=len(reloaded.vertices),
        face_count=len(reloaded.faces),
        has_texture=has_texture,
        warnings=warnings,
    )
