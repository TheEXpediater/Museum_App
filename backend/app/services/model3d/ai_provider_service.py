from __future__ import annotations

from dataclasses import dataclass

from app.config import Settings
from app.services.model3d import triposr_provider
from app.services.model3d.ai_provider import Ai3DProvider


@dataclass(frozen=True)
class Ai3DAvailability:
    available: bool
    provider: str | None
    max_images: int | None
    message: str | None


def detect_ai_availability(settings: Settings) -> Ai3DAvailability:
    """Mirrors colmap_service.detect_colmap(): never raises, so ordinary status reporting never
    fails solely because AI 3D is unconfigured. AI 3D is optional - COLMAP and the rest of the
    museum system must work identically whether or not this returns available=True.

    Two independent AI 3D axes are checked, in this order:
      1. Local, free, single-image generation (TripoSR) - the DEFAULT "Quick AI 3D Preview".
         Needs no API key; only an installed local runtime (tools/triposr/).
      2. Paid, remote, multi-image generation (Meshy) - an ADVANCED/opt-in alternative, only
         relevant when explicitly configured with AI_3D_ENABLED + AI_3D_API_KEY.
    Whichever is available first is used; nothing here changes when the other is absent.
    """
    local_availability = triposr_provider.detect(settings)
    if local_availability.available:
        return Ai3DAvailability(available=True, provider=settings.local_ai_3d_provider, max_images=1, message=None)

    if not settings.ai_3d_enabled:
        return Ai3DAvailability(
            available=False, provider=None, max_images=None,
            message=local_availability.message or "AI 3D preview is not available.",
        )
    if not settings.ai_3d_api_key:
        return Ai3DAvailability(
            available=False, provider=None, max_images=None, message="AI 3D preview has no API key configured."
        )
    try:
        provider = _get_remote_provider(settings)
    except Ai3DConfigurationError as exc:
        return Ai3DAvailability(available=False, provider=None, max_images=None, message=str(exc))
    return Ai3DAvailability(
        available=True, provider=settings.ai_3d_provider, max_images=provider.max_images, message=None
    )


class Ai3DConfigurationError(RuntimeError):
    pass


def get_provider(settings: Settings) -> Ai3DProvider:
    """Raises Ai3DConfigurationError if AI 3D is disabled/unconfigured/unsupported - callers that
    already checked detect_ai_availability().available should not normally hit this, but routes
    still call it defensively (e.g. a race where the admin submits just as the config changes)."""
    if triposr_provider.detect(settings).available:
        return triposr_provider.TripoSrProvider(settings)
    return _get_remote_provider(settings)


def _get_remote_provider(settings: Settings) -> Ai3DProvider:
    if not settings.ai_3d_enabled:
        raise Ai3DConfigurationError("AI 3D preview is disabled (AI_3D_ENABLED=false).")
    if not settings.ai_3d_api_key:
        raise Ai3DConfigurationError("AI 3D preview has no API key configured (AI_3D_API_KEY is empty).")

    provider_name = settings.ai_3d_provider
    if provider_name == "meshy":
        from app.services.model3d.meshy_provider import MeshyProvider

        return MeshyProvider(api_key=settings.ai_3d_api_key)

    raise Ai3DConfigurationError(f"Unsupported AI_3D_PROVIDER: {provider_name!r}.")
