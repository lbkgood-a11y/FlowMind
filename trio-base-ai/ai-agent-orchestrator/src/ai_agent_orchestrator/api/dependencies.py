from __future__ import annotations

from uuid import uuid4

from fastapi import Header, HTTPException, Request

from ai_agent_orchestrator.config import Settings
from ai_agent_orchestrator.contracts.models import AgentActor, TrustedRequestContext
from ai_agent_orchestrator.runtime.run_service import AgentRunService


def run_service(request: Request) -> AgentRunService:
    return request.app.state.run_service


def settings(request: Request) -> Settings:
    return request.app.state.settings


async def trusted_context(
    request: Request,
    x_user_id: str | None = Header(default=None, alias="X-User-Id"),
    x_user_name: str | None = Header(default=None, alias="X-User-Name"),
    x_username: str | None = Header(default=None, alias="X-Username"),
    x_tenant_id: str | None = Header(default=None, alias="X-Tenant-Id"),
    x_b3_trace_id: str | None = Header(default=None, alias="X-B3-TraceId"),
    traceparent: str | None = Header(default=None, alias="traceparent"),
    x_correlation_id: str | None = Header(default=None, alias="X-Correlation-Id"),
    x_request_id: str | None = Header(default=None, alias="X-Request-Id"),
    accept_language: str | None = Header(default=None, alias="Accept-Language"),
) -> TrustedRequestContext:
    config: Settings = request.app.state.settings
    if not x_user_id:
        if not config.allow_development_identity or config.environment == "production":
            raise HTTPException(status_code=401, detail="TRUSTED_USER_CONTEXT_REQUIRED")
        x_user_id = "dev-user"
        x_user_name = "Development User"
    tenant_id = x_tenant_id or config.default_tenant_id
    if tenant_id != config.default_tenant_id:
        raise HTTPException(status_code=403, detail="TENANT_NOT_AVAILABLE")
    trace_id = x_b3_trace_id or _trace_id_from_traceparent(traceparent)
    if not trace_id:
        if config.environment == "production":
            raise HTTPException(status_code=400, detail="TRACE_CONTEXT_REQUIRED")
        trace_id = uuid4().hex
    correlation_id = x_correlation_id or x_request_id or str(uuid4())
    return TrustedRequestContext(
        tenantId=tenant_id,
        actor=AgentActor(
            id=x_user_id,
            displayName=x_username or x_user_name,
            tenantId=tenant_id,
        ),
        traceId=trace_id,
        correlationId=correlation_id,
        requestId=x_request_id,
        locale=(accept_language or "zh-CN").split(",", 1)[0],
    )


def authorization_header(request: Request) -> str | None:
    return request.headers.get("Authorization")


def _trace_id_from_traceparent(traceparent: str | None) -> str | None:
    if not traceparent:
        return None
    parts = traceparent.split("-")
    if len(parts) == 4 and len(parts[1]) == 32:
        return parts[1]
    return None
