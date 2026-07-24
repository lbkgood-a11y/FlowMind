from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException
from starlette.concurrency import run_in_threadpool

from ..models.rag import AssembleRequest, AssembleResponse, DocumentIngestRequest, SearchResponse
from ..security import RagAccessContext, require_admin, trusted_context
from ..services.rag_assembly import assemble
from ..services.rag_ingestion import delete_document, ingest_document, scan_local_docs
from ..services.rag_search import list_documents, search

router = APIRouter()
Access = Annotated[RagAccessContext, Depends(trusted_context)]
Admin = Annotated[None, Depends(require_admin)]


@router.post("/api/v1/rag/documents")
async def ingest(req: DocumentIngestRequest, context: Access, _: Admin):
    try:
        return await run_in_threadpool(
            ingest_document,
            req.title,
            req.content,
            tenant_id=context.tenant_id,
            actor_id=context.actor_id,
            knowledge_space=req.knowledge_space,
            access_scope=req.access_scope,
            source_uri=req.source_uri,
            version=req.version,
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/api/v1/rag/documents")
async def list_docs(
    context: Access,
    knowledge_space: str = "enterprise-policies",
):
    return await run_in_threadpool(list_documents, context.tenant_id, knowledge_space)


@router.delete("/api/v1/rag/documents/{doc_id}")
async def delete_doc(doc_id: str, context: Access, _: Admin):
    if not await run_in_threadpool(delete_document, doc_id, context.tenant_id):
        raise HTTPException(status_code=404, detail="DOCUMENT_NOT_FOUND")
    return {"status": "deleted"}


@router.get("/api/v1/rag/search")
async def search_rag(
    context: Access,
    q: str,
    top_k: int = 5,
    knowledge_space: str = "enterprise-policies",
):
    if top_k > 20:
        top_k = 20
    chunks = await run_in_threadpool(
        search,
        q,
        top_k,
        tenant_id=context.tenant_id,
        actor_id=context.actor_id,
        knowledge_space=knowledge_space,
    )
    if not chunks:
        return SearchResponse(chunks=[], message="No relevant content found")
    return SearchResponse(chunks=chunks, message="")


@router.post("/api/v1/rag/assemble")
async def assemble_prompt(req: AssembleRequest, context: Access) -> AssembleResponse:
    return await run_in_threadpool(
        assemble,
        req,
        tenant_id=context.tenant_id,
        actor_id=context.actor_id,
    )


@router.post("/api/v1/rag/scan")
async def scan_local(context: Access, _: Admin):
    results = await run_in_threadpool(
        scan_local_docs,
        "docs",
        tenant_id=context.tenant_id,
        actor_id=context.actor_id,
        knowledge_space="enterprise-policies",
    )
    return {"scanned": len(results), "results": results}
