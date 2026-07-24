from __future__ import annotations

import json
import re
from typing import Any

import httpx

from ai_agent_orchestrator import observability

from .base import GovernedClientError, GovernedHttpClient


class LlmGatewayClient(GovernedHttpClient):
    async def complete(
        self,
        *,
        model: str,
        messages: list[dict[str, str]],
        temperature: float = 0,
        max_tokens: int = 1200,
    ) -> str:
        try:
            with observability.TRACER.start_as_current_span(
                "agent.llm.stream",
                attributes={"gen_ai.request.model": model},
            ) as span:
                async with self._client.stream(
                    "POST",
                    "/api/v1/ai/chat",
                    headers=self.headers(),
                    json={
                        "model": model,
                        "messages": messages,
                        "temperature": temperature,
                        "max_tokens": max_tokens,
                        "stream": True,
                    },
                ) as response:
                    span.set_attribute("http.response.status_code", response.status_code)
                    if response.status_code >= 400:
                        raise GovernedClientError(
                            f"LLM_GATEWAY_HTTP_{response.status_code}",
                            status_code=response.status_code,
                            retryable=response.status_code >= 500,
                        )
                    chunks: list[str] = []
                    async for line in response.aiter_lines():
                        if not line.startswith("data:"):
                            continue
                        data = line[5:].lstrip()
                        if data == "[DONE]":
                            break
                        if data.startswith("{"):
                            try:
                                value = json.loads(data)
                            except json.JSONDecodeError:
                                value = None
                            if isinstance(value, dict) and value.get("error"):
                                raise GovernedClientError(str(value["error"])[:128])
                        chunks.append(data)
                    return "".join(chunks)
        except httpx.TimeoutException as exception:
            raise GovernedClientError(
                "LLM_GATEWAY_TIMEOUT", status_code=504, retryable=True
            ) from exception
        except httpx.HTTPError as exception:
            raise GovernedClientError("LLM_GATEWAY_UNAVAILABLE", retryable=True) from exception

    async def complete_json(
        self,
        *,
        model: str,
        system: str,
        user: str,
        max_tokens: int = 1200,
    ) -> dict[str, Any]:
        content = await self.complete(
            model=model,
            messages=[
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            temperature=0,
            max_tokens=max_tokens,
        )
        normalized = re.sub(r"^```(?:json)?\s*|\s*```$", "", content.strip())
        try:
            value = json.loads(normalized)
        except json.JSONDecodeError as exception:
            raise GovernedClientError("LLM_STRUCTURED_OUTPUT_INVALID") from exception
        if not isinstance(value, dict):
            raise GovernedClientError("LLM_STRUCTURED_OUTPUT_INVALID")
        return value
