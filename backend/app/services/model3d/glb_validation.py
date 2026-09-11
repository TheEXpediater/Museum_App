from __future__ import annotations

import hashlib
import math
from dataclasses import dataclass
from pathlib import Path

from app.services.model3d.glb_converter import default_preview_material

# A generous safety net against a corrupted/runaway download, not a mobile-optimization target -
# AI-provider GLBs legitimately carry real 2k-8k textures and are not expected to be as small as
# COLMAP's lightweight preview meshes (see MODEL_3D_MAX_GLB_MB, which does not apply here).
EXTERNAL_GLB_HARD_CEILING_MB = 200


class GlbValidationError(RuntimeError):
    pass


@dataclass(frozen=True)
class ValidatedGlb:
    sha256: str
    size_bytes: int
    vertex_count: int
    face_count: int
    bounds_min: list[float]
    bounds_max: list[float]
    repaired_material_or_normals: bool


def validate_external_glb(path: Path) -> ValidatedGlb:
    """Validates a GLB that this backend did not itself produce (e.g. downloaded from an AI
    provider) before it is ever published. Unlike convert_to_glb() (which always assigns a
    fallback material/normals because COLMAP's untextured PLY path genuinely never has any),
    this never re-exports/mutates the mesh unless a required attribute is actually missing - a
    properly textured AI-generated GLB must survive with its real material intact.

    Raises GlbValidationError with an admin-safe message on any failure. Never publishes: the
    caller decides whether/when to move the validated file into the real models3d/ location.
    """
    import trimesh

    if not path.is_file():
        raise GlbValidationError("The downloaded 3D model file does not exist.")

    size_bytes = path.stat().st_size
    if size_bytes == 0:
        raise GlbValidationError("The downloaded 3D model file is empty.")
    size_mb = size_bytes / (1024 * 1024)
    if size_mb > EXTERNAL_GLB_HARD_CEILING_MB:
        raise GlbValidationError(f"The downloaded 3D model is {size_mb:.1f} MB, above the safety limit.")

    try:
        scene = trimesh.load(str(path), file_type="glb", force="mesh", process=False)
    except Exception as exc:
        raise GlbValidationError(f"The downloaded file is not a readable GLB: {exc}") from exc

    if not isinstance(scene, trimesh.Trimesh):
        raise GlbValidationError("The downloaded GLB did not resolve to a single mesh.")
    if len(scene.vertices) == 0 or len(scene.faces) == 0:
        raise GlbValidationError("The downloaded GLB has no geometry (zero vertices or faces).")

    bounds = scene.bounds
    if bounds is None or not bool(np_isfinite_all(bounds)):
        raise GlbValidationError("The downloaded GLB has invalid (non-finite) bounds.")

    repaired = _ensure_material_and_normals_if_missing(scene)
    if repaired:
        path.write_bytes(scene.export(file_type="glb"))
        # Re-open the file we just (possibly) rewrote, exactly like convert_to_glb() does, so a
        # broken re-export is caught here rather than surfacing later as a silent black screen.
        try:
            reloaded = trimesh.load(str(path), file_type="glb", force="mesh", process=False)
        except Exception as exc:
            raise GlbValidationError(f"Repaired GLB failed to re-open: {exc}") from exc
        if not isinstance(reloaded, trimesh.Trimesh) or len(reloaded.vertices) == 0 or len(reloaded.faces) == 0:
            raise GlbValidationError("Repaired GLB has no readable geometry.")
        scene = reloaded
        size_bytes = path.stat().st_size
        bounds = scene.bounds

    return ValidatedGlb(
        sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
        size_bytes=size_bytes,
        vertex_count=len(scene.vertices),
        face_count=len(scene.faces),
        bounds_min=bounds[0].tolist(),
        bounds_max=bounds[1].tolist(),
        repaired_material_or_normals=repaired,
    )


def _ensure_material_and_normals_if_missing(mesh) -> bool:
    """Returns True if the mesh was modified. Only touches what is actually absent.

    Deliberately does NOT use `"vertex_normals" not in mesh._cache.cache` as a "does this file
    have normals" check the way convert_to_glb() does for a freshly-loaded-from-PLY mesh: that
    trick only works because that mesh has never been exported before, so cache-empty genuinely
    means "never computed". Here the mesh was just reloaded from an already-exported GLB -
    trimesh's GLTF loader never eagerly populates vertex_normals from the file's own NORMAL
    accessor into the cache regardless of whether the file has one, so checking the cache after
    load would flag every external GLB as "missing normals" and force an unnecessary re-export
    (recomputing normals from face angles) even for a properly complete AI asset. `.material` has
    no such problem - it is set directly from the file's materials array at load time, not
    lazily derived - so that remains a reliable, load-time signal.
    """
    if getattr(mesh.visual, "material", None) is not None:
        return False

    # A missing material - not merely missing vertex color - is what actually causes the
    # black-screen bug (see glb_converter.py's convert_to_glb): glTF's spec default for a
    # material-less primitive is metallicFactor=1, which this app's renderer shows as black
    # regardless of whether COLOR_0 is present. Real proof: the cube control-test GLB used to
    # diagnose that bug had vertex colors and still rendered black until an explicit material
    # was assigned. Fixing it requires a re-export, so normals are forced into the cache here
    # too (free, and required for the re-export to include NORMAL either way).
    import trimesh

    mesh.visual = trimesh.visual.texture.TextureVisuals(material=default_preview_material())
    _ = mesh.vertex_normals
    return True


def np_isfinite_all(bounds) -> bool:
    try:
        return bool(all(math.isfinite(float(v)) for row in bounds for v in row))
    except (TypeError, ValueError):
        return False
