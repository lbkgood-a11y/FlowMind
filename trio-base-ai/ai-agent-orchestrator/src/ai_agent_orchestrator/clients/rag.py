from __future__ import annotations

from ai_agent_orchestrator.contracts.models import AgentEvidence

from .base import GovernedHttpClient


class RagClient(GovernedHttpClient):
    async def search(
        self,
        query: str,
        *,
        knowledge_space: str,
        top_k: int = 5,
    ) -> list[AgentEvidence]:
        payload = await self.request_json(
            "GET",
            "/api/v1/rag/search",
            params={"q": query, "top_k": min(top_k, 20), "knowledge_space": knowledge_space},
        )
        if not isinstance(payload, dict):
            return []
        evidence: list[AgentEvidence] = []
        for index, chunk in enumerate(payload.get("chunks") or []):
            if not isinstance(chunk, dict):
                continue
            evidence.append(
                AgentEvidence(
                    evidenceId=str(
                        chunk.get("id") or chunk.get("chunk_id") or f"evidence-{index + 1}"
                    ),
                    title=str(chunk.get("title") or chunk.get("document_title") or "知识库内容"),
                    uri=chunk.get("uri") or chunk.get("source"),
                    excerpt=str(chunk.get("content") or chunk.get("text") or "")[:2000],
                    score=chunk.get("score") or chunk.get("similarity"),
                    knowledgeSpace=knowledge_space,
                )
            )
        return evidence
