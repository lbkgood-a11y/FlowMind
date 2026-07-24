from __future__ import annotations

import logging
import os

from ..db.init_db import get_connection
from ..ids import new_ulid
logger = logging.getLogger(__name__)

CHUNK_SIZE = 500
CHUNK_OVERLAP = 50


def split_text(text: str, chunk_size: int = CHUNK_SIZE, overlap: int = CHUNK_OVERLAP) -> list[str]:
    if not text.strip():
        return []
    chunks = []
    start = 0
    while start < len(text):
        end = min(start + chunk_size, len(text))
        chunks.append(text[start:end].strip())
        if end >= len(text):
            break
        start = end - overlap
    return chunks


def ingest_document(
    title: str,
    content: str,
    *,
    tenant_id: str,
    actor_id: str,
    knowledge_space: str,
    access_scope: str = "AUTHENTICATED",
    source_path: str = "",
    source_uri: str = "",
    version: str = "1",
) -> dict:
    # Embedding dependencies are intentionally lazy: startup/readiness must not
    # download or initialize a model before an ingestion request arrives.
    from .embedding_service import embed_batch

    chunks = split_text(content)
    if not chunks:
        raise ValueError("EMPTY_DOCUMENT")

    doc_id = new_ulid()
    vectors = embed_batch(chunks)

    conn = get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            """
            INSERT INTO rag_documents (
                id, tenant_id, knowledge_space, access_scope, owner_actor_id,
                title, source_path, source_uri, version, chunk_count
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """,
            (
                doc_id,
                tenant_id,
                knowledge_space,
                access_scope,
                actor_id,
                title,
                source_path,
                source_uri,
                version,
                len(chunks),
            ),
        )
        for i, (chunk, vector) in enumerate(zip(chunks, vectors)):
            chunk_id = new_ulid()
            vector_str = "[" + ",".join(str(v) for v in vector) + "]"
            cur.execute(
                "INSERT INTO rag_document_chunks (id, document_id, chunk_index, content, embedding) "
                "VALUES (%s, %s, %s, %s, %s::vector)",
                (chunk_id, doc_id, i, chunk, vector_str),
            )
        conn.commit()
        logger.info("Document ingested: %s (%d chunks)", title, len(chunks))
        return {"document_id": doc_id, "chunk_count": len(chunks)}
    except Exception:
        conn.rollback()
        raise
    finally:
        cur.close()
        conn.close()


def delete_document(doc_id: str, tenant_id: str) -> bool:
    conn = get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            "SELECT 1 FROM rag_documents WHERE id = %s AND tenant_id = %s",
            (doc_id, tenant_id),
        )
        if not cur.fetchone():
            return False
        cur.execute(
            "DELETE FROM rag_documents WHERE id = %s AND tenant_id = %s",
            (doc_id, tenant_id),
        )
        conn.commit()
        return True
    finally:
        cur.close()
        conn.close()


def scan_local_docs(
    docs_dir: str = "docs",
    *,
    tenant_id: str = "default",
    actor_id: str = "system",
    knowledge_space: str = "enterprise-policies",
) -> list[dict]:
    if not os.path.isdir(docs_dir):
        logger.info("Docs directory '%s' not found, skipping scan", docs_dir)
        return []

    results = []
    for root, _, files in os.walk(docs_dir):
        for fname in files:
            if not fname.endswith(".md"):
                continue
            fpath = os.path.join(root, fname)
            try:
                with open(fpath, "r", encoding="utf-8") as f:
                    content = f.read()
                title = fname.replace(".md", "").replace("_", " ").title()
                result = ingest_document(
                    title,
                    content,
                    tenant_id=tenant_id,
                    actor_id=actor_id,
                    knowledge_space=knowledge_space,
                    source_path=fpath,
                    source_uri=f"docs://{fpath.replace(os.sep, '/')}",
                )
                results.append({"path": fpath, **result})
                logger.info("Scanned: %s → %s", fpath, result["document_id"])
            except Exception as e:
                logger.error("Failed to scan %s: %s", fpath, e)
    return results
