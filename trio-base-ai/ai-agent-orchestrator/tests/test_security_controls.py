from __future__ import annotations

import pytest
from pydantic import ValidationError

from ai_agent_orchestrator.contracts.models import AgentActor, AgentState
from ai_agent_orchestrator.security.redaction import minimize_state_data


def test_sensitive_state_is_redacted_before_persistence_or_events() -> None:
    minimized = minimize_state_data(
        {
            "authorization": "Bearer top-secret",
            "message": "手机号13800138000，身份证110101199001011234，卡号6222020202020202",
        }
    )
    serialized = str(minimized)
    assert "top-secret" not in serialized
    assert "13800138000" not in serialized
    assert "110101199001011234" not in serialized
    assert "6222020202020202" not in serialized


def test_actor_and_state_reject_identity_tenant_override() -> None:
    with pytest.raises(ValidationError, match="trusted default tenant"):
        AgentActor(id="attacker", tenantId="other")

    with pytest.raises(ValidationError, match="default"):
        AgentState(
            graphId="triobase-assistant",
            graphVersion="1.0.0",
            threadId="thread-1",
            runId="run-1",
            tenantId="other",
            actor=AgentActor(id="U001"),
            traceId="trace-1",
            correlationId="correlation-1",
            message="override tenant",
        )
