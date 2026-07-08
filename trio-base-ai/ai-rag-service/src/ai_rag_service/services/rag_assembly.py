from __future__ import annotations

from ..models.rag import AssembleRequest, AssembleResponse
from .rag_search import search


def assemble(request: AssembleRequest) -> AssembleResponse:
    chunks = search(request.query, request.top_k)

    sources = [
        {"title": c.document_title, "chunk_index": c.chunk_index, "similarity": c.similarity}
        for c in chunks
    ]

    if not chunks:
        prompt = (
            "未找到相关资料，请基于通用知识回答问题。\n"
            f"问题: {request.query}"
        )
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
