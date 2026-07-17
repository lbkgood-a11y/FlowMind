# external-structure-registry Specification

## Purpose
Define governed registration, versioning, compatibility, import, export, tenant extension, and audit behavior for canonical and external integration structures.

## Requirements

### Requirement: Register canonical and external structures
The system SHALL register namespaced canonical structures and external-system structures with stable identifiers, ownership, format, direction, and descriptive metadata.

#### Scenario: Register a canonical structure
- **WHEN** an authorized manager submits a valid canonical JSON structure in an unused namespace and name
- **THEN** the system creates a stable structure identity with an initial draft version

#### Scenario: Reject duplicate identity
- **WHEN** a manager attempts to create another structure with the same tenant, namespace, name, and kind
- **THEN** the system rejects the request with a conflict response

### Requirement: Manage immutable structure versions
The system SHALL support draft, published, deprecated, and archived structure-version states, and SHALL permit content edits only while a version is draft.

#### Scenario: Publish a draft
- **WHEN** an authorized manager publishes a valid draft structure version
- **THEN** the system stores an immutable published snapshot and records actor, timestamp, and change summary

#### Scenario: Prevent published mutation
- **WHEN** a caller attempts to edit the schema content of a published version
- **THEN** the system rejects the edit and requires creation of a new draft version

### Requirement: Extend canonical structures per tenant
The system SHALL allow a tenant to create a versioned extension from a pinned published platform or domain canonical structure and SHALL preserve inherited field compatibility and governance metadata.

#### Scenario: Add tenant fields
- **WHEN** an authorized tenant manager adds tenant-namespaced optional fields to an extension draft
- **THEN** the system accepts the additions without modifying the parent canonical structure

#### Scenario: Redefine inherited field
- **WHEN** a tenant extension attempts to remove an inherited field or change its type, semantic identity, or sensitivity classification
- **THEN** the system rejects the extension as incompatible

#### Scenario: Parent publishes a new version
- **WHEN** the parent canonical structure publishes a newer version
- **THEN** the existing tenant extension remains pinned to its current parent version until an explicit upgrade draft is created and validated

### Requirement: Validate schema and compatibility
The system SHALL validate registered structures, classify structural changes against the preceding published version as compatible or breaking, and require a publisher semantic-change declaration before publication.

#### Scenario: Add an optional field
- **WHEN** a new draft adds an optional field without changing existing field semantics
- **THEN** the system classifies the structural change as compatible

#### Scenario: Remove a required field
- **WHEN** a draft removes a required field from an existing published major version
- **THEN** the system classifies the change as breaking and blocks publication under the same compatibility line

#### Scenario: Declare semantic unit change
- **WHEN** a field retains its JSON type but changes business unit, timezone, enumeration meaning, or semantic interpretation
- **THEN** the publisher must declare the semantic change and the system requires breaking-change or security-sensitive approval as applicable

### Requirement: Import OpenAPI structures
The system SHALL import supported request, response, and component schemas from valid OpenAPI 3.x documents into draft external or canonical structures.

#### Scenario: Import component schemas
- **WHEN** an authorized manager uploads a valid OpenAPI 3.x document and selects component schemas
- **THEN** the system creates corresponding draft structures and preserves source-document provenance

#### Scenario: Reject invalid OpenAPI
- **WHEN** an uploaded document is syntactically invalid or contains unsupported unresolved references
- **THEN** the system rejects the import and returns location-specific validation errors

### Requirement: Export published contracts
The system SHALL export selected published structures and approved operations as a valid OpenAPI 3.x document.

#### Scenario: Export a published route contract
- **WHEN** an authorized user exports a published integration release
- **THEN** the document contains pinned request and response schemas and TrioBase lifecycle extensions without exposing secrets

### Requirement: Govern access and audit lifecycle actions
The system SHALL enforce tenant isolation and permissions for structure management and SHALL audit create, modify, publish, deprecate, archive, import, and export actions.

#### Scenario: Cross-tenant lookup
- **WHEN** a caller requests a structure belonging to another tenant without platform-level permission
- **THEN** the system returns no accessible structure data and records the denied access according to audit policy
