from __future__ import annotations

import asyncio
import json
import time
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from enum import StrEnum
from typing import Any

from pydantic import BaseModel

from ai_agent_orchestrator import observability
from ai_agent_orchestrator.clients.base import GovernedClientError


class ToolKind(StrEnum):
    READ = "READ"
    RETRIEVAL = "RETRIEVAL"
    ACTION_CANDIDATE = "ACTION_CANDIDATE"


ToolHandler = Callable[[BaseModel], Awaitable[BaseModel]]


@dataclass(frozen=True, slots=True)
class ToolRegistration:
    name: str
    kind: ToolKind
    input_schema: type[BaseModel]
    output_schema: type[BaseModel]
    handler: ToolHandler
    requires_authorization: bool = True
    timeout_seconds: float = 20
    retry_attempts: int = 2


class ToolRegistry:
    def __init__(self, max_result_bytes: int) -> None:
        self._tools: dict[str, ToolRegistration] = {}
        self._max_result_bytes = max_result_bytes

    def register(self, registration: ToolRegistration) -> None:
        if registration.name in self._tools:
            raise ValueError(f"tool already registered: {registration.name}")
        self._tools[registration.name] = registration

    def resolve(self, name: str) -> ToolRegistration:
        if name not in self._tools:
            raise KeyError("AGENT_TOOL_NOT_REGISTERED")
        return self._tools[name]

    async def invoke(self, name: str, payload: dict[str, Any]) -> BaseModel:
        registration = self.resolve(name)
        tool_input = registration.input_schema.model_validate(payload)
        started = time.monotonic()
        attempts = (
            max(1, registration.retry_attempts)
            if registration.kind in {ToolKind.READ, ToolKind.RETRIEVAL}
            else 1
        )
        try:
            for attempt in range(attempts):
                try:
                    async with asyncio.timeout(registration.timeout_seconds):
                        output = await registration.handler(tool_input)
                    break
                except GovernedClientError as exception:
                    if not exception.retryable or attempt + 1 >= attempts:
                        raise
                    observability.AGENT_TOOL_RETRIES.labels(registration.name).inc()
                    await asyncio.sleep(min(0.5, 0.1 * (attempt + 1)))
            validated = registration.output_schema.model_validate(output)
            encoded = json.dumps(validated.model_dump(mode="json"), ensure_ascii=False).encode()
            if len(encoded) > self._max_result_bytes:
                raise ValueError("AGENT_TOOL_RESULT_TOO_LARGE")
        except Exception:
            observability.AGENT_TOOL_CALLS.labels(
                registration.name,
                registration.kind.value,
                "failed",
            ).inc()
            raise
        else:
            observability.AGENT_TOOL_CALLS.labels(
                registration.name,
                registration.kind.value,
                "succeeded",
            ).inc()
            return validated
        finally:
            observability.AGENT_TOOL_DURATION.labels(
                registration.name,
                registration.kind.value,
            ).observe(time.monotonic() - started)
