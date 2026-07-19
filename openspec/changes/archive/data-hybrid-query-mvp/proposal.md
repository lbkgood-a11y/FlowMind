## Why

TrioBase already has a workflow runtime MVP, but the platform still lacks a concrete way to register enterprise datasets, ingest searchable knowledge, and expose a unified query surface for GUI pages, LUI agents, and workflow triggers. This change starts the roadmap's second phase by making data usable inside the product instead of leaving `trio-base-data` as module scaffolding.

## What Changes

- Add a data catalog MVP for registering internal datasets and document collections with ownership, status, and schema metadata.
- Add document ingestion into PostgreSQL-backed chunks with deterministic local embedding generation for development and test use.
- Add a hybrid query API that can return structured rows, semantic chunks, or both in a single response.
- Add gateway routing and permission/menu fixtures for the data query surface.
- Add a compact frontend validation page for registering sample datasets/documents and running hybrid queries.
- Keep ClickHouse/Doris, external vector DBs, CDC connectors, and full agent orchestration out of this MVP; the MVP uses PostgreSQL to prove the contract and integration flow first.

## Capabilities

### New Capabilities
- `data-ingestion-catalog`: dataset/document collection registration, document ingestion, chunk storage, and lifecycle status.
- `hybrid-query-runtime`: structured query, semantic query, and combined hybrid query response contract.

### Modified Capabilities
- None.

## Impact

- Backend modules: `trio-base-data/data-pipeline`, `trio-base-data/data-embedding`, `trio-base-data/data-analytics`.
- Gateway: add `/api/v1/data/**` route to the data query service.
- Auth/menu fixtures: add permissions and menu entries for data catalog and hybrid query pages.
- Frontend: add a data center route and validation UI in `apps/web-antd`.
- Database: PostgreSQL tables for datasets, fields, documents, chunks, and query audit records; optional pgvector extension when available.
- Tests: targeted Maven tests for catalog, ingestion, embedding determinism, query contracts, and gateway/menu wiring.
