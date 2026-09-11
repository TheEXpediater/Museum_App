from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from pathlib import Path

# Provider-neutral task status. Providers map their own vocabulary onto these four values (see
# MeshyProvider._map_status) so callers (ai_generation_service.py) never see provider-specific
# strings.
STATUS_PENDING = "pending"
STATUS_IN_PROGRESS = "in_progress"
STATUS_SUCCEEDED = "succeeded"
STATUS_FAILED = "failed"


class Ai3DProviderError(RuntimeError):
    """Raised for any provider request failure: network error, non-2xx response, malformed
    response body, or a task that finished in a failed/canceled state."""


@dataclass(frozen=True)
class Ai3DTaskHandle:
    """Returned by create_generation(). Only the provider's task id is guaranteed - callers must
    not assume anything else about provider-specific request/response shape."""

    provider_task_id: str


@dataclass(frozen=True)
class Ai3DTaskResult:
    """Returned by get_generation_status(). glb_url is only set when status == STATUS_SUCCEEDED."""

    status: str
    progress: int | None = None
    glb_url: str | None = None
    error_message: str | None = None


class Ai3DProvider(ABC):
    """Provider-neutral interface for image-to-3D generation.

    Nothing outside this module (and its concrete provider implementations) should know which
    external service is in use, what its request/response fields are named, or how its
    authentication works - ai_generation_service.py only calls these three methods.
    """

    #: Maximum number of source images this provider's endpoint accepts in one request.
    max_images: int = 1

    @abstractmethod
    def create_generation(self, image_paths: list[Path]) -> Ai3DTaskHandle:
        """Submits 1..max_images local image files and returns a handle to poll."""

    @abstractmethod
    def get_generation_status(self, task_id: str) -> Ai3DTaskResult:
        """Polls one task. Must not raise for a still-in-progress task; only for a genuine
        request failure (see Ai3DProviderError)."""

    @abstractmethod
    def download_result(self, glb_url: str, destination: Path) -> None:
        """Streams the provider's result GLB to a local file. Raises Ai3DProviderError on
        failure. Callers are responsible for validating the downloaded file afterward - this
        method only guarantees the HTTP transfer succeeded, not that the bytes are a valid GLB."""
