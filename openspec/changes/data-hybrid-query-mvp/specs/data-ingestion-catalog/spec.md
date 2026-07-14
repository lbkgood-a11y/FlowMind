## ADDED Requirements

### Requirement: Dataset Catalog Registration
The system SHALL allow authorized users to register datasets with a stable dataset key, display name, dataset type, owner metadata, schema fields, and lifecycle status.

#### Scenario: Register a structured dataset
- **WHEN** a client POSTs a dataset with key, name, type `STRUCTURED`, and field metadata to `/api/v1/data/datasets`
- **THEN** the system stores the dataset and fields, returns the dataset ID, and marks the dataset as `ACTIVE`

#### Scenario: Duplicate dataset key rejected
- **WHEN** a client POSTs a dataset using an existing dataset key
- **THEN** the system returns HTTP 409 with error `DATASET_KEY_ALREADY_EXISTS`

#### Scenario: List active datasets
- **WHEN** a client GETs `/api/v1/data/datasets?status=ACTIVE`
- **THEN** the system returns a paginated list of active datasets ordered by updated time descending

### Requirement: Document Collection Ingestion
The system SHALL allow authorized users to ingest text documents into a registered document collection, split each document into chunks, and store chunk metadata for semantic query.

#### Scenario: Ingest text document
- **WHEN** a client POSTs a title and content to `/api/v1/data/documents`
- **THEN** the system creates a document row, splits the content into chunks, stores chunk text and deterministic embeddings, and returns document ID with chunk count

#### Scenario: Empty document rejected
- **WHEN** a client POSTs a document with blank content
- **THEN** the system returns HTTP 400 with error `EMPTY_DOCUMENT`

#### Scenario: Reingest document source
- **WHEN** a client POSTs a document with a source key that already exists in the same collection
- **THEN** the system replaces prior chunks for that source key atomically and keeps only the latest document version searchable

### Requirement: Ingestion Auditability
The system SHALL record ingestion timestamps, operator identity when available, source keys, and status for datasets and documents.

#### Scenario: Document ingestion audit fields
- **WHEN** a document is ingested through the API
- **THEN** the stored document includes `created_by`, `updated_by`, `created_at`, `updated_at`, and `status`
