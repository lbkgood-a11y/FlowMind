from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field

from ai_agent_orchestrator.contracts.models import ActionCandidate

from .base import GovernedHttpClient


class ActionValidationResult(BaseModel):
    model_config = ConfigDict(
        alias_generator=lambda value: value.split("_")[0]
        + "".join(part[:1].upper() + part[1:] for part in value.split("_")[1:]),
        populate_by_name=True,
        extra="allow",
    )

    candidate_id: str | None = None
    action_type: str | None = None
    valid: bool = False
    definition_exists: bool = False
    schema_valid: bool = False
    visible: bool = True
    enabled: bool = False
    dispatchable: bool = False
    disabled_reason: str | None = None
    requires_confirmation: bool = True
    confirmation_satisfied: bool = False
    confirmation: dict[str, Any] | None = None
    errors: list[dict[str, Any]] = Field(default_factory=list)
    refresh_scopes: list[str] = Field(default_factory=list)


class ActionClient(GovernedHttpClient):
    async def validate_candidate(self, candidate: ActionCandidate) -> ActionValidationResult:
        base_path = _action_base_path(candidate)
        payload = await self.request_json(
            "POST",
            f"{base_path}/candidates/validate",
            json_body=candidate.model_dump(mode="json", by_alias=True),
        )
        return ActionValidationResult.model_validate(payload)

    async def dispatch_candidate(self, candidate: ActionCandidate) -> dict[str, Any]:
        base_path = _action_base_path(candidate)
        payload = await self.request_json(
            "POST",
            f"{base_path}/candidates/dispatch",
            json_body=candidate.model_dump(mode="json", by_alias=True),
        )
        if not isinstance(payload, dict):
            raise ValueError("ACTION_RESULT_INVALID")
        return payload

    async def get_action(self, action_id: str) -> dict[str, Any]:
        payload = await self.request_json("GET", f"/actions/{action_id}")
        if not isinstance(payload, dict):
            raise ValueError("ACTION_RESULT_INVALID")
        return payload


OWNER_ACTION_BASE_PATHS = {
    "service-lowcode": "/lowcode-runtime/actions",
    "service-workflow-engine": "/workflow-actions",
    "service-openapi": "/openapi/management/actions",
}


def _action_base_path(candidate: ActionCandidate) -> str:
    owner_service = candidate.target.owner_service if candidate.target else None
    if owner_service in OWNER_ACTION_BASE_PATHS:
        return OWNER_ACTION_BASE_PATHS[owner_service]
    action_type = candidate.action_type
    if action_type.startswith("lowcode."):
        return OWNER_ACTION_BASE_PATHS["service-lowcode"]
    if action_type.startswith("process."):
        return OWNER_ACTION_BASE_PATHS["service-workflow-engine"]
    if action_type.startswith("integration."):
        return OWNER_ACTION_BASE_PATHS["service-openapi"]
    raise ValueError("ACTION_OWNER_UNRESOLVED")
