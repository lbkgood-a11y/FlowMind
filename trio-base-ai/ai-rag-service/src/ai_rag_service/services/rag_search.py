from __future__ import annotations

from typing import List

from ..db.init_db import get_connection
from ..models.rag import ChunkResult
SIMILARITY_THRESHOLD = 0.7


def search(
    query: str,
    top_k: int = 5,
    *,
    tenant_id: str,
    actor_id: str,
    knowledge_space: str,
) -> List[ChunkResult]:
    # Keep model loading outside module import so health checks and policy tests
    # do not initialize the embedding runtime.
    from .embedding_service import embed

    query_vector = embed(query)
    vector_str = "[" + ",".join(str(v) for v in query_vector) + "]"

    conn = get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            """
            SELECT c.id, c.document_id, c.chunk_index, c.content,
                   1 - (c.embedding <=> %s::vector) AS similarity,
                   d.title, d.source_uri, d.knowledge_space, d.version
            FROM rag_document_chunks c
            JOIN rag_documents d ON c.document_id = d.id
            WHERE d.tenant_id = %s
              AND d.knowledge_space = %s
              AND (d.access_scope = 'AUTHENTICATED' OR d.owner_actor_id = %s)
              AND 1 - (c.embedding <=> %s::vector) >= %s
            ORDER BY similarity DESC
            LIMIT %s
            """,
            (
                vector_str,
                tenant_id,
                knowledge_space,
                actor_id,
                vector_str,
                SIMILARITY_THRESHOLD,
                top_k,
            ),
        )
        rows = cur.fetchall()
        return [
            ChunkResult(
                id=row[0],
                document_id=row[1],
                chunk_index=row[2],
                content=row[3],
                similarity=round(float(row[4]), 4),
                document_title=row[5],
                uri=row[6],
                knowledge_space=row[7],
                version=row[8],
            )
            for row in rows
        ]
    finally:
        cur.close()
        conn.close()


def list_documents(tenant_id: str, knowledge_space: str) -> List[dict]:
    conn = get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            """
            SELECT id, title, chunk_count, source_uri, version, created_at
            FROM rag_documents
            WHERE tenant_id = %s AND knowledge_space = %s
            ORDER BY created_at DESC
            """,
            (tenant_id, knowledge_space),
        )
        rows = cur.fetchall()
        return [
            {
                "id": row[0],
                "title": row[1],
                "chunk_count": row[2],
                "uri": row[3],
                "version": row[4],
                "created_at": str(row[5]),
            }
            for row in rows
        ]
    finally:
        cur.close()
        conn.close()
