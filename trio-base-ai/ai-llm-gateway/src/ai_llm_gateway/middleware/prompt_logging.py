from __future__ import annotations

import json
import time
import logging
from typing import Optional

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

logger = logging.getLogger("prompt_logging")


class PromptLoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next) -> Response:
        if not request.url.path.startswith("/api/v1/ai/chat"):
            return await call_next(request)

        trace_id = request.headers.get("X-B3-TraceId", "unknown")
        start = time.time()

        body = await request.body()
        try:
            body_json = json.loads(body) if body else {}
        except json.JSONDecodeError:
            body_json = {}

        model = body_json.get("model", "unknown")
        messages = body_json.get("messages", [])
        prompt_preview = ""
        if messages:
            last_msg = str(messages[-1].get("content", "")) if isinstance(messages[-1], dict) else str(messages[-1])
            prompt_preview = last_msg[:500]

        response = await call_next(request)
        elapsed_ms = int((time.time() - start) * 1000)

        log_entry = {
            "trace_id": trace_id,
            "model": model,
            "prompt_preview": prompt_preview,
            "response_preview": "",
            "tokens_used": 0,
            "cost_estimate": 0.0,
            "cache_hit": "X-Cache-Hit" in response.headers,
            "latency_ms": elapsed_ms,
        }

        logger.info("LLM Request: %s", json.dumps(log_entry, ensure_ascii=False))
        return response
