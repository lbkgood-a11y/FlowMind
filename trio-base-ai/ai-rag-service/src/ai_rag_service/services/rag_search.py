from __future__ import annotations

from typing import List

from ..db.init_db import get_connection
from ..models.rag import ChunkResult
from .embedding_service import embed

SIMILARITY_THRESHOLD = 0.7


def search(query: str, top_k: int = 5) -> List[ChunkResult]:
    query_vector = embed(query)
    vector_str = "[" + ",".join(str(v) for v in query_vector) + "]"

    conn = get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            """
            SELECT c.chunk_index, c.content, 1 - (c.embedding <=> %s::vector) AS similarity,
                   d.title
            FROM rag_document_chunks c
            JOIN rag_documents d ON c.document_id = d.id
            WHERE 1 - (c.embedding <=> %s::vector) >= %s
            ORDER BY similarity DESC
            LIMIT %s
            """,
            (vector_str, vector_str, SIMILARITY_THRESHOLD, top_k),
        )
        rows = cur.fetchall()
        return [
            ChunkResult(
                chunk_index=row[0],
                content=row[1],
                similarity=round(float(row[2]), 4),
                document_title=row[3],
            )
            for row in rows
        ]
    finally:
        cur.close()
        conn.close()


def list_documents() -> List[dict]:
    conn = get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            "SELECT id, title, chunk_count, created_at FROM rag_documents ORDER BY created_at DESC"
        )
        rows = cur.fetchall()
        return [
            {
                "id": row[0],
                "title": row[1],
                "chunk_count": row[2],
                "created_at": str(row[3]),
            }
            for row in rows
        ]
    finally:
        cur.close()
        conn.close()
