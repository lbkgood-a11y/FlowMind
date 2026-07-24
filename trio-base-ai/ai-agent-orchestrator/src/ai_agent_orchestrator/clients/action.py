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
        payload = await self.request_json(
            "POST",
            "/actions/candidates/validate",
            json_body=candidate.model_dump(mode="json", by_alias=True),
        )
        return ActionValidationResult.model_validate(payload)

    async def dispatch_candidate(self, candidate: ActionCandidate) -> dict[str, Any]:
        payload = await self.request_json(
            "POST",
            "/actions/candidates/dispatch",
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
