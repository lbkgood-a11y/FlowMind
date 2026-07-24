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
                tenant_id   VARCHAR(64) NOT NULL DEFAULT 'default',
                knowledge_space VARCHAR(128) NOT NULL DEFAULT 'enterprise-policies',
                access_scope VARCHAR(32) NOT NULL DEFAULT 'AUTHENTICATED',
                owner_actor_id VARCHAR(128),
                title       VARCHAR(512) NOT NULL,
                source_path VARCHAR(1024),
                source_uri  VARCHAR(2048),
                version     VARCHAR(64) NOT NULL DEFAULT '1',
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

        cur.execute("ALTER TABLE rag_documents ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'")
        cur.execute("ALTER TABLE rag_documents ADD COLUMN IF NOT EXISTS knowledge_space VARCHAR(128) NOT NULL DEFAULT 'enterprise-policies'")
        cur.execute("ALTER TABLE rag_documents ADD COLUMN IF NOT EXISTS access_scope VARCHAR(32) NOT NULL DEFAULT 'AUTHENTICATED'")
        cur.execute("ALTER TABLE rag_documents ADD COLUMN IF NOT EXISTS owner_actor_id VARCHAR(128)")
        cur.execute("ALTER TABLE rag_documents ADD COLUMN IF NOT EXISTS source_uri VARCHAR(2048)")
        cur.execute("ALTER TABLE rag_documents ADD COLUMN IF NOT EXISTS version VARCHAR(64) NOT NULL DEFAULT '1'")

        cur.execute("""
            CREATE INDEX IF NOT EXISTS idx_rag_chunks_doc
                ON rag_document_chunks(document_id)
        """)
        cur.execute("""
            CREATE INDEX IF NOT EXISTS idx_rag_documents_namespace
                ON rag_documents(tenant_id, knowledge_space, created_at DESC)
        """)

        conn.commit()
        cur.close()
        conn.close()
        logger.info("RAG schema initialized")
    except Exception as e:
        logger.warning("RAG schema init skipped (DB not available): %s", e)
