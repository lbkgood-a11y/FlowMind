from __future__ import annotations

import json
import logging
import time

from fastapi import APIRouter, Request
from fastapi.responses import StreamingResponse

from ..middleware.data_masking import scan_and_redact
from ..models.chat import ChatMessage, ChatRequest
from ..observability import (
    LLM_CACHE_REQUESTS,
    LLM_FIRST_TOKEN_SECONDS,
    LLM_OUTPUT_CHARACTERS,
    LLM_STREAM_DURATION_SECONDS,
    LLM_STREAMS,
)
from ..services.cache_service import CacheScope, cache_lookup, cache_store
from ..services.llm_service import chat_stream

logger = logging.getLogger(__name__)
router = APIRouter()


@router.post("/api/v1/ai/chat")
async def chat(request: ChatRequest, http_request: Request):
    prompt_text = json.dumps([m.model_dump() for m in request.messages], ensure_ascii=False)

    redacted, findings = scan_and_redact(prompt_text)
    if findings:
        logger.info("Data masking redacted: %s", findings)

    effective_request = request
    effective_prompt = prompt_text
    if redacted != prompt_text:
        effective_prompt = redacted
        effective_request = request.model_copy(update={
            "messages": [ChatMessage.model_validate(message) for message in json.loads(redacted)]
        })

    cache_scope = CacheScope(
        tenant_id=http_request.headers.get("X-Tenant-Id", ""),
        actor_id=http_request.headers.get("X-User-Id", ""),
        authorization_version=http_request.headers.get("X-Authorization-Version", "current"),
        knowledge_version=http_request.headers.get("X-Knowledge-Version", "none"),
    )
    cache_allowed = http_request.headers.get("X-LLM-Cache-Policy", "deny").lower() == "allow"
    cached = await cache_lookup(
        effective_request.model,
        effective_prompt,
        cache_scope,
        cache_allowed,
    )
    if cached:
        LLM_CACHE_REQUESTS.labels("hit").inc()

        async def cached_stream():
            yield f"data: {cached}\n\n"
            yield "data: [DONE]\n\n"

        return StreamingResponse(
            cached_stream(),
            media_type="text/event-stream",
            headers={"X-Cache-Hit": "true"},
        )

    LLM_CACHE_REQUESTS.labels("miss" if cache_allowed else "bypassed").inc()

    async def generate():
        response_parts: list[str] = []
        started = time.monotonic()
        first_token_recorded = False
        status = "succeeded"

        try:
            async for chunk in chat_stream(effective_request):
                if chunk.startswith("data: ") and chunk != "data: [DONE]\n\n":
                    payload = chunk[6:-2]
                    if payload.startswith("{\"error\":"):
                        status = "failed"
                    else:
                        if not first_token_recorded:
                            LLM_FIRST_TOKEN_SECONDS.labels(effective_request.model).observe(
                                time.monotonic() - started
                            )
                            first_token_recorded = True
                        response_parts.append(payload)
                        LLM_OUTPUT_CHARACTERS.labels(effective_request.model).inc(len(payload))
                yield chunk

            if response_parts:
                await cache_store(
                    effective_request.model,
                    effective_prompt,
                    "".join(response_parts),
                    cache_scope,
                    cache_allowed,
                )
        finally:
            LLM_STREAMS.labels(effective_request.model, status).inc()
            LLM_STREAM_DURATION_SECONDS.labels(effective_request.model).observe(
                time.monotonic() - started
            )

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
    )
