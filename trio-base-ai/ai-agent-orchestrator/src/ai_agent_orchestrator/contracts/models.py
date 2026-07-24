from __future__ import annotations

from datetime import datetime
from enum import StrEnum
from typing import Any

from pydantic import Field, field_validator

from .base import ContractModel


class AgentRunStatus(StrEnum):
    CREATED = "CREATED"
    RUNNING = "RUNNING"
    WAITING_INPUT = "WAITING_INPUT"
    WAITING_CONFIRMATION = "WAITING_CONFIRMATION"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


class AgentActor(ContractModel):
    id: str = Field(min_length=1, max_length=128)
    display_name: str | None = Field(default=None, max_length=256)
    type: str = "USER"
    tenant_id: str = "default"

    @field_validator("tenant_id")
    @classmethod
    def actor_tenant_is_default(cls, value: str) -> str:
        if value != "default":
            raise ValueError("only the trusted default tenant is enabled")
        return value


class TrustedRequestContext(ContractModel):
    tenant_id: str = "default"
    actor: AgentActor
    trace_id: str = Field(min_length=1, max_length=128)
    correlation_id: str = Field(min_length=1, max_length=128)
    request_id: str | None = Field(default=None, max_length=128)
    locale: str = Field(default="zh-CN", max_length=32)
    current_route: str | None = Field(default=None, max_length=512)
    current_app_key: str | None = Field(default=None, max_length=128)
    current_object_type: str | None = Field(default=None, max_length=128)
    current_object_id: str | None = Field(default=None, max_length=256)

    @field_validator("tenant_id")
    @classmethod
    def tenant_is_default(cls, value: str) -> str:
        if value != "default":
            raise ValueError("only the trusted default tenant is enabled")
        return value


class AgentEvidence(ContractModel):
    evidence_id: str
    title: str
    uri: str | None = None
    excerpt: str | None = Field(default=None, max_length=2000)
    score: float | None = Field(default=None, ge=0, le=1)
    knowledge_space: str | None = None


class ActionTarget(ContractModel):
    type: str = "LOWCODE_FORM"
    id: str | None = None
    owner_service: str = "service-lowcode"
    tenant_id: str = "default"
    version: str | None = None
    attributes: dict[str, Any] = Field(default_factory=dict)


class ActionConfirmation(ContractModel):
    required: bool = True
    title: str = "确认执行"
    message: str | None = None
    confirm_label: str = "确认"
    risk_level: str = "NORMAL"


class ActionContext(ContractModel):
    trace_id: str
    correlation_id: str
    tenant_id: str = "default"
    locale: str = "zh-CN"
    confirmation_id: str | None = None
    confirmed_by: str | None = None
    confirmed_at: datetime | None = None
    attributes: dict[str, Any] = Field(default_factory=dict)


class ActionCandidate(ContractModel):
    candidate_id: str
    action_type: str
    source: str = "LUI"
    proposed_by: str = "ai-agent-orchestrator"
    reason: str
    requires_confirmation: bool = True
    confirmation: ActionConfirmation = Field(default_factory=ActionConfirmation)
    actor: AgentActor
    target: ActionTarget
    payload: dict[str, Any]
    context: ActionContext
    idempotency_key: str
    created_at: datetime


class AgentInterrupt(ContractModel):
    kind: str
    prompt: str
    candidate_id: str | None = None
    missing_slots: list[str] = Field(default_factory=list)
    created_at: datetime


class AgentUsage(ContractModel):
    model_calls: int = Field(default=0, ge=0)
    input_tokens: int = Field(default=0, ge=0)
    output_tokens: int = Field(default=0, ge=0)
    estimated_cost_usd: float = Field(default=0, ge=0)


class AgentRunError(ContractModel):
    code: str
    message: str
    category: str = "SYSTEM"
    retryable: bool = False
    details: dict[str, Any] = Field(default_factory=dict)


class AgentState(ContractModel):
    schema_version: str = "1.0"
    graph_id: str
    graph_version: str
    thread_id: str
    run_id: str
    tenant_id: str = "default"
    actor: AgentActor
    trace_id: str
    correlation_id: str
    locale: str = "zh-CN"
    message: str
    intent: str | None = None
    domain: str | None = None
    confidence: float | None = Field(default=None, ge=0, le=1)
    slots: dict[str, Any] = Field(default_factory=dict)
    missing_slots: list[str] = Field(default_factory=list)
    evidence: list[AgentEvidence] = Field(default_factory=list)
    action_candidates: list[ActionCandidate] = Field(default_factory=list)
    pending_interrupt: AgentInterrupt | None = None
    action_refs: list[str] = Field(default_factory=list)
    status: AgentRunStatus = AgentRunStatus.CREATED
    step_count: int = Field(default=0, ge=0)
    usage: AgentUsage = Field(default_factory=AgentUsage)
    warnings: list[str] = Field(default_factory=list)
    error: AgentRunError | None = None

    @field_validator("tenant_id")
    @classmethod
    def state_tenant_is_default(cls, value: str) -> str:
        if value != "default":
            raise ValueError("only tenant 'default' is currently supported")
        return value
