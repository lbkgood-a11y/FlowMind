from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True, slots=True)
class GraphRegistration:
    graph_id: str
    graph_version: str
    state_schema_version: str
    graph: Any


class GraphRegistry:
    def __init__(self) -> None:
        self._graphs: dict[tuple[str, str], GraphRegistration] = {}
        self._latest: dict[str, str] = {}

    def register(self, registration: GraphRegistration, *, latest: bool = True) -> None:
        key = (registration.graph_id, registration.graph_version)
        if key in self._graphs:
            raise ValueError(f"graph already registered: {key}")
        self._graphs[key] = registration
        if latest:
            self._latest[registration.graph_id] = registration.graph_version

    def resolve(self, graph_id: str, graph_version: str | None = None) -> GraphRegistration:
        version = graph_version or self._latest.get(graph_id)
        if not version:
            raise KeyError(f"graph not registered: {graph_id}")
        registration = self._graphs.get((graph_id, version))
        if not registration:
            raise KeyError(f"graph version not registered: {graph_id}@{version}")
        return registration

    def require_compatible(
        self,
        graph_id: str,
        graph_version: str,
        state_schema_version: str,
    ) -> GraphRegistration:
        registration = self.resolve(graph_id, graph_version)
        if registration.state_schema_version != state_schema_version:
            raise ValueError(
                "AGENT_STATE_MIGRATION_REQUIRED: "
                f"checkpoint={state_schema_version}, runtime={registration.state_schema_version}"
            )
        return registration
