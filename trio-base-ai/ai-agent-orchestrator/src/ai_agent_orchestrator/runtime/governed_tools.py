from __future__ import annotations

from pydantic import BaseModel, Field

from ai_agent_orchestrator.clients import ActionClient, LowcodeClient, RagClient
from ai_agent_orchestrator.clients.action import ActionValidationResult
from ai_agent_orchestrator.clients.lowcode import RuntimeApplicationDescriptor
from ai_agent_orchestrator.contracts.models import ActionCandidate, AgentEvidence

from .tool_registry import ToolKind, ToolRegistration, ToolRegistry


class LowcodeApplicationReadInput(BaseModel):
    app_key: str = Field(min_length=1, max_length=128)
    version: int | None = Field(default=None, ge=1)


class KnowledgeSearchInput(BaseModel):
    query: str = Field(min_length=1, max_length=16_000)
    knowledge_space: str = Field(min_length=1, max_length=128)
    top_k: int = Field(default=5, ge=1, le=20)


class KnowledgeSearchOutput(BaseModel):
    evidence: list[AgentEvidence]


class ActionCandidateValidationInput(BaseModel):
    candidate: ActionCandidate


def create_governed_tool_registry(
    *,
    max_result_bytes: int,
    lowcode: LowcodeClient,
    rag: RagClient,
    action: ActionClient,
) -> ToolRegistry:
    registry = ToolRegistry(max_result_bytes)

    async def read_lowcode(value: BaseModel) -> BaseModel:
        parsed = LowcodeApplicationReadInput.model_validate(value)
        return await lowcode.get_runtime_application(parsed.app_key, parsed.version)

    async def search_knowledge(value: BaseModel) -> BaseModel:
        parsed = KnowledgeSearchInput.model_validate(value)
        evidence = await rag.search(
            parsed.query,
            knowledge_space=parsed.knowledge_space,
            top_k=parsed.top_k,
        )
        return KnowledgeSearchOutput(evidence=evidence)

    async def validate_action(value: BaseModel) -> BaseModel:
        parsed = ActionCandidateValidationInput.model_validate(value)
        return await action.validate_candidate(parsed.candidate)

    registry.register(
        ToolRegistration(
            name="lowcode.runtime.read",
            kind=ToolKind.READ,
            input_schema=LowcodeApplicationReadInput,
            output_schema=RuntimeApplicationDescriptor,
            handler=read_lowcode,
        )
    )
    registry.register(
        ToolRegistration(
            name="knowledge.search",
            kind=ToolKind.RETRIEVAL,
            input_schema=KnowledgeSearchInput,
            output_schema=KnowledgeSearchOutput,
            handler=search_knowledge,
        )
    )
    registry.register(
        ToolRegistration(
            name="action.candidate.validate",
            kind=ToolKind.ACTION_CANDIDATE,
            input_schema=ActionCandidateValidationInput,
            output_schema=ActionValidationResult,
            handler=validate_action,
        )
    )
    return registry
