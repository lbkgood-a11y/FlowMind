from __future__ import annotations

import json
from datetime import UTC, datetime
from enum import StrEnum
from typing import Any

from pydantic import Field

from .base import ContractModel


class AgentEventType(StrEnum):
    RUN_CREATED = "run.created"
    MESSAGE_DELTA = "message.delta"
    EVIDENCE_READY = "evidence.ready"
    SLOT_MISSING = "slot.missing"
    ACTION_CANDIDATE = "action.candidate"
    CONFIRMATION_REQUIRED = "confirmation.required"
    ACTION_STATUS = "action.status"
    RUN_COMPLETED = "run.completed"
    RUN_FAILED = "run.failed"
    RUN_CANCELLED = "run.cancelled"
    HEARTBEAT = "heartbeat"


class AgentEvent(ContractModel):
    event_id: str
    event_type: AgentEventType | str
    run_id: str
    thread_id: str
    sequence: int = Field(ge=1)
    timestamp: datetime = Field(default_factory=lambda: datetime.now(UTC))
    trace_id: str
    data_schema_version: str = "1.0"
    data: dict[str, Any] = Field(default_factory=dict)

    def to_sse(self) -> str:
        payload = self.model_dump(mode="json", by_alias=True)
        return (
            f"id: {self.event_id}\n"
            f"event: {self.event_type}\n"
            f"data: {json.dumps(payload, ensure_ascii=False, separators=(',', ':'))}\n\n"
        )
