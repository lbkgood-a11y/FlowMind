## Context

`trio-base-data` currently contains `data-pipeline`, `data-embedding`, and `data-analytics` modules, but they do not yet expose a working enterprise data capability. The workflow MVP can run processes and consume lowcode form snapshots, while the data layer still lacks a catalog, ingestion path, query contract, and frontend validation surface.

The MVP must respect the platform direction: Spring Cloud handles service exposure and gateway routing, Temporal remains for cross-service business workflows, and AI/RAG integrations must keep sensitive data behind the API gateway. The first implementation should be locally reliable and testable without requiring ClickHouse, Doris, Milvus, or a production embedding model.

## Goals / Non-Goals

**Goals:**
- Register datasets and document collections with schema metadata and lifecycle status.
- Ingest documents into chunks and deterministic local embeddings suitable for repeatable tests.
- Expose structured query, semantic query, and hybrid query APIs under `/api/v1/data/**`.
- Add gateway routing, permissions, menu fixtures, and a compact frontend validation page.
- Preserve an upgrade path toward ClickHouse/Doris and external vector databases.

**Non-Goals:**
- Full CDC connector runtime, Debezium integration, or Kafka streaming.
- Large-scale analytical storage in ClickHouse/Doris.
- Production-grade Chinese embedding quality.
- Multi-Agent orchestration or LUI tool-call automation.
- Arbitrary SQL execution from users.

## Decisions

### D1: Expose the MVP through `data-analytics`

`data-analytics` SHALL be the externally routed Spring Boot service for `/api/v1/data/**`. It owns controller contracts for catalog, ingestion, and hybrid query. `data-pipeline` and `data-embedding` remain Maven modules that can hold reusable services, DTOs, and future connector logic.

Alternative considered: run three independent services. That matches the long-term architecture but adds operational overhead before the contracts are proven. The MVP keeps one externally reachable service and clear internal package boundaries.

### D2: Use PostgreSQL as the MVP store

Catalog rows, document rows, chunks, embeddings, and query audit records SHALL live in PostgreSQL. If pgvector is available, migrations MAY enable it, but the MVP MUST also work with deterministic embedding text/array columns so local tests do not depend on native vector indexes.

Alternative considered: introduce ClickHouse and Milvus immediately. That would validate the final topology, but it would slow down the first usable slice. PostgreSQL keeps the loop tight while preserving a storage abstraction.

### D3: Deterministic local embedding for MVP

The embedding component SHALL generate deterministic fixed-dimension vectors from normalized text using local code. This is intentionally not a semantic-quality model; it exists to make ingestion and query ranking repeatable in tests and dev.

Alternative considered: sentence-transformers or external embedding APIs. Those are better semantically, but introduce Python runtime/model download/API key instability. A future change can swap the embedding provider behind the same interface.

### D4: Safe structured query templates

Structured query SHALL run only against registered dataset templates, not arbitrary user SQL. A dataset can define field metadata and a safe backing table/view key. The MVP can ship sample datasets backed by local PostgreSQL tables and generated fixture rows.

Alternative considered: accept raw SQL. It is faster to demo but violates the platform's safety posture and makes auth/data-permission enforcement much harder.

### D5: Hybrid response contract

Hybrid query SHALL return both a `structured` section and a `semantic` section, each with status, rows/chunks, count, and elapsed time. Clients can request `STRUCTURED`, `SEMANTIC`, or `HYBRID`. Empty partial results are not errors; contract or validation failures are errors.

## Risks / Trade-offs

- PostgreSQL-only MVP may hide analytical-scale issues -> keep storage and query services behind interfaces and avoid leaking SQL details into frontend contracts.
- Deterministic embeddings are low quality -> label them as MVP provider and test only ordering/contract behavior, not semantic correctness.
- Menu/API permissions can drift from frontend guards -> seed permissions and add tests or SQL verification for every protected endpoint used by the UI.
- Running one data service may blur module boundaries -> keep packages and Maven modules explicit, and do not add workflow/AI responsibilities to `data-analytics`.

## Migration Plan

1. Add PostgreSQL migrations for catalog, documents, chunks, query audit, and sample dataset fixtures.
2. Add service code and tests in the data modules.
3. Add gateway route and auth/menu fixtures.
4. Add frontend page and API client.
5. Validate with targeted Maven tests and frontend typecheck/unit coverage.

Rollback is removing the `/api/v1/data/**` route/menu visibility and leaving the new tables unused. Data migrations are additive and do not alter existing process/auth/lowcode tables.

## Open Questions

- Should the next production embedding provider be BGE-M3, an external LLM gateway embedding route, or a private vectorization service?
- Should structured query first target workflow instance/form snapshots, imported CSV-like tables, or operational service views?
