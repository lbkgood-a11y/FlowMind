from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

import pytest

from ai_agent_orchestrator.clients.action import ActionClient, _action_base_path
from ai_agent_orchestrator.contracts.models import (
    ActionCandidate,
    ActionContext,
    ActionTarget,
    AgentActor,
)


def test_routes_lowcode_candidate_to_owner_hosted_endpoint() -> None:
    candidate = _candidate("lowcode.form.submit", "service-lowcode")

    assert _action_base_path(candidate) == "/lowcode-runtime/actions"


def test_routes_workflow_candidate_to_owner_hosted_endpoint() -> None:
    candidate = _candidate("process.task.approve", "service-workflow-engine")

    assert _action_base_path(candidate) == "/workflow-actions"


def test_rejects_unknown_action_owner() -> None:
    candidate = _candidate("custom.action.run", "service-custom")

    with pytest.raises(ValueError, match="ACTION_OWNER_UNRESOLVED"):
        _action_base_path(candidate)


@pytest.mark.asyncio
async def test_dispatches_lowcode_leave_candidate_to_owner_endpoint() -> None:
    client = FakeActionClient(
        {
            "actionId": "ACT001",
            "actionType": "lowcode.form.submit",
            "status": "SUCCEEDED",
            "ownerService": "service-lowcode",
            "ownerExecutionRef": "leave-001",
        }
    )
    candidate = _candidate("lowcode.form.submit", "service-lowcode")
    candidate.payload = {
        "appKey": "leave",
        "actionCode": "submitAndLaunch",
        "data": {"reason": "family"},
    }

    result = await client.dispatch_candidate(candidate)

    assert client.calls[0]["path"] == "/lowcode-runtime/actions/candidates/dispatch"
    assert client.calls[0]["json_body"]["payload"]["appKey"] == "leave"
    assert result["status"] == "SUCCEEDED"
    assert result["ownerExecutionRef"] == "leave-001"


@pytest.mark.asyncio
async def test_validates_workflow_candidate_rejection_from_owner_endpoint() -> None:
    client = FakeActionClient(
        {
            "candidateId": "cand-1",
            "actionType": "process.task.approve",
            "valid": True,
            "definitionExists": True,
            "schemaValid": True,
            "enabled": False,
            "dispatchable": False,
            "disabledReason": "PROCESS_TASK_ACTIONABLE",
            "errors": [{"code": "PROCESS_TASK_ACTIONABLE", "category": "GUARD"}],
        }
    )

    result = await client.validate_candidate(
        _candidate("process.task.approve", "service-workflow-engine")
    )

    assert client.calls[0]["path"] == "/workflow-actions/candidates/validate"
    assert result.action_type == "process.task.approve"
    assert not result.dispatchable
    assert result.errors[0]["code"] == "PROCESS_TASK_ACTIONABLE"


@pytest.mark.asyncio
async def test_dispatch_rejects_non_object_action_result() -> None:
    client = FakeActionClient(["not", "a", "dict"])

    with pytest.raises(ValueError, match="ACTION_RESULT_INVALID"):
        await client.dispatch_candidate(_candidate("lowcode.form.submit", "service-lowcode"))


def _candidate(action_type: str, owner_service: str) -> ActionCandidate:
    now = datetime.now(UTC)
    actor = AgentActor(id="user-1", display_name="Ada")
    return ActionCandidate(
        candidate_id="cand-1",
        action_type=action_type,
        actor=actor,
        target=ActionTarget(owner_service=owner_service),
        payload={"reason": "test"},
        context=ActionContext(
            trace_id="trace-1",
            correlation_id="corr-1",
        ),
        idempotency_key="idem-1",
        reason="test",
        created_at=now,
    )


class FakeActionClient(ActionClient):
    def __init__(self, response: Any) -> None:
        self.response = response
        self.calls: list[dict[str, Any]] = []

    async def request_json(
        self,
        method: str,
        path: str,
        *,
        params: dict[str, Any] | None = None,
        json_body: Any = None,
    ) -> Any:
        self.calls.append(
            {
                "json_body": json_body,
                "method": method,
                "params": params,
                "path": path,
            }
        )
        return self.response
