"""Expose liveness and readiness signals without invoking model providers."""

from __future__ import annotations

from fastapi import APIRouter, Request

router = APIRouter(tags=["health"])


@router.get("/health")
async def health(request: Request) -> dict[str, object]:
    """Report process liveness and whether the agent runtime feature is enabled."""
    settings = request.app.state.settings
    return {
        "status": "healthy",
        "service": settings.service_name,
        "environment": settings.environment,
        "enabled": settings.enabled,
    }


@router.get("/ready")
async def ready(request: Request) -> dict[str, object]:
    """Report the checkpoint backend and graph versions available for new runs."""
    return {
        "status": "ready",
        "checkpointBackend": request.app.state.settings.checkpoint_backend,
        "graphs": ["triobase-assistant@1.0.0"],
    }
