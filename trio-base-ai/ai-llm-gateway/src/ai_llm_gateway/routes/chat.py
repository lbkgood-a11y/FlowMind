from __future__ import annotations

import json
import logging

from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from ..middleware.data_masking import scan_and_redact
from ..models.chat import ChatMessage, ChatRequest
from ..services.cache_service import cache_lookup, cache_store
from ..services.llm_service import chat_stream

logger = logging.getLogger(__name__)
router = APIRouter()


@router.post("/api/v1/ai/chat")
async def chat(request: ChatRequest):
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

    cached = cache_lookup(effective_request.model, effective_prompt)
    if cached:

        async def cached_stream():
            yield f"data: {cached}\n\n"
            yield "data: [DONE]\n\n"

        return StreamingResponse(
            cached_stream(),
            media_type="text/event-stream",
            headers={"X-Cache-Hit": "true"},
        )

    async def generate():
        response_parts: list[str] = []

        async for chunk in chat_stream(effective_request):
            if chunk.startswith("data: ") and chunk != "data: [DONE]\n\n":
                payload = chunk[6:-2]
                if not payload.startswith("{\"error\":"):
                    response_parts.append(payload)
            yield chunk

        if response_parts:
            cache_store(effective_request.model, effective_prompt, "".join(response_parts))

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
    )
