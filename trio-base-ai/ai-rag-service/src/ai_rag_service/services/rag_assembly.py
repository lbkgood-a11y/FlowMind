from __future__ import annotations

from ..models.rag import AssembleRequest, AssembleResponse
from .rag_search import search


def assemble(
    request: AssembleRequest,
    *,
    tenant_id: str,
    actor_id: str,
) -> AssembleResponse:
    chunks = search(
        request.query,
        request.top_k,
        tenant_id=tenant_id,
        actor_id=actor_id,
        knowledge_space=request.knowledge_space,
    )

    sources = [
        {
            "id": c.id,
            "document_id": c.document_id,
            "title": c.document_title,
            "uri": c.uri,
            "knowledge_space": c.knowledge_space,
            "version": c.version,
            "chunk_index": c.chunk_index,
            "similarity": c.similarity,
        }
        for c in chunks
    ]

    if not chunks:
        prompt = "未找到有权限访问的相关资料。不得基于通用知识猜测企业答案。"
    else:
        context_parts = []
        for i, c in enumerate(chunks, 1):
            context_parts.append(f"[来源{i}: {c.document_title}] {c.content}")
        context = "\n---\n".join(context_parts)
        prompt = (
            f"基于以下参考资料回答问题:\n\n{context}\n\n"
            f"问题: {request.query}"
        )

    return AssembleResponse(prompt=prompt, sources=sources)
