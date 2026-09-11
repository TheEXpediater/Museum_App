from __future__ import annotations

# The 6 major regions an admin can mark as "visible in the generation image" when submitting a
# Quick AI (single-image) 3D preview. This is a coarse, honest heuristic - never a claimed model
# confidence or an exact hallucination percentage (see CLAUDE.md "COVERAGE / AI-INFERRED
# PERCENTAGE"). Only the actual generation input counts: reference photos not fed into the AI
# model must not reduce the estimated inferred percentage.
REGIONS = ("front", "right", "back", "left", "top", "bottom")


def estimate_coverage(visible_regions: list[str]) -> tuple[int | None, int | None, list[str]]:
    """Returns (estimated_supported_percent, estimated_inferred_percent, normalized_regions).

    Both percentages are None ("coverage estimate unavailable") when no region information was
    given - never a fabricated 0%. Rounded to whole percent; no fake decimals.
    """
    normalized = sorted({region.strip().lower() for region in visible_regions if region and region.strip().lower() in REGIONS})
    if not normalized:
        return None, None, []
    supported = round(100 * len(normalized) / len(REGIONS))
    inferred = 100 - supported
    return supported, inferred, normalized
