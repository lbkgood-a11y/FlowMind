from __future__ import annotations

import asyncio
import json
from dataclasses import dataclass, field
from datetime import UTC, datetime, timedelta
from typing import Any, Protocol

from psycopg.rows import dict_row
from psycopg_pool import AsyncConnectionPool

from ai_agent_orchestrator.contracts.events import AgentEvent
from ai_agent_orchestrator.contracts.models import AgentRunError, AgentRunStatus


@dataclass(slots=True)
class RunRecord:
    run_id: str
    thread_id: str
    graph_id: str
    graph_version: str
    state_schema_version: str
    tenant_id: str
    actor_id: str
    trace_id: str
    correlation_id: str
    idempotency_key: str
    status: AgentRunStatus = AgentRunStatus.CREATED
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))
    updated_at: datetime = field(default_factory=lambda: datetime.now(UTC))
    last_sequence: int = 0
    pending_interrupt: dict[str, Any] | None = None
    action_refs: list[str] = field(default_factory=list)
    error: AgentRunError | None = None


class RunRepository(Protocol):
    async def setup(self) -> None: ...

    async def close(self) -> None: ...

    async def create(self, record: RunRecord) -> tuple[RunRecord, bool]: ...

    async def get(self, run_id: str, tenant_id: str, actor_id: str) -> RunRecord | None: ...

    async def update(
        self,
        run_id: str,
        *,
        status: AgentRunStatus | None = None,
        pending_interrupt: dict[str, Any] | None = None,
        clear_interrupt: bool = False,
        action_refs: list[str] | None = None,
        error: AgentRunError | None = None,
    ) -> RunRecord: ...

    async def append_event(self, event: AgentEvent) -> AgentEvent: ...

    async def events_after(self, run_id: str, after_sequence: int) -> list[AgentEvent]: ...

    async def wait_for_events(
        self,
        run_id: str,
        after_sequence: int,
        wait_seconds: float,
    ) -> list[AgentEvent]: ...

    async def delete_thread(self, tenant_id: str, actor_id: str, thread_id: str) -> int: ...

    async def purge_expired(self, retention_days: int) -> int: ...


class InMemoryRunRepository:
    def __init__(self) -> None:
        self._runs: dict[str, RunRecord] = {}
        self._idempotency: dict[tuple[str, str, str], str] = {}
        self._events: dict[str, list[AgentEvent]] = {}
        self._conditions: dict[str, asyncio.Condition] = {}
        self._lock = asyncio.Lock()

    async def setup(self) -> None:
        return None

    async def close(self) -> None:
        return None

    async def create(self, record: RunRecord) -> tuple[RunRecord, bool]:
        key = (record.tenant_id, record.actor_id, record.idempotency_key)
        async with self._lock:
            existing_id = self._idempotency.get(key)
            if existing_id:
                return self._runs[existing_id], False
            self._runs[record.run_id] = record
            self._idempotency[key] = record.run_id
            self._events[record.run_id] = []
            self._conditions[record.run_id] = asyncio.Condition()
            return record, True

    async def get(self, run_id: str, tenant_id: str, actor_id: str) -> RunRecord | None:
        record = self._runs.get(run_id)
        if record is None or record.tenant_id != tenant_id or record.actor_id != actor_id:
            return None
        return record

    async def update(
        self,
        run_id: str,
        *,
        status: AgentRunStatus | None = None,
        pending_interrupt: dict[str, Any] | None = None,
        clear_interrupt: bool = False,
        action_refs: list[str] | None = None,
        error: AgentRunError | None = None,
    ) -> RunRecord:
        async with self._lock:
            record = self._runs[run_id]
            if status is not None:
                record.status = status
            if clear_interrupt:
                record.pending_interrupt = None
            elif pending_interrupt is not None:
                record.pending_interrupt = pending_interrupt
            if action_refs is not None:
                record.action_refs = list(action_refs)
            if error is not None:
                record.error = error
            record.updated_at = datetime.now(UTC)
            return record

    async def append_event(self, event: AgentEvent) -> AgentEvent:
        condition = self._conditions[event.run_id]
        async with condition:
            async with self._lock:
                record = self._runs[event.run_id]
                record.last_sequence += 1
                record.updated_at = datetime.now(UTC)
                stored = event.model_copy(
                    update={
                        "sequence": record.last_sequence,
                        "event_id": f"{event.run_id}:{record.last_sequence}",
                    }
                )
                self._events[event.run_id].append(stored)
            condition.notify_all()
            return stored

    async def events_after(self, run_id: str, after_sequence: int) -> list[AgentEvent]:
        return [event for event in self._events.get(run_id, []) if event.sequence > after_sequence]

    async def wait_for_events(
        self,
        run_id: str,
        after_sequence: int,
        wait_seconds: float,
    ) -> list[AgentEvent]:
        events = await self.events_after(run_id, after_sequence)
        if events:
            return events
        condition = self._conditions.get(run_id)
        if condition is None:
            return []
        async with condition:
            try:
                await asyncio.wait_for(condition.wait(), timeout=wait_seconds)
            except TimeoutError:
                return []
        return await self.events_after(run_id, after_sequence)

    async def delete_thread(self, tenant_id: str, actor_id: str, thread_id: str) -> int:
        async with self._lock:
            ids = [
                record.run_id
                for record in self._runs.values()
                if record.tenant_id == tenant_id
                and record.actor_id == actor_id
                and record.thread_id == thread_id
            ]
            for run_id in ids:
                record = self._runs.pop(run_id)
                self._idempotency.pop(
                    (record.tenant_id, record.actor_id, record.idempotency_key),
                    None,
                )
                self._events.pop(run_id, None)
                self._conditions.pop(run_id, None)
            return len(ids)

    async def purge_expired(self, retention_days: int) -> int:
        threshold = datetime.now(UTC) - timedelta(days=retention_days)
        deleted = 0
        for record in list(self._runs.values()):
            if record.updated_at < threshold:
                deleted += await self.delete_thread(
                    record.tenant_id,
                    record.actor_id,
                    record.thread_id,
                )
        return deleted


class PostgresRunRepository:
    def __init__(self, database_url: str, poll_interval: float = 0.25) -> None:
        self._pool = AsyncConnectionPool(database_url, open=False, kwargs={"row_factory": dict_row})
        self._poll_interval = poll_interval

    async def setup(self) -> None:
        await self._pool.open()
        async with self._pool.connection() as connection:
            await connection.execute(_SCHEMA_SQL)
            await connection.commit()

    async def close(self) -> None:
        await self._pool.close()

    async def create(self, record: RunRecord) -> tuple[RunRecord, bool]:
        async with self._pool.connection() as connection:
            cursor = await connection.execute(
                """
                INSERT INTO ai_agent_run (
                    run_id, thread_id, graph_id, graph_version, state_schema_version,
                    tenant_id, actor_id, trace_id, correlation_id, idempotency_key,
                    status, created_at, updated_at, last_sequence, action_refs
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 0, '[]'::jsonb)
                ON CONFLICT (tenant_id, actor_id, idempotency_key) DO NOTHING
                RETURNING *
                """,
                (
                    record.run_id,
                    record.thread_id,
                    record.graph_id,
                    record.graph_version,
                    record.state_schema_version,
                    record.tenant_id,
                    record.actor_id,
                    record.trace_id,
                    record.correlation_id,
                    record.idempotency_key,
                    record.status.value,
                    record.created_at,
                    record.updated_at,
                ),
            )
            row = await cursor.fetchone()
            created = row is not None
            if row is None:
                cursor = await connection.execute(
                    """
                    SELECT * FROM ai_agent_run
                    WHERE tenant_id=%s AND actor_id=%s AND idempotency_key=%s
                    """,
                    (record.tenant_id, record.actor_id, record.idempotency_key),
                )
                row = await cursor.fetchone()
            await connection.commit()
        if row is None:
            raise RuntimeError("AGENT_RUN_CREATE_FAILED")
        return _record_from_row(row), created

    async def get(self, run_id: str, tenant_id: str, actor_id: str) -> RunRecord | None:
        async with self._pool.connection() as connection:
            cursor = await connection.execute(
                "SELECT * FROM ai_agent_run WHERE run_id=%s AND tenant_id=%s AND actor_id=%s",
                (run_id, tenant_id, actor_id),
            )
            row = await cursor.fetchone()
        return _record_from_row(row) if row else None

    async def update(
        self,
        run_id: str,
        *,
        status: AgentRunStatus | None = None,
        pending_interrupt: dict[str, Any] | None = None,
        clear_interrupt: bool = False,
        action_refs: list[str] | None = None,
        error: AgentRunError | None = None,
    ) -> RunRecord:
        fields = ["updated_at=now()"]
        values: list[Any] = []
        if status is not None:
            fields.append("status=%s")
            values.append(status.value)
        if clear_interrupt:
            fields.append("pending_interrupt=NULL")
        elif pending_interrupt is not None:
            fields.append("pending_interrupt=%s::jsonb")
            values.append(json.dumps(pending_interrupt, ensure_ascii=False))
        if action_refs is not None:
            fields.append("action_refs=%s::jsonb")
            values.append(json.dumps(action_refs))
        if error is not None:
            fields.append("error=%s::jsonb")
            values.append(
                json.dumps(error.model_dump(mode="json", by_alias=True), ensure_ascii=False)
            )
        values.append(run_id)
        async with self._pool.connection() as connection:
            cursor = await connection.execute(
                f"UPDATE ai_agent_run SET {', '.join(fields)} WHERE run_id=%s RETURNING *",  # noqa: S608
                values,
            )
            row = await cursor.fetchone()
            await connection.commit()
        if row is None:
            raise KeyError("AGENT_RUN_NOT_FOUND")
        return _record_from_row(row)

    async def append_event(self, event: AgentEvent) -> AgentEvent:
        async with self._pool.connection() as connection:
            async with connection.transaction():
                cursor = await connection.execute(
                    """
                    UPDATE ai_agent_run
                    SET last_sequence=last_sequence+1, updated_at=now()
                    WHERE run_id=%s
                    RETURNING last_sequence
                    """,
                    (event.run_id,),
                )
                row = await cursor.fetchone()
                if row is None:
                    raise KeyError("AGENT_RUN_NOT_FOUND")
                sequence = int(row["last_sequence"])
                stored = event.model_copy(
                    update={"sequence": sequence, "event_id": f"{event.run_id}:{sequence}"}
                )
                await connection.execute(
                    """
                    INSERT INTO ai_agent_event (
                        event_id, run_id, thread_id, sequence, event_type, timestamp,
                        trace_id, data_schema_version, event_data
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s::jsonb)
                    """,
                    (
                        stored.event_id,
                        stored.run_id,
                        stored.thread_id,
                        stored.sequence,
                        str(stored.event_type),
                        stored.timestamp,
                        stored.trace_id,
                        stored.data_schema_version,
                        json.dumps(stored.data, ensure_ascii=False),
                    ),
                )
        return stored

    async def events_after(self, run_id: str, after_sequence: int) -> list[AgentEvent]:
        async with self._pool.connection() as connection:
            cursor = await connection.execute(
                """
                SELECT * FROM ai_agent_event
                WHERE run_id=%s AND sequence>%s ORDER BY sequence ASC LIMIT 500
                """,
                (run_id, after_sequence),
            )
            rows = await cursor.fetchall()
        return [_event_from_row(row) for row in rows]

    async def wait_for_events(
        self,
        run_id: str,
        after_sequence: int,
        wait_seconds: float,
    ) -> list[AgentEvent]:
        loop = asyncio.get_running_loop()
        deadline = loop.time() + wait_seconds
        while loop.time() < deadline:
            events = await self.events_after(run_id, after_sequence)
            if events:
                return events
            await asyncio.sleep(min(self._poll_interval, max(0, deadline - loop.time())))
        return []

    async def delete_thread(self, tenant_id: str, actor_id: str, thread_id: str) -> int:
        async with self._pool.connection() as connection:
            cursor = await connection.execute(
                """
                DELETE FROM ai_agent_run WHERE tenant_id=%s AND actor_id=%s AND thread_id=%s
                RETURNING run_id
                """,
                (tenant_id, actor_id, thread_id),
            )
            rows = await cursor.fetchall()
            await connection.commit()
        return len(rows)

    async def purge_expired(self, retention_days: int) -> int:
        async with self._pool.connection() as connection:
            cursor = await connection.execute(
                "DELETE FROM ai_agent_run "
                "WHERE updated_at < now() - (%s * interval '1 day') "
                "RETURNING run_id",
                (retention_days,),
            )
            rows = await cursor.fetchall()
            await connection.commit()
        return len(rows)


def _json_value(value: Any, fallback: Any) -> Any:
    if value is None:
        return fallback
    if isinstance(value, str):
        return json.loads(value)
    return value


def _record_from_row(row: dict[str, Any]) -> RunRecord:
    error_value = _json_value(row.get("error"), None)
    return RunRecord(
        run_id=row["run_id"],
        thread_id=row["thread_id"],
        graph_id=row["graph_id"],
        graph_version=row["graph_version"],
        state_schema_version=row["state_schema_version"],
        tenant_id=row["tenant_id"],
        actor_id=row["actor_id"],
        trace_id=row["trace_id"],
        correlation_id=row["correlation_id"],
        idempotency_key=row["idempotency_key"],
        status=AgentRunStatus(row["status"]),
        created_at=row["created_at"],
        updated_at=row["updated_at"],
        last_sequence=int(row.get("last_sequence") or 0),
        pending_interrupt=_json_value(row.get("pending_interrupt"), None),
        action_refs=_json_value(row.get("action_refs"), []),
        error=AgentRunError.model_validate(error_value) if error_value else None,
    )


def _event_from_row(row: dict[str, Any]) -> AgentEvent:
    return AgentEvent(
        eventId=row["event_id"],
        eventType=row["event_type"],
        runId=row["run_id"],
        threadId=row["thread_id"],
        sequence=row["sequence"],
        timestamp=row["timestamp"],
        traceId=row["trace_id"],
        dataSchemaVersion=row["data_schema_version"],
        data=_json_value(row.get("event_data"), {}),
    )


_SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS ai_agent_run (
    run_id VARCHAR(64) PRIMARY KEY,
    thread_id VARCHAR(128) NOT NULL,
    graph_id VARCHAR(128) NOT NULL,
    graph_version VARCHAR(64) NOT NULL,
    state_schema_version VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL,
    pending_interrupt JSONB,
    action_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    error JSONB,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_ai_agent_run_idempotency UNIQUE (tenant_id, actor_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_ai_agent_run_thread
    ON ai_agent_run (tenant_id, actor_id, thread_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_agent_run_retention ON ai_agent_run (updated_at);

CREATE TABLE IF NOT EXISTS ai_agent_event (
    event_id VARCHAR(96) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES ai_agent_run(run_id) ON DELETE CASCADE,
    thread_id VARCHAR(128) NOT NULL,
    sequence BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    data_schema_version VARCHAR(32) NOT NULL,
    event_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uk_ai_agent_event_sequence UNIQUE (run_id, sequence)
);
CREATE INDEX IF NOT EXISTS idx_ai_agent_event_replay ON ai_agent_event (run_id, sequence);
"""
