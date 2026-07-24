from __future__ import annotations

from operator import add
from typing import Annotated, Any, TypedDict


class AgentGraphState(TypedDict, total=False):
    agent: dict[str, Any]
    model: str
    descriptor: dict[str, Any]
    validation: dict[str, Any]
    action_result: dict[str, Any]
    events: Annotated[list[dict[str, Any]], add]
