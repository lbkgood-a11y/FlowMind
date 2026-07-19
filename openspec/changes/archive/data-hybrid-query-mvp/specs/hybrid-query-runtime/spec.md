## ADDED Requirements

### Requirement: Structured Dataset Query
The system SHALL allow authorized users to query registered structured datasets through safe query parameters rather than arbitrary SQL.

#### Scenario: Query sample structured dataset
- **WHEN** a client POSTs a structured query with dataset key, filters, page, and size to `/api/v1/data/query/structured`
- **THEN** the system returns rows, total count, field metadata, and elapsed time

#### Scenario: Unknown dataset rejected
- **WHEN** a client queries a dataset key that is not registered or not active
- **THEN** the system returns HTTP 404 with error `DATASET_NOT_FOUND`

#### Scenario: Page size capped
- **WHEN** a client requests page size greater than the configured maximum
- **THEN** the system caps page size to the maximum and returns the effective page size in the response

### Requirement: Semantic Document Query
The system SHALL allow authorized users to search ingested document chunks by natural language query and return ranked chunk results.

#### Scenario: Search document chunks
- **WHEN** a client POSTs a semantic query with text and topK to `/api/v1/data/query/semantic`
- **THEN** the system embeds the query, ranks stored chunks by similarity, and returns top chunks with score, document title, and chunk index

#### Scenario: Semantic query topK capped
- **WHEN** a client requests topK greater than the configured maximum
- **THEN** the system caps topK to the maximum and returns the effective topK in the response

#### Scenario: No semantic matches
- **WHEN** no chunk meets the similarity threshold
- **THEN** the system returns an empty chunk list with HTTP 200

### Requirement: Hybrid Query Response
The system SHALL provide a hybrid query endpoint that can execute structured and semantic retrieval together and return a combined response.

#### Scenario: Hybrid query returns both sections
- **WHEN** a client POSTs mode `HYBRID` to `/api/v1/data/query/hybrid`
- **THEN** the system returns a `structured` section and a `semantic` section, each with status, result count, elapsed time, and result payload

#### Scenario: Structured-only mode
- **WHEN** a client POSTs mode `STRUCTURED` to `/api/v1/data/query/hybrid`
- **THEN** the system returns a populated `structured` section and a skipped `semantic` section

#### Scenario: Query audit recorded
- **WHEN** a hybrid query completes
- **THEN** the system records query mode, dataset key, operator identity when available, elapsed time, and result counts
