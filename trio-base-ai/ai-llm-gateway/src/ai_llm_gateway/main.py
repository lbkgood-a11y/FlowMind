from __future__ import annotations

import logging

from contextlib import asynccontextmanager
from fastapi import FastAPI
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
from starlette.responses import Response

from .middleware.prompt_logging import PromptLoggingMiddleware
from .routes.chat import router as chat_router
from .routes.health import router as health_router

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting ai-llm-gateway ...")
    yield
    logger.info("Shutting down ai-llm-gateway ...")


app = FastAPI(
    title="TrioBase LLM Gateway",
    description="LLM 抽象网关 — 铁律 2 第二道防线：模型路由(LiteLLM) + 语义缓存(GPTCache) + Prompt 追踪 + 脱敏二次校验",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(PromptLoggingMiddleware)
app.include_router(health_router)
app.include_router(chat_router)


@app.get("/metrics", include_in_schema=False)
async def metrics() -> Response:
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


def main():
    import uvicorn
    uvicorn.run("ai_llm_gateway.main:app", host="0.0.0.0", port=8002, reload=True)


if __name__ == "__main__":
    main()
