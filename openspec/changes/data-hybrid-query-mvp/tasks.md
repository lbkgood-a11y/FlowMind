## 1. Service Foundation

- [x] 1.1 Verify current `trio-base-data` module structure, dependency inheritance, and empty source trees before implementation.
- [x] 1.2 Make `data-analytics` a runnable Spring Boot service with PostgreSQL, MyBatis-Plus, Flyway, validation, and shared common dependencies.
- [x] 1.3 Add local application configuration for `data-analytics` on port 8091 with Nacos disabled by default.
- [x] 1.4 Add gateway route for `/api/v1/data/**` to `data-analytics`.

## 2. Catalog Schema And Domain

- [x] 2.1 Add Flyway migration for `data_dataset`, `data_dataset_field`, `data_document`, `data_document_chunk`, and `data_query_audit`.
- [x] 2.2 Add dataset and field entities, mappers, DTOs, and request validation.
- [x] 2.3 Implement dataset registration, duplicate-key rejection, listing, and detail retrieval.
- [x] 2.4 Add deterministic fixture dataset rows for a small workflow/expense analytics sample.

## 3. Document Ingestion And Embedding

- [x] 3.1 Add document DTOs, entities, and mapper methods for documents and chunks.
- [x] 3.2 Implement deterministic local embedding provider with fixed dimension and repeatable output.
- [x] 3.3 Implement chunking with max size and overlap configuration.
- [x] 3.4 Implement document ingestion with blank-content rejection and source-key replacement in one transaction.
- [x] 3.5 Add unit tests for chunking, embedding determinism, and document replacement.

## 4. Query Runtime

- [x] 4.1 Add structured query request/response DTOs and safe dataset query service.
- [x] 4.2 Implement structured query against registered sample datasets with filter, paging, page-size cap, field metadata, and elapsed time.
- [x] 4.3 Add semantic query request/response DTOs and similarity ranking over stored chunks.
- [x] 4.4 Implement semantic topK cap, threshold handling, and empty-result success behavior.
- [x] 4.5 Implement hybrid query endpoint with `STRUCTURED`, `SEMANTIC`, and `HYBRID` modes.
- [x] 4.6 Record query audit rows for completed structured, semantic, and hybrid queries.

## 5. API, Permissions, And Frontend

- [x] 5.1 Add controllers for `/api/v1/data/datasets`, `/api/v1/data/documents`, and `/api/v1/data/query/**`.
- [x] 5.2 Add auth permissions and menu fixtures for Data Center, Dataset Catalog, and Hybrid Query.
- [x] 5.3 Add `web-antd` API client for data catalog, document ingestion, and hybrid query.
- [x] 5.4 Add compact frontend validation page for registering datasets/documents and running hybrid queries.
- [x] 5.5 Ensure frontend permissions match seeded API permission codes.

## 6. Verification

- [x] 6.1 Add targeted backend tests for catalog registration/listing and duplicate-key rejection.
- [x] 6.2 Add targeted backend tests for structured, semantic, and hybrid query contracts.
- [x] 6.3 Run targeted Maven tests for `data-analytics`.
- [x] 6.4 Run frontend typecheck and relevant unit tests.
- [x] 6.5 Run `openspec validate data-hybrid-query-mvp --strict`.
