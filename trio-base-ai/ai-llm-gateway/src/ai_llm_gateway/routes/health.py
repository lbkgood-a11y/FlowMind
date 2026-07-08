from __future__ import annotations

from fastapi import APIRouter

from ..config import config

router = APIRouter()


@router.get("/health")
async def health():
    providers_status = {}
    for name, provider in config.llm_providers.items():
        providers_status[name] = "ok" if provider.api_key else "no_key"

    return {
        "status": "healthy",
        "service": "ai-llm-gateway",
        "providers": providers_status,
    }


@router.get("/api/v1/ai/models")
async def list_models():
    models = []
    for name, provider in config.llm_providers.items():
        for model in provider.models:
            models.append({
                "id": f"{name}/{model}",
                "provider": name,
                "max_tokens": 8192,
            })
    return {"models": models}
