from __future__ import annotations

import pytest
from fastapi import HTTPException

from ai_rag_service.config import config
from ai_rag_service.models.rag import AssembleRequest
from ai_rag_service.security import require_admin, trusted_context
from ai_rag_service.services import rag_assembly


@pytest.mark.asyncio
async def test_search_context_requires_trusted_default_tenant() -> None:
    with pytest.raises(HTTPException) as missing:
        await trusted_context(None, "default", "trace-1")
    assert missing.value.status_code == 401

    with pytest.raises(HTTPException) as tenant:
        await trusted_context("U001", "tenant-b", "trace-1")
    assert tenant.value.status_code == 403

    context = await trusted_context("U001", "default", "trace-1")
    assert context.tenant_id == "default"
    assert context.actor_id == "U001"


@pytest.mark.asyncio
async def test_ingestion_requires_admin_token() -> None:
    with pytest.raises(HTTPException) as denied:
        await require_admin("invalid")
    assert denied.value.status_code == 403
    assert await require_admin(config.admin_token) is None


def test_no_evidence_never_falls_back_to_general_knowledge(monkeypatch) -> None:
    monkeypatch.setattr(rag_assembly, "search", lambda *args, **kwargs: [])
    response = rag_assembly.assemble(
        AssembleRequest(query="公司的请假天数是多少？"),
        tenant_id="default",
        actor_id="U001",
    )
    assert response.sources == []
    assert "不得基于通用知识猜测" in response.prompt
