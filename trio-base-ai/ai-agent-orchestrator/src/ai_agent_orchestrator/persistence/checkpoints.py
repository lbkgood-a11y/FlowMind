from __future__ import annotations

from contextlib import AbstractAsyncContextManager
from typing import Any

from langgraph.checkpoint.memory import InMemorySaver

from ai_agent_orchestrator.config import Settings


class CheckpointManager:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._context: AbstractAsyncContextManager[Any] | None = None
        self.saver: Any | None = None

    async def open(self) -> Any:
        if self.saver is not None:
            return self.saver
        if self._settings.checkpoint_backend == "memory":
            self.saver = InMemorySaver()
            return self.saver

        from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver

        self._context = AsyncPostgresSaver.from_conn_string(self._settings.database_url)
        self.saver = await self._context.__aenter__()
        await self.saver.setup()
        return self.saver

    async def close(self) -> None:
        if self._context is not None:
            await self._context.__aexit__(None, None, None)
        self._context = None
        self.saver = None

    async def delete_thread(self, namespace: str) -> None:
        if self.saver is not None and hasattr(self.saver, "adelete_thread"):
            await self.saver.adelete_thread(namespace)

    @staticmethod
    def namespace(tenant_id: str, graph_id: str, graph_version: str, thread_id: str) -> str:
        return ":".join((tenant_id, graph_id, graph_version, thread_id))
