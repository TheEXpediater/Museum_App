from __future__ import annotations

import re
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path

from app.config import Settings

# Only generic, well-known install locations. Never a personal machine-specific path.
_WINDOWS_CANDIDATES = (
    r"C:\Program Files\COLMAP\COLMAP.bat",
    r"C:\Program Files\COLMAP\colmap.exe",
    r"C:\Program Files (x86)\COLMAP\COLMAP.bat",
    r"C:\Program Files (x86)\COLMAP\colmap.exe",
)

_VERSION_PATTERN = re.compile(r"COLMAP\s+(\d+\.\d+(?:\.\d+)?)", re.IGNORECASE)


@dataclass(frozen=True)
class ColmapAvailability:
    available: bool
    bin_path: str | None
    version: str | None
    message: str | None


def _candidate_paths(configured_bin: str) -> list[str]:
    candidates: list[str] = []
    configured = (configured_bin or "").strip()
    if configured:
        candidates.append(configured)
    resolved_from_path = shutil.which(configured) if configured else None
    if resolved_from_path:
        candidates.append(resolved_from_path)
    default_on_path = shutil.which("colmap")
    if default_on_path:
        candidates.append(default_on_path)
    candidates.extend(path for path in _WINDOWS_CANDIDATES if Path(path).is_file())
    # De-duplicate while preserving order.
    seen: set[str] = set()
    unique: list[str] = []
    for candidate in candidates:
        if candidate not in seen:
            seen.add(candidate)
            unique.append(candidate)
    return unique


def _probe_version(bin_path: str) -> str | None:
    try:
        result = subprocess.run(
            [bin_path, "-h"],
            capture_output=True,
            text=True,
            timeout=15,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return None

    output = f"{result.stdout or ''}\n{result.stderr or ''}"
    match = _VERSION_PATTERN.search(output)
    if match:
        return match.group(1)
    # Some builds respond to `-h` without printing a version banner. Any usage output at all
    # still proves the binary runs, so treat that as "installed, version unknown".
    if output.strip():
        return "unknown"
    return None


def detect_colmap(settings: Settings) -> ColmapAvailability:
    """Resolve COLMAP in order: configured COLMAP_BIN, PATH, then known install locations.

    COLMAP is an optional feature dependency - this never raises, so ordinary application
    startup and status reporting never fail solely because COLMAP is missing.
    """
    if not settings.model_3d_enabled:
        return ColmapAvailability(available=False, bin_path=None, version=None, message="3D reconstruction is disabled.")

    for candidate in _candidate_paths(settings.colmap_bin):
        version = _probe_version(candidate)
        if version is not None:
            return ColmapAvailability(available=True, bin_path=candidate, version=version, message=None)

    return ColmapAvailability(
        available=False,
        bin_path=None,
        version=None,
        message="COLMAP was not found. Set COLMAP_BIN or add colmap to PATH to enable 3D reconstruction.",
    )


def get_help_text(bin_path: str) -> str:
    """Full `-h` output, used to confirm exact option/command names before invoking a stage."""
    try:
        result = subprocess.run([bin_path, "-h"], capture_output=True, text=True, timeout=15, check=False)
        return f"{result.stdout or ''}\n{result.stderr or ''}"
    except (OSError, subprocess.SubprocessError):
        return ""


def command_supported(bin_path: str, command: str) -> bool:
    help_text = get_help_text(bin_path)
    return bool(re.search(rf"^\s*{re.escape(command)}\b", help_text, re.MULTILINE))
