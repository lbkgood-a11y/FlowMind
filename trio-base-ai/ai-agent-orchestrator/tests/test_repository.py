from __future__ import annotations

from ai_agent_orchestrator.contracts.events import AgentEvent
from ai_agent_orchestrator.persistence.repository import InMemoryRunRepository, RunRecord


def record() -> RunRecord:
    return RunRecord(
        run_id="run-1",
        thread_id="thread-1",
        graph_id="assistant",
        graph_version="1.0.0",
        state_schema_version="1.0",
        tenant_id="default",
        actor_id="U001",
        trace_id="trace-1",
        correlation_id="correlation-1",
        idempotency_key="idem-1",
    )


async def test_run_creation_is_idempotent_and_events_replay_in_order() -> None:
    repository = InMemoryRunRepository()
    first, created = await repository.create(record())
    duplicate, duplicate_created = await repository.create(record())
    assert created is True
    assert duplicate_created is False
    assert duplicate.run_id == first.run_id

    for text in ("one", "two"):
        await repository.append_event(
            AgentEvent(
                eventId="pending",
                eventType="message.delta",
                runId=first.run_id,
                threadId=first.thread_id,
                sequence=1,
                traceId=first.trace_id,
                data={"text": text},
            )
        )
    replay = await repository.events_after(first.run_id, 1)
    assert [event.sequence for event in replay] == [2]
    assert replay[0].data["text"] == "two"


async def test_run_access_is_scoped_to_actor_and_tenant() -> None:
    repository = InMemoryRunRepository()
    await repository.create(record())
    assert await repository.get("run-1", "default", "U001") is not None
    assert await repository.get("run-1", "default", "U002") is None
    assert await repository.get("run-1", "tenant-b", "U001") is None
