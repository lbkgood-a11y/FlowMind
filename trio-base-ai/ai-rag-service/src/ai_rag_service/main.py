from __future__ import annotations

import logging

from contextlib import asynccontextmanager
from fastapi import FastAPI

from .db.init_db import init_rag_schema
from .routes.health import router as health_router
from .routes.rag import router as rag_router

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting ai-rag-service ...")
    init_rag_schema()
    yield
    logger.info("Shutting down ai-rag-service ...")


app = FastAPI(
    title="TrioBase RAG Service",
    description="独立 RAG 微服务 — 文档入库、向量检索与 Prompt 组装",
    version="0.1.0",
    lifespan=lifespan,
)

app.include_router(health_router)
app.include_router(rag_router)


def main():
    import uvicorn
    uvicorn.run("ai_rag_service.main:app", host="0.0.0.0", port=8003, reload=True)


if __name__ == "__main__":
    main()
