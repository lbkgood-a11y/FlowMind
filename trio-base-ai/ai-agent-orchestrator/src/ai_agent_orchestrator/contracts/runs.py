from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import Field

from .base import ContractModel
from .models import AgentRunError, AgentRunStatus


class AgentPageContext(ContractModel):
    route: str | None = Field(default=None, max_length=512)
    app_key: str | None = Field(default=None, max_length=128)
    object_type: str | None = Field(default=None, max_length=128)
    object_id: str | None = Field(default=None, max_length=256)


class AgentRunCreate(ContractModel):
    message: str = Field(min_length=1, max_length=16_000)
    thread_id: str | None = Field(default=None, min_length=1, max_length=128)
    graph_id: str = Field(default="triobase-assistant", max_length=128)
    graph_version: str | None = Field(default=None, max_length=64)
    model: str | None = Field(default=None, max_length=128)
    page_context: AgentPageContext = Field(default_factory=AgentPageContext)


class AgentRunResume(ContractModel):
    kind: Literal["input", "action_result", "cancel"]
    values: dict[str, Any] = Field(default_factory=dict)


class AgentRunResponse(ContractModel):
    run_id: str
    thread_id: str
    graph_id: str
    graph_version: str
    status: AgentRunStatus
    trace_id: str
    correlation_id: str
    created_at: datetime
    updated_at: datetime
    last_sequence: int = 0
    pending_interrupt: dict[str, Any] | None = None
    action_refs: list[str] = Field(default_factory=list)
    error: AgentRunError | None = None
