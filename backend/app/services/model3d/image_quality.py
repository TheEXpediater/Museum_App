from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image, UnidentifiedImageError

MIN_DIMENSION_PX = 480
# Average-hash Hamming distance at/under this value is treated as a near-duplicate photo.
NEAR_DUPLICATE_HAMMING_DISTANCE = 4


@dataclass
class ImageQualityIssue:
    filename: str
    reason: str


@dataclass
class ImageQualityReport:
    usable_count: int
    total_count: int
    duplicate_count: int
    issues: list[ImageQualityIssue] = field(default_factory=list)

    @property
    def guidance(self) -> list[str]:
        messages: list[str] = []
        if self.duplicate_count:
            messages.append(
                f"{self.duplicate_count} photo(s) look like duplicates or near-duplicates; "
                "replace them with new viewpoints instead of repeating the same angle."
            )
        unreadable = [issue for issue in self.issues if issue.reason != "duplicate"]
        if unreadable:
            messages.append(
                f"{len(unreadable)} photo(s) could not be used (unreadable or too small); remove or replace them."
            )
        return messages


def _average_hash(path: Path) -> int | None:
    try:
        with Image.open(path) as image:
            grayscale = image.convert("L").resize((8, 8))
            pixels = list(grayscale.getdata())
    except (UnidentifiedImageError, OSError):
        return None
    average = sum(pixels) / len(pixels)
    bits = 0
    for pixel in pixels:
        bits = (bits << 1) | (1 if pixel >= average else 0)
    return bits


def _hamming_distance(a: int, b: int) -> int:
    return bin(a ^ b).count("1")


def assess_images(paths: list[Path]) -> ImageQualityReport:
    """Cheap, local pre-flight checks: format/readability/dimensions/near-duplicates.

    This is only the first, inexpensive heuristic gate. It never claims a reconstruction will
    succeed - the COLMAP sparse pass remains the authoritative quality gate.
    """
    issues: list[ImageQualityIssue] = []
    hashes: list[tuple[str, int]] = []
    usable = 0

    for path in paths:
        try:
            with Image.open(path) as image:
                image.verify()
        except (UnidentifiedImageError, OSError):
            issues.append(ImageQualityIssue(filename=path.name, reason="unreadable"))
            continue

        try:
            with Image.open(path) as image:
                width, height = image.size
        except (UnidentifiedImageError, OSError):
            issues.append(ImageQualityIssue(filename=path.name, reason="unreadable"))
            continue

        if width < MIN_DIMENSION_PX or height < MIN_DIMENSION_PX:
            issues.append(ImageQualityIssue(filename=path.name, reason="low_resolution"))
            continue

        usable += 1
        digest = _average_hash(path)
        if digest is not None:
            hashes.append((path.name, digest))

    duplicate_count = 0
    flagged: set[str] = set()
    for index, (name_a, hash_a) in enumerate(hashes):
        if name_a in flagged:
            continue
        for name_b, hash_b in hashes[index + 1 :]:
            if name_b in flagged:
                continue
            if _hamming_distance(hash_a, hash_b) <= NEAR_DUPLICATE_HAMMING_DISTANCE:
                flagged.add(name_b)
                duplicate_count += 1
                issues.append(ImageQualityIssue(filename=name_b, reason="duplicate"))

    return ImageQualityReport(
        usable_count=usable - duplicate_count,
        total_count=len(paths),
        duplicate_count=duplicate_count,
        issues=issues,
    )
