from __future__ import annotations

from dataclasses import dataclass, field

from app.services.model3d import states

# Purely a degenerate-output safety net (near-zero geometry), not a completeness bar - a small
# but legitimate low-poly preview mesh must not be flagged by this alone. registered_image_ratio
# (below) is the primary completeness signal, per MODEL_3D_MIN_REGISTERED_RATIO.
MIN_NONDEGENERATE_VERTICES = 4
MIN_NONDEGENERATE_FACES = 4

# Rough SfM sanity ceiling in pixels. COLMAP's own bundle adjustment quality varies a lot with
# scene content, so this is intentionally generous - it exists to catch a clearly diverged solve,
# not to grade otherwise-successful reconstructions.
MAX_REASONABLE_REPROJECTION_ERROR = 2.0


@dataclass(frozen=True)
class QualityAssessment:
    quality: str
    reasons: list[str] = field(default_factory=list)

    @property
    def is_sufficient(self) -> bool:
        return self.quality == states.QUALITY_GOOD


def assess_reconstruction_quality(
    *,
    source_image_count: int,
    registered_image_count: int | None,
    registered_image_ratio: float | None,
    sparse_point_count: int | None,
    mean_reprojection_error: float | None,
    min_registered_ratio: float,
    vertex_count: int | None = None,
    face_count: int | None = None,
    bounds_finite: bool | None = None,
) -> QualityAssessment:
    """Classifies a COLMAP reconstruction as QUALITY_GOOD or QUALITY_INSUFFICIENT.

    This is deliberately separate from the existing "can we even attempt a preview" sanity gate
    in reconstruction_service.py (registered >= 2 and sparse points >= 10, which decides the
    ready_for_build/needs_images status and must stay unchanged - see
    test_preflight_ready_for_build_from_a_partial_low_ratio_reconstruction). A reconstruction can
    be "usable enough to attempt" yet still be low quality: 3 of 30 photos registering produces a
    real, loadable GLB, but not a complete artifact. Quality is reported alongside status, not
    folded into it, so Admin UI can offer the AI fallback without changing how ready_for_build /
    needs_images already behave.

    Called from two places: reconstruction_service.run_preflight() right after sparse
    reconstruction (mesh metrics not yet available), and reconstruction_worker.run() after the
    full build produces a mesh (mesh metrics available). Both pass whatever they have; the
    reasons list only mentions checks that actually ran.
    """
    reasons: list[str] = []

    if registered_image_ratio is not None and registered_image_ratio < min_registered_ratio:
        registered = registered_image_count or 0
        reasons.append(
            f"Only {registered} of {source_image_count} photo(s) could be matched "
            f"({registered_image_ratio:.0%} registered; {min_registered_ratio:.0%} is the target "
            "for a complete reconstruction)."
        )

    if mean_reprojection_error is not None and mean_reprojection_error > MAX_REASONABLE_REPROJECTION_ERROR:
        reasons.append(
            f"Mean reprojection error ({mean_reprojection_error:.2f}px) is higher than expected for "
            "a reliable reconstruction."
        )

    if vertex_count is not None and vertex_count < MIN_NONDEGENERATE_VERTICES:
        reasons.append("The reconstructed mesh has almost no geometry.")
    if face_count is not None and face_count < MIN_NONDEGENERATE_FACES:
        reasons.append("The reconstructed mesh has almost no surface area.")
    if bounds_finite is False:
        reasons.append("The reconstructed geometry has invalid (non-finite) bounds.")

    quality = states.QUALITY_INSUFFICIENT if reasons else states.QUALITY_GOOD
    return QualityAssessment(quality=quality, reasons=reasons)
