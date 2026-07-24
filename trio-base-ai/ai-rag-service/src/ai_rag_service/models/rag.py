from __future__ import annotations

from typing import List, Literal

from pydantic import BaseModel, Field


class DocumentIngestRequest(BaseModel):
    title: str
    content: str
    knowledge_space: str = "enterprise-policies"
    access_scope: Literal["AUTHENTICATED", "PRIVATE"] = "AUTHENTICATED"
    source_uri: str = ""
    version: str = "1"


class SearchRequest(BaseModel):
    query: str
    top_k: int = Field(default=5, ge=1, le=20)


class AssembleRequest(BaseModel):
    query: str
    top_k: int = Field(default=3, ge=1, le=10)
    knowledge_space: str = "enterprise-policies"


class ChunkResult(BaseModel):
    id: str
    document_id: str
    chunk_index: int
    content: str
    similarity: float
    document_title: str
    uri: str | None = None
    knowledge_space: str
    version: str


class SearchResponse(BaseModel):
    chunks: List[ChunkResult]
    message: str = ""


class AssembleResponse(BaseModel):
    prompt: str
    sources: List[dict]
