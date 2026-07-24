from __future__ import annotations

import hashlib
import json
import logging
import time

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

from .data_masking import scan_and_redact

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

        model = str(body_json.get("model", "unknown"))[:128]
        serialized = json.dumps(body_json.get("messages", []), ensure_ascii=False)
        redacted, findings = scan_and_redact(serialized)
        prompt_hash = hashlib.sha256(redacted.encode("utf-8")).hexdigest()

        response = await call_next(request)
        elapsed_ms = int((time.time() - start) * 1000)

        log_entry = {
            "trace_id": trace_id,
            "model": model,
            "prompt_hash": prompt_hash,
            "prompt_chars": len(redacted),
            "redaction_types": sorted(set(findings)),
            "tokens_used": 0,
            "cost_estimate": 0.0,
            "cache_hit": "X-Cache-Hit" in response.headers,
            "latency_ms": elapsed_ms,
        }

        logger.info("LLM Request: %s", json.dumps(log_entry, ensure_ascii=False))
        return response
