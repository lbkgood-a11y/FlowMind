from __future__ import annotations

import json
from typing import Any

from pydantic import BaseModel, ConfigDict, Field

from .base import GovernedHttpClient


class RuntimeApplicationAction(BaseModel):
    model_config = ConfigDict(
        alias_generator=lambda value: value.split("_")[0]
        + "".join(part[:1].upper() + part[1:] for part in value.split("_")[1:]),
        populate_by_name=True,
        extra="allow",
    )

    action_code: str
    action_type: str
    label: str
    allowed: bool = True
    process_key: str | None = None
    metadata_json: str | None = None

    def global_action_type(self) -> str:
        action_types = {
            "CREATE": "lowcode.form.create",
            "SAVE": "lowcode.form.save",
            "SUBMIT": "lowcode.form.submit",
            "SUBMIT_AND_LAUNCH_WORKFLOW": "lowcode.form.submit",
        }
        try:
            return action_types[self.action_type]
        except KeyError as exc:
            raise ValueError("LOWCODE_MUTATION_ACTION_UNSUPPORTED") from exc

    def launches_workflow(self) -> bool:
        return self.action_type == "SUBMIT_AND_LAUNCH_WORKFLOW"


class RuntimeFieldRule(BaseModel):
    model_config = ConfigDict(
        alias_generator=lambda value: value.split("_")[0]
        + "".join(part[:1].upper() + part[1:] for part in value.split("_")[1:]),
        populate_by_name=True,
        extra="allow",
    )

    field_key: str
    read_mode: str = "VISIBLE"
    write_mode: str = "EDITABLE"


class RuntimeApplicationDescriptor(BaseModel):
    model_config = ConfigDict(
        alias_generator=lambda value: value.split("_")[0]
        + "".join(part[:1].upper() + part[1:] for part in value.split("_")[1:]),
        populate_by_name=True,
        extra="allow",
    )

    app_key: str
    name: str
    form_key: str
    version: int
    schema_text: str = Field(alias="schemaJson")
    actions: list[RuntimeApplicationAction] = Field(default_factory=list)
    field_rules: list[RuntimeFieldRule] = Field(default_factory=list)

    def schema(self) -> dict[str, Any]:
        value = json.loads(self.schema_text)
        if not isinstance(value, dict):
            raise ValueError("LOWCODE_SCHEMA_INVALID")
        return value

    def primary_mutation_action(self) -> RuntimeApplicationAction:
        priority = {
            "SUBMIT_AND_LAUNCH_WORKFLOW": 0,
            "SUBMIT": 1,
            "CREATE": 2,
            "SAVE": 3,
        }
        candidates = [
            action
            for action in self.actions
            if action.allowed and action.action_type in priority
        ]
        if not candidates:
            raise ValueError("LOWCODE_MUTATION_ACTION_UNAVAILABLE")
        return min(candidates, key=lambda action: priority[action.action_type])


class LowcodeClient(GovernedHttpClient):
    async def get_runtime_application(
        self,
        app_key: str,
        version: int | None = None,
    ) -> RuntimeApplicationDescriptor:
        payload = await self.request_json(
            "GET",
            f"/lowcode-runtime/apps/{app_key}",
            params={"version": version} if version is not None else None,
        )
        return RuntimeApplicationDescriptor.model_validate(payload)

    async def list_runtime_applications(self, keyword: str | None = None) -> list[dict[str, Any]]:
        payload = await self.request_json(
            "GET",
            "/lowcode-runtime/apps",
            params={"page": 1, "size": 50, "keyword": keyword}
            if keyword
            else {"page": 1, "size": 50},
        )
        if isinstance(payload, dict):
            return list(payload.get("records") or [])
        return []
