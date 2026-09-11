from __future__ import annotations

import base64
import mimetypes
from pathlib import Path

import httpx

from app.services.model3d.ai_provider import (
    Ai3DProvider,
    Ai3DProviderError,
    Ai3DTaskHandle,
    Ai3DTaskResult,
    STATUS_FAILED,
    STATUS_IN_PROGRESS,
    STATUS_PENDING,
    STATUS_SUCCEEDED,
)

# https://docs.meshy.ai/en/api/quick-start
BASE_URL = "https://api.meshy.ai/openapi/v1"

# https://docs.meshy.ai/en/api/multi-image-to-3d - "1 to 4 images" in image_urls.
MAX_MULTI_IMAGE_COUNT = 4

_STATUS_MAP = {
    "PENDING": STATUS_PENDING,
    "IN_PROGRESS": STATUS_IN_PROGRESS,
    "SUCCEEDED": STATUS_SUCCEEDED,
    "FAILED": STATUS_FAILED,
    "CANCELED": STATUS_FAILED,
}

_REQUEST_TIMEOUT_SECONDS = 30.0
_DOWNLOAD_TIMEOUT_SECONDS = 120.0


class MeshyProvider(Ai3DProvider):
    """Meshy AI's Multi-Image to 3D API (https://docs.meshy.ai/en/api/multi-image-to-3d).

    Images are sent as base64 data URIs rather than public URLs: this system's reconstruction
    photos live on the admin's local backend, which has no public hosting, and the documented
    API explicitly accepts "publicly accessible URLs or base64 data URIs" for image_urls.
    """

    max_images = MAX_MULTI_IMAGE_COUNT

    def __init__(self, api_key: str, *, client: httpx.Client | None = None) -> None:
        if not api_key:
            raise ValueError("Meshy API key is required.")
        self._api_key = api_key
        self._client = client or httpx.Client(timeout=_REQUEST_TIMEOUT_SECONDS)

    def _headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self._api_key}", "Content-Type": "application/json"}

    def create_generation(self, image_paths: list[Path]) -> Ai3DTaskHandle:
        if not image_paths:
            raise ValueError("At least one image is required.")
        if len(image_paths) > self.max_images:
            raise ValueError(f"Meshy accepts at most {self.max_images} images per request.")

        image_urls = [_to_data_uri(path) for path in image_paths]
        try:
            response = self._client.post(
                f"{BASE_URL}/multi-image-to-3d",
                headers=self._headers(),
                json={"image_urls": image_urls},
            )
        except httpx.HTTPError as exc:
            raise Ai3DProviderError(f"Could not reach Meshy: {exc}") from exc

        if response.status_code >= 400:
            raise Ai3DProviderError(
                f"Meshy rejected the generation request (HTTP {response.status_code}): {_error_snippet(response)}"
            )
        try:
            task_id = response.json()["result"]
        except (ValueError, KeyError) as exc:
            raise Ai3DProviderError("Meshy's response did not include a task id.") from exc
        return Ai3DTaskHandle(provider_task_id=task_id)

    def get_generation_status(self, task_id: str) -> Ai3DTaskResult:
        try:
            response = self._client.get(
                f"{BASE_URL}/multi-image-to-3d/{task_id}",
                headers=self._headers(),
            )
        except httpx.HTTPError as exc:
            raise Ai3DProviderError(f"Could not reach Meshy: {exc}") from exc

        if response.status_code >= 400:
            raise Ai3DProviderError(
                f"Meshy rejected the status request (HTTP {response.status_code}): {_error_snippet(response)}"
            )
        try:
            body = response.json()
        except ValueError as exc:
            raise Ai3DProviderError("Meshy's status response was not valid JSON.") from exc

        provider_status = str(body.get("status") or "").upper()
        status = _STATUS_MAP.get(provider_status, STATUS_FAILED)
        glb_url = None
        error_message = None
        if status == STATUS_SUCCEEDED:
            glb_url = (body.get("model_urls") or {}).get("glb")
            if not glb_url:
                status = STATUS_FAILED
                error_message = "Meshy reported success but did not return a GLB URL."
        elif status == STATUS_FAILED:
            error_message = (body.get("task_error") or {}).get("message") or f"Meshy task status: {provider_status or 'unknown'}."

        return Ai3DTaskResult(
            status=status,
            progress=body.get("progress"),
            glb_url=glb_url,
            error_message=error_message,
        )

    def download_result(self, glb_url: str, destination: Path) -> None:
        try:
            with self._client.stream("GET", glb_url, timeout=_DOWNLOAD_TIMEOUT_SECONDS) as response:
                if response.status_code >= 400:
                    raise Ai3DProviderError(f"Downloading the Meshy result failed (HTTP {response.status_code}).")
                destination.parent.mkdir(parents=True, exist_ok=True)
                with destination.open("wb") as handle:
                    for chunk in response.iter_bytes():
                        handle.write(chunk)
        except httpx.HTTPError as exc:
            raise Ai3DProviderError(f"Downloading the Meshy result failed: {exc}") from exc


def _to_data_uri(path: Path) -> str:
    mime_type, _ = mimetypes.guess_type(path.name)
    if mime_type not in {"image/jpeg", "image/png"}:
        raise ValueError(f"{path.name}: Meshy only accepts JPG, JPEG, or PNG images.")
    encoded = base64.b64encode(path.read_bytes()).decode("ascii")
    return f"data:{mime_type};base64,{encoded}"


def _error_snippet(response: httpx.Response) -> str:
    try:
        body = response.json()
        return str(body.get("message") or body)[:200]
    except ValueError:
        return response.text[:200]
