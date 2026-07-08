from __future__ import annotations

from fastapi import APIRouter, HTTPException

from ..models.rag import AssembleRequest, AssembleResponse, DocumentIngestRequest, SearchResponse
from ..services.rag_assembly import assemble
from ..services.rag_ingestion import delete_document, ingest_document, scan_local_docs
from ..services.rag_search import list_documents, search

router = APIRouter()


@router.post("/api/v1/rag/documents")
async def ingest(req: DocumentIngestRequest):
    try:
        return ingest_document(req.title, req.content)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/api/v1/rag/documents")
async def list_docs():
    return list_documents()


@router.delete("/api/v1/rag/documents/{doc_id}")
async def delete_doc(doc_id: str):
    if not delete_document(doc_id):
        raise HTTPException(status_code=404, detail="DOCUMENT_NOT_FOUND")
    return {"status": "deleted"}


@router.get("/api/v1/rag/search")
async def search_rag(q: str, top_k: int = 5):
    if top_k > 20:
        top_k = 20
    chunks = search(q, top_k)
    if not chunks:
        return SearchResponse(chunks=[], message="No relevant content found")
    return SearchResponse(chunks=chunks, message="")


@router.post("/api/v1/rag/assemble")
async def assemble_prompt(req: AssembleRequest) -> AssembleResponse:
    return assemble(req)


@router.post("/api/v1/rag/scan")
async def scan_local():
    results = scan_local_docs("docs")
    return {"scanned": len(results), "results": results}
