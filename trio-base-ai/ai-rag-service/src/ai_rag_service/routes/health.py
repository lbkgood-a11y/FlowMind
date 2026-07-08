from __future__ import annotations

from fastapi import APIRouter

from ..config import config

router = APIRouter()


@router.get("/health")
async def health():
    return {
        "status": "healthy",
        "service": "ai-rag-service",
        "embedding_model": config.embedding_model,
    }
