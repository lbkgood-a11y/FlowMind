from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum

from pydantic import BaseModel


class NodeKind(StrEnum):
    DETERMINISTIC = "DETERMINISTIC"
    MODEL = "MODEL"
    IO = "IO"
    INTERRUPT = "INTERRUPT"


class Sensitivity(StrEnum):
    PUBLIC = "PUBLIC"
    INTERNAL = "INTERNAL"
    SENSITIVE = "SENSITIVE"


@dataclass(frozen=True, slots=True)
class NodeContract:
    name: str
    kind: NodeKind
    input_schema: type[BaseModel]
    output_schema: type[BaseModel]
    timeout_seconds: float
    retryable: bool
    sensitivity: Sensitivity
    observability_name: str

    def __post_init__(self) -> None:
        if self.timeout_seconds <= 0:
            raise ValueError("node timeout must be positive")
        if self.kind == NodeKind.INTERRUPT and self.retryable:
            raise ValueError("interrupt nodes cannot be retried")
