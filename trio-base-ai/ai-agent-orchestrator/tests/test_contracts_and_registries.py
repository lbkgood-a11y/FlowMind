from __future__ import annotations

from datetime import UTC, datetime

import pytest
from pydantic import BaseModel, ValidationError

from ai_agent_orchestrator.clients.base import GovernedClientError
from ai_agent_orchestrator.clients.lowcode import RuntimeApplicationDescriptor
from ai_agent_orchestrator.config import Settings
from ai_agent_orchestrator.contracts.events import AgentEvent
from ai_agent_orchestrator.contracts.models import AgentActor, TrustedRequestContext
from ai_agent_orchestrator.runtime.graph_registry import GraphRegistration, GraphRegistry
from ai_agent_orchestrator.runtime.tool_registry import ToolKind, ToolRegistration, ToolRegistry


class ToolInput(BaseModel):
    value: str


class ToolOutput(BaseModel):
    value: str


def trusted_context() -> TrustedRequestContext:
    return TrustedRequestContext(
        tenantId="default",
        actor=AgentActor(id="U001", tenantId="default"),
        traceId="trace-1",
        correlationId="correlation-1",
    )


def test_production_requires_postgres_checkpoint() -> None:
    with pytest.raises(ValidationError, match="production requires"):
        Settings(environment="production", checkpoint_backend="memory")


def test_non_default_tenant_is_rejected() -> None:
    with pytest.raises(ValidationError, match="default tenant"):
        TrustedRequestContext(
            tenantId="tenant-b",
            actor=AgentActor(id="U001", tenantId="tenant-b"),
            traceId="trace-1",
            correlationId="correlation-1",
        )


def test_runtime_application_selects_supported_mutation_by_priority() -> None:
    descriptor = RuntimeApplicationDescriptor.model_validate(
        {
            "appKey": "leave",
            "name": "请假申请",
            "formKey": "leave",
            "version": 1,
            "schemaJson": '{"type":"object"}',
            "actions": [
                {
                    "actionCode": "SAVE",
                    "actionType": "SAVE",
                    "label": "保存",
                    "allowed": True,
                },
                {
                    "actionCode": "CREATE",
                    "actionType": "CREATE",
                    "label": "新建",
                    "allowed": True,
                },
            ],
        }
    )
    action = descriptor.primary_mutation_action()
    assert action.action_code == "CREATE"
    assert action.global_action_type() == "lowcode.form.create"
    assert not action.launches_workflow()


def test_event_envelope_renders_replayable_sse() -> None:
    event = AgentEvent(
        eventId="run-1:2",
        eventType="message.delta",
        runId="run-1",
        threadId="thread-1",
        sequence=2,
        timestamp=datetime(2026, 7, 24, tzinfo=UTC),
        traceId="trace-1",
        data={"text": "你好"},
    )
    payload = event.to_sse()
    assert "id: run-1:2" in payload
    assert "event: message.delta" in payload
    assert '"sequence":2' in payload


def test_graph_registry_pins_versions_and_rejects_schema_mismatch() -> None:
    registry = GraphRegistry()
    registry.register(GraphRegistration("assistant", "1.0.0", "1.0", object()))
    assert registry.resolve("assistant").graph_version == "1.0.0"
    with pytest.raises(ValueError, match="MIGRATION_REQUIRED"):
        registry.require_compatible("assistant", "1.0.0", "0.9")


@pytest.mark.asyncio
async def test_tool_registry_allows_only_registered_schema_valid_tools() -> None:
    async def echo(value: BaseModel) -> BaseModel:
        parsed = ToolInput.model_validate(value)
        return ToolOutput(value=parsed.value)

    registry = ToolRegistry(max_result_bytes=128)
    registry.register(
        ToolRegistration(
            name="leave.read",
            kind=ToolKind.READ,
            input_schema=ToolInput,
            output_schema=ToolOutput,
            handler=echo,
        )
    )
    result = await registry.invoke("leave.read", {"value": "ok"})
    assert result.value == "ok"
    with pytest.raises(KeyError, match="AGENT_TOOL_NOT_REGISTERED"):
        await registry.invoke("shell", {"value": "rm"})


@pytest.mark.asyncio
async def test_read_tool_retries_only_bounded_retryable_failures() -> None:
    attempts = 0

    async def transient_then_ok(value: BaseModel) -> BaseModel:
        nonlocal attempts
        attempts += 1
        if attempts == 1:
            raise GovernedClientError("TEMPORARY", retryable=True)
        return ToolOutput(value=ToolInput.model_validate(value).value)

    registry = ToolRegistry(max_result_bytes=128)
    registry.register(
        ToolRegistration(
            name="leave.retryable-read",
            kind=ToolKind.READ,
            input_schema=ToolInput,
            output_schema=ToolOutput,
            handler=transient_then_ok,
            retry_attempts=2,
        )
    )
    assert (await registry.invoke("leave.retryable-read", {"value": "ok"})).value == "ok"
    assert attempts == 2
