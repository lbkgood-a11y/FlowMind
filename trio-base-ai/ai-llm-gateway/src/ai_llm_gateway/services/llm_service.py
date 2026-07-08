from __future__ import annotations

import logging
from typing import AsyncIterator

import litellm

from ..config import config
from ..models.chat import ChatRequest, ChatResponse

logger = logging.getLogger(__name__)

litellm.drop_params = True


async def chat_stream(request: ChatRequest) -> AsyncIterator[str]:
    provider, _, model_name = request.model.partition("/")
    provider_cfg = config.llm_providers.get(provider)

    if not provider_cfg:
        available = list(config.llm_providers.keys())
        yield f'data: {{"error": "UNSUPPORTED_MODEL", "available": {available}}}\n\n'
        yield "data: [DONE]\n\n"
        return

    litellm.api_key = provider_cfg.api_key
    if provider_cfg.base_url:
        litellm.api_base = provider_cfg.base_url

    litellm_model = f"{provider}/{model_name}"

    try:
        response = await litellm.acompletion(
            model=litellm_model,
            messages=[m.model_dump() for m in request.messages],
            temperature=request.temperature,
            max_tokens=request.max_tokens,
            stream=True,
        )

        async for chunk in response:
            if chunk.choices and chunk.choices[0].delta.content:
                content = chunk.choices[0].delta.content
                yield f"data: {content}\n\n"

        yield "data: [DONE]\n\n"

    except Exception as e:
        logger.error("LLM call failed: %s", str(e))
        yield f'data: {{"error": "LLM_CALL_FAILED", "detail": "{str(e)}"}}\n\n'
        yield "data: [DONE]\n\n"


async def chat_sync(request: ChatRequest) -> ChatResponse:
    provider, _, model_name = request.model.partition("/")
    provider_cfg = config.llm_providers.get(provider)

    if not provider_cfg:
        available = list(config.llm_providers.keys())
        raise ValueError(f"Unsupported model: {request.model}. Available: {available}")

    litellm.api_key = provider_cfg.api_key
    if provider_cfg.base_url:
        litellm.api_base = provider_cfg.base_url

    litellm_model = f"{provider}/{model_name}"

    response = await litellm.acompletion(
        model=litellm_model,
        messages=[m.model_dump() for m in request.messages],
        temperature=request.temperature,
        max_tokens=request.max_tokens,
        stream=False,
    )

    content = response.choices[0].message.content if response.choices else ""
    return ChatResponse(
        id=response.id,
        model=request.model,
        content=content,
        cached=False,
    )
