from __future__ import annotations

import logging

import psycopg2
from psycopg2.extensions import connection as PgConnection

from ..config import config

logger = logging.getLogger(__name__)


def get_connection() -> PgConnection:
    return psycopg2.connect(config.database_url)


def init_rag_schema() -> None:
    try:
        conn = get_connection()
        cur = conn.cursor()

        cur.execute("CREATE EXTENSION IF NOT EXISTS vector")

        cur.execute("""
            CREATE TABLE IF NOT EXISTS rag_documents (
                id          VARCHAR(32) PRIMARY KEY,
                title       VARCHAR(512) NOT NULL,
                source_path VARCHAR(1024),
                chunk_count INTEGER NOT NULL DEFAULT 0,
                created_at  TIMESTAMP NOT NULL DEFAULT NOW()
            )
        """)

        cur.execute("""
            CREATE TABLE IF NOT EXISTS rag_document_chunks (
                id            VARCHAR(32) PRIMARY KEY,
                document_id   VARCHAR(32) NOT NULL REFERENCES rag_documents(id) ON DELETE CASCADE,
                chunk_index   INTEGER NOT NULL,
                content       TEXT NOT NULL,
                embedding     vector(384),
                created_at    TIMESTAMP NOT NULL DEFAULT NOW()
            )
        """)

        cur.execute("""
            CREATE INDEX IF NOT EXISTS idx_rag_chunks_doc
                ON rag_document_chunks(document_id)
        """)

        conn.commit()
        cur.close()
        conn.close()
        logger.info("RAG schema initialized")
    except Exception as e:
        logger.warning("RAG schema init skipped (DB not available): %s", e)
