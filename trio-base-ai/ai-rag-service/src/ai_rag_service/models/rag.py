from __future__ import annotations

from typing import List

from pydantic import BaseModel, Field


class DocumentIngestRequest(BaseModel):
    title: str
    content: str


class SearchRequest(BaseModel):
    query: str
    top_k: int = Field(default=5, ge=1, le=20)


class AssembleRequest(BaseModel):
    query: str
    top_k: int = Field(default=3, ge=1, le=10)


class ChunkResult(BaseModel):
    chunk_index: int
    content: str
    similarity: float
    document_title: str


class SearchResponse(BaseModel):
    chunks: List[ChunkResult]
    message: str = ""


class AssembleResponse(BaseModel):
    prompt: str
    sources: List[dict]
