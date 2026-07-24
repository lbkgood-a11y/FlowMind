from __future__ import annotations

from dataclasses import dataclass

from fastapi import Header, HTTPException

from .config import config


@dataclass(frozen=True, slots=True)
class RagAccessContext:
    tenant_id: str
    actor_id: str
    trace_id: str


async def trusted_context(
    x_user_id: str | None = Header(default=None, alias="X-User-Id"),
    x_tenant_id: str | None = Header(default=None, alias="X-Tenant-Id"),
    x_b3_trace_id: str | None = Header(default=None, alias="X-B3-TraceId"),
) -> RagAccessContext:
    if not x_user_id:
        raise HTTPException(status_code=401, detail="TRUSTED_USER_CONTEXT_REQUIRED")
    tenant_id = x_tenant_id or config.default_tenant_id
    if tenant_id != config.default_tenant_id:
        raise HTTPException(status_code=403, detail="TENANT_NOT_AVAILABLE")
    if not x_b3_trace_id:
        raise HTTPException(status_code=400, detail="TRACE_CONTEXT_REQUIRED")
    return RagAccessContext(tenant_id=tenant_id, actor_id=x_user_id, trace_id=x_b3_trace_id)


async def require_admin(
    x_rag_admin_token: str | None = Header(default=None, alias="X-RAG-Admin-Token"),
) -> None:
    if not x_rag_admin_token or x_rag_admin_token != config.admin_token:
        raise HTTPException(status_code=403, detail="RAG_ADMIN_REQUIRED")
