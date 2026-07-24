from __future__ import annotations

import asyncio
import time
from collections.abc import AsyncIterator
from datetime import UTC, datetime
from typing import Any
from uuid import uuid4

from langgraph.types import Command

from ai_agent_orchestrator import observability
from ai_agent_orchestrator.config import Settings
from ai_agent_orchestrator.contracts.events import AgentEvent, AgentEventType
from ai_agent_orchestrator.contracts.models import (
    AgentRunError,
    AgentRunStatus,
    AgentState,
    TrustedRequestContext,
)
from ai_agent_orchestrator.contracts.runs import AgentRunCreate, AgentRunResponse, AgentRunResume
from ai_agent_orchestrator.observability import (
    AGENT_EVENTS,
    AGENT_NODE_UPDATES,
    AGENT_RUN_DURATION,
    AGENT_RUNS,
)
from ai_agent_orchestrator.persistence.checkpoints import CheckpointManager
from ai_agent_orchestrator.persistence.repository import RunRecord, RunRepository
from ai_agent_orchestrator.security.context import ExecutionCredentials, use_execution_credentials
from ai_agent_orchestrator.security.redaction import minimize_state_data

from .graph_registry import GraphRegistry

_TERMINAL = {
    AgentRunStatus.COMPLETED,
    AgentRunStatus.FAILED,
    AgentRunStatus.CANCELLED,
}


class AgentRunService:
    def __init__(
        self,
        *,
        settings: Settings,
        repository: RunRepository,
        checkpoints: CheckpointManager,
        graphs: GraphRegistry,
    ) -> None:
        self._settings = settings
        self._repository = repository
        self._checkpoints = checkpoints
        self._graphs = graphs
        self._tasks: dict[str, asyncio.Task[None]] = {}
        self._invocation_slots = asyncio.Semaphore(settings.max_concurrent_invocations)

    async def close(self) -> None:
        tasks = list(self._tasks.values())
        for task in tasks:
            task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)

    async def create(
        self,
        request: AgentRunCreate,
        context: TrustedRequestContext,
        authorization: str | None,
        idempotency_key: str,
    ) -> AgentRunResponse:
        self._ensure_capacity()
        registration = self._graphs.resolve(request.graph_id, request.graph_version)
        now = datetime.now(UTC)
        run_id = str(uuid4())
        thread_id = request.thread_id or str(uuid4())
        record = RunRecord(
            run_id=run_id,
            thread_id=thread_id,
            graph_id=registration.graph_id,
            graph_version=registration.graph_version,
            state_schema_version=registration.state_schema_version,
            tenant_id=context.tenant_id,
            actor_id=context.actor.id,
            trace_id=context.trace_id,
            correlation_id=context.correlation_id,
            idempotency_key=idempotency_key,
            created_at=now,
            updated_at=now,
        )
        record, created = await self._repository.create(record)
        if created:
            await self._append(
                record,
                AgentEventType.RUN_CREATED,
                {
                    "graphId": record.graph_id,
                    "graphVersion": record.graph_version,
                    "status": record.status.value,
                },
            )
            agent = AgentState(
                graphId=record.graph_id,
                graphVersion=record.graph_version,
                threadId=record.thread_id,
                runId=record.run_id,
                tenantId=context.tenant_id,
                actor=context.actor,
                traceId=context.trace_id,
                correlationId=context.correlation_id,
                locale=context.locale,
                message=request.message,
            )
            credentials = ExecutionCredentials(context=context, authorization=authorization)
            graph_input = {
                "agent": minimize_state_data(agent.model_dump(mode="json")),
                "model": request.model or self._settings.default_model,
                "events": [],
            }
            self._start(record, graph_input, credentials)
        return _response(record)

    async def get(self, run_id: str, context: TrustedRequestContext) -> AgentRunResponse | None:
        record = await self._repository.get(run_id, context.tenant_id, context.actor.id)
        return _response(record) if record else None

    async def resume(
        self,
        run_id: str,
        request: AgentRunResume,
        context: TrustedRequestContext,
        authorization: str | None,
    ) -> AgentRunResponse:
        record = await self._require(run_id, context)
        if record.status not in {AgentRunStatus.WAITING_INPUT, AgentRunStatus.WAITING_CONFIRMATION}:
            raise ValueError("AGENT_RUN_NOT_WAITING")
        self._graphs.require_compatible(
            record.graph_id,
            record.graph_version,
            record.state_schema_version,
        )
        self._ensure_capacity()
        await self._repository.update(
            run_id,
            status=AgentRunStatus.RUNNING,
            clear_interrupt=True,
        )
        credentials = ExecutionCredentials(context=context, authorization=authorization)
        self._start(record, Command(resume=request.model_dump(mode="json")), credentials)
        updated = await self._require(run_id, context)
        return _response(updated)

    async def cancel(self, run_id: str, context: TrustedRequestContext) -> AgentRunResponse:
        record = await self._require(run_id, context)
        if record.status in _TERMINAL:
            return _response(record)
        task = self._tasks.get(run_id)
        if task is not None:
            task.cancel()
        record = await self._repository.update(
            run_id,
            status=AgentRunStatus.CANCELLED,
            clear_interrupt=True,
        )
        await self._append(record, AgentEventType.RUN_CANCELLED, {"message": "运行已取消"})
        observability.AGENT_CANCELLATIONS.labels(record.graph_id).inc()
        AGENT_RUNS.labels(record.graph_id, record.status.value).inc()
        return _response(record)

    async def delete_thread(self, thread_id: str, context: TrustedRequestContext) -> int:
        namespace_prefix = CheckpointManager.namespace(
            context.tenant_id,
            "triobase-assistant",
            "1.0.0",
            thread_id,
        )
        await self._checkpoints.delete_thread(namespace_prefix)
        return await self._repository.delete_thread(context.tenant_id, context.actor.id, thread_id)

    async def stream(
        self,
        run_id: str,
        context: TrustedRequestContext,
        after_sequence: int,
    ) -> AsyncIterator[AgentEvent]:
        record = await self._require(run_id, context)
        cursor = max(0, after_sequence)
        while True:
            events = await self._repository.wait_for_events(
                run_id,
                cursor,
                wait_seconds=15,
            )
            if events:
                for event in events:
                    cursor = event.sequence
                    yield event
                record = await self._require(run_id, context)
                if record.status in _TERMINAL and cursor >= record.last_sequence:
                    break
                continue
            record = await self._require(run_id, context)
            if record.status in _TERMINAL and cursor >= record.last_sequence:
                break
            heartbeat = await self._append(
                record, AgentEventType.HEARTBEAT, {"status": record.status.value}
            )
            cursor = heartbeat.sequence
            yield heartbeat

    def _start(
        self,
        record: RunRecord,
        graph_input: dict[str, Any] | Command,
        credentials: ExecutionCredentials,
    ) -> None:
        existing = self._tasks.get(record.run_id)
        if existing is not None and not existing.done():
            raise ValueError("AGENT_RUN_ALREADY_EXECUTING")
        task = asyncio.create_task(
            self._execute(record, graph_input, credentials),
            name=f"agent-run:{record.run_id}",
        )
        self._tasks[record.run_id] = task
        task.add_done_callback(lambda _: self._tasks.pop(record.run_id, None))

    def _ensure_capacity(self) -> None:
        active = sum(not task.done() for task in self._tasks.values())
        if active >= self._settings.max_queued_invocations:
            raise ValueError("AGENT_CAPACITY_EXCEEDED")

    async def _execute(
        self,
        record: RunRecord,
        graph_input: dict[str, Any] | Command,
        credentials: ExecutionCredentials,
    ) -> None:
        async with self._invocation_slots:
            await self._execute_invocation(record, graph_input, credentials)

    async def _execute_invocation(
        self,
        record: RunRecord,
        graph_input: dict[str, Any] | Command,
        credentials: ExecutionCredentials,
    ) -> None:
        registration = self._graphs.resolve(record.graph_id, record.graph_version)
        namespace = CheckpointManager.namespace(
            record.tenant_id,
            record.graph_id,
            record.graph_version,
            record.thread_id,
        )
        config = {
            "configurable": {"thread_id": namespace},
            "recursion_limit": self._settings.max_steps,
        }
        started = time.monotonic()
        await self._repository.update(record.run_id, status=AgentRunStatus.RUNNING)
        try:
            with (
                observability.TRACER.start_as_current_span(
                    "agent.run",
                    attributes={
                        "agent.run_id": record.run_id,
                        "agent.thread_id": record.thread_id,
                        "agent.graph_id": record.graph_id,
                        "agent.graph_version": record.graph_version,
                        "tenant.id": record.tenant_id,
                        "enduser.id": record.actor_id,
                        "trace.correlation_id": record.correlation_id,
                    },
                ),
                use_execution_credentials(credentials),
            ):
                async with asyncio.timeout(self._settings.max_run_seconds):
                    async for update in registration.graph.astream(
                        graph_input,
                        config,
                        stream_mode="updates",
                    ):
                        interrupt_value = _interrupt_value(update)
                        if interrupt_value is not None:
                            kind = interrupt_value.get("kind")
                            waiting = (
                                AgentRunStatus.WAITING_CONFIRMATION
                                if kind == "confirmation"
                                else AgentRunStatus.WAITING_INPUT
                            )
                            record = await self._repository.update(
                                record.run_id,
                                status=waiting,
                                pending_interrupt=minimize_state_data(interrupt_value),
                            )
                            event_type = (
                                AgentEventType.CONFIRMATION_REQUIRED
                                if waiting == AgentRunStatus.WAITING_CONFIRMATION
                                else AgentEventType.SLOT_MISSING
                            )
                            observability.AGENT_INTERRUPTS.labels(
                                record.graph_id,
                                str(kind or "input"),
                            ).inc()
                            await self._append(
                                record, event_type, minimize_state_data(interrupt_value)
                            )
                            return
                        await self._persist_update(record, update)

                    snapshot = await registration.graph.aget_state(config)
                    values = snapshot.values if snapshot else {}
                    state_value = values.get("agent") if isinstance(values, dict) else None
                    agent = AgentState.model_validate(state_value) if state_value else None
                    final_status = agent.status if agent else AgentRunStatus.COMPLETED
                    if final_status not in _TERMINAL:
                        final_status = AgentRunStatus.COMPLETED
                    record = await self._repository.update(
                        record.run_id,
                        status=final_status,
                        clear_interrupt=True,
                        action_refs=agent.action_refs if agent else [],
                        error=agent.error if agent and agent.error else None,
                    )
                    AGENT_RUNS.labels(record.graph_id, record.status.value).inc()
        except asyncio.CancelledError:
            raise
        except TimeoutError:
            await self._fail(record, "AGENT_RUN_TIMEOUT", "Agent 运行超时", retryable=True)
        except Exception as exception:  # noqa: BLE001
            code = str(exception)
            if not code or len(code) > 128 or any(char.isspace() for char in code):
                code = "AGENT_RUN_FAILED"
            await self._fail(record, code, "Agent 运行失败")
        finally:
            AGENT_RUN_DURATION.labels(record.graph_id).observe(time.monotonic() - started)

    async def _persist_update(self, record: RunRecord, update: Any) -> None:
        if not isinstance(update, dict):
            return
        for node, node_update in update.items():
            if node == "__interrupt__" or not isinstance(node_update, dict):
                continue
            AGENT_NODE_UPDATES.labels(record.graph_id, str(node)).inc()
            with observability.TRACER.start_as_current_span(
                "agent.node.update",
                attributes={"agent.graph_id": record.graph_id, "agent.node": str(node)},
            ):
                pass
            events = node_update.get("events") or []
            for item in events:
                if not isinstance(item, dict):
                    continue
                event_type = item.get("eventType") or AgentEventType.MESSAGE_DELTA.value
                await self._append(record, event_type, minimize_state_data(item.get("data") or {}))

    async def _fail(
        self,
        record: RunRecord,
        code: str,
        message: str,
        *,
        retryable: bool = False,
    ) -> None:
        error = AgentRunError(code=code, message=message, retryable=retryable)
        record = await self._repository.update(
            record.run_id,
            status=AgentRunStatus.FAILED,
            clear_interrupt=True,
            error=error,
        )
        await self._append(
            record,
            AgentEventType.RUN_FAILED,
            {"error": error.model_dump(mode="json", by_alias=True)},
        )
        AGENT_RUNS.labels(record.graph_id, record.status.value).inc()

    async def _append(
        self,
        record: RunRecord,
        event_type: AgentEventType | str,
        data: dict[str, Any],
    ) -> AgentEvent:
        event = AgentEvent(
            eventId=f"{record.run_id}:pending",
            eventType=event_type,
            runId=record.run_id,
            threadId=record.thread_id,
            sequence=1,
            traceId=record.trace_id,
            data=minimize_state_data(data),
        )
        stored = await self._repository.append_event(event)
        AGENT_EVENTS.labels(str(stored.event_type)).inc()
        return stored

    async def _require(self, run_id: str, context: TrustedRequestContext) -> RunRecord:
        record = await self._repository.get(run_id, context.tenant_id, context.actor.id)
        if record is None:
            raise KeyError("AGENT_RUN_NOT_FOUND")
        return record


def _interrupt_value(update: Any) -> dict[str, Any] | None:
    if not isinstance(update, dict) or "__interrupt__" not in update:
        return None
    interrupts = update.get("__interrupt__") or []
    if not interrupts:
        return None
    first = interrupts[0]
    value = getattr(first, "value", first)
    return value if isinstance(value, dict) else {"kind": "input", "prompt": str(value)}


def _response(record: RunRecord) -> AgentRunResponse:
    return AgentRunResponse(
        runId=record.run_id,
        threadId=record.thread_id,
        graphId=record.graph_id,
        graphVersion=record.graph_version,
        status=record.status,
        traceId=record.trace_id,
        correlationId=record.correlation_id,
        createdAt=record.created_at,
        updatedAt=record.updated_at,
        lastSequence=record.last_sequence,
        pendingInterrupt=record.pending_interrupt,
        actionRefs=record.action_refs,
        error=record.error,
    )
