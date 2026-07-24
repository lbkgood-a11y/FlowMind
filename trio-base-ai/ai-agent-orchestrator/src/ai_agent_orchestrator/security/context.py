from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from contextvars import ContextVar
from dataclasses import dataclass

from ai_agent_orchestrator.contracts.models import TrustedRequestContext


@dataclass(frozen=True, slots=True)
class ExecutionCredentials:
    context: TrustedRequestContext
    authorization: str | None = None

    def downstream_headers(self) -> dict[str, str]:
        headers = {
            "X-Tenant-Id": self.context.tenant_id,
            "X-User-Id": self.context.actor.id,
            "X-B3-TraceId": self.context.trace_id,
            "X-Correlation-Id": self.context.correlation_id,
            "Accept-Language": self.context.locale,
        }
        if self.authorization:
            headers["Authorization"] = self.authorization
        return headers


_credentials: ContextVar[ExecutionCredentials | None] = ContextVar(
    "agent_execution_credentials",
    default=None,
)


def get_execution_credentials() -> ExecutionCredentials:
    credentials = _credentials.get()
    if credentials is None:
        raise RuntimeError("TRUSTED_EXECUTION_CONTEXT_MISSING")
    return credentials


@contextmanager
def use_execution_credentials(credentials: ExecutionCredentials) -> Iterator[None]:
    token = _credentials.set(credentials)
    try:
        yield
    finally:
        _credentials.reset(token)
