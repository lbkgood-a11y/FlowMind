## ADDED Requirements

### Requirement: Visual configuration for lifecycle resources
The system SHALL provide visual create configuration forms for structures, mappings, value maps, connectors, routes, orchestrations, callback profiles, API products, applications, subscriptions, and traffic policies.

#### Scenario: Implementer creates a connector without raw JSON
- **WHEN** an implementation operator opens connector management and selects create
- **THEN** the system renders labeled controls for base URL, operation path, method, timeout, operation class, authentication type, and network policy and submits a valid connector payload without requiring raw JSON entry

#### Scenario: Every resource has visual metadata
- **WHEN** the OpenAPI lifecycle resource registry is loaded
- **THEN** every resource with a create endpoint has create form metadata and a default payload generator

### Requirement: Nested visual builders
The system SHALL support nested visual builders for repeated and object-based configuration such as mapping rules, value map entries, product routes, application contacts, subscription overrides, callback security settings, orchestration steps, and policy restrictions.

#### Scenario: Implementer adds mapping rules visually
- **WHEN** an implementation operator edits a mapping create form
- **THEN** the operator can add, remove, and configure ordered mapping rules using controls for operation, source pointer, target pointer, required flag, and rule config

### Requirement: Advanced JSON fallback
The system SHALL preserve an advanced JSON mode that previews the generated payload and allows manual payload editing with validation before submit.

#### Scenario: Operator edits generated JSON
- **WHEN** an operator switches from visual mode to advanced JSON mode and edits the payload
- **THEN** the system validates JSON syntax before submission and rejects invalid JSON without invoking the backend

### Requirement: Action payload guidance
The system SHALL provide visual action payload forms for lifecycle actions that require request bodies while using empty payloads for bodyless actions.

#### Scenario: Operator runs rollback action
- **WHEN** an operator selects the route rollback action
- **THEN** the system displays controls for target release and reason instead of requiring a raw JSON object

### Requirement: Reference selection without manual IDs
The system SHALL prevent implementation operators from manually entering internal lifecycle identifiers in visual mode and SHALL provide searchable reference selectors that display business code/key fields and submit the backend DTO identifier or code value required by the typed API.

#### Scenario: Implementer selects dependent versions by code
- **WHEN** an implementation operator configures mappings, routes, callback profiles, subscriptions, products, or lifecycle action targets
- **THEN** the visual form provides searchable selectors for structures, structure versions, mappings, connector versions, route versions, releases, product versions, application clients, callback versions, orchestration versions, subscriptions, and policies instead of plain text ID fields
- **AND** each option shows a code/key or semantic label before the opaque ID
- **AND** the generated payload remains compatible with the existing backend DTO shape

### Requirement: Tenant-scoped reference catalogs
The system SHALL scope lifecycle reference selector catalogs to the operator tenant, including version assets whose rows inherit tenancy from parent lifecycle assets.

#### Scenario: Operator searches version references
- **WHEN** an implementation operator opens a version reference selector for structures, mappings, value maps, connectors, routes, orchestrations, callbacks, or products
- **THEN** the catalog derives candidate versions only from parent assets visible in the current tenant context
- **AND** the selector still supports searching by parent code/key, semantic version, or visible reference label

### Requirement: Permission-aware visual controls
The system MUST preserve existing permission checks when replacing JSON dialogs with visual forms.

#### Scenario: Read-only operator opens a resource page
- **WHEN** an operator lacks the resource write permission
- **THEN** the visual create and lifecycle action controls are hidden or disabled just as the raw JSON controls were

### Requirement: Structured detail and draft editing
The system SHALL show lifecycle asset details as structured readable fields instead of defaulting to raw JSON, and SHALL expose visual draft editing for lifecycle assets with mutable draft update APIs.

#### Scenario: Implementer inspects an asset
- **WHEN** an implementation operator opens a lifecycle asset detail dialog
- **THEN** the system shows common metadata and configuration fields with readable labels, nested sections, arrays, and state indicators
- **AND** raw JSON is available only as an advanced troubleshooting view

#### Scenario: Implementer edits a mutable draft
- **WHEN** an implementation operator opens a DRAFT structure, mapping, value map, connector, route, orchestration, callback profile, product version, application, or traffic policy
- **THEN** the system offers a visual edit dialog backed by the typed update API for that asset
- **AND** immutable published/runtime snapshot assets remain read-only with a clear draft-only editing rule

### Requirement: Demo lifecycle data reset
The system SHALL provide a repeatable local reset path that clears OpenAPI lifecycle tables and recreates a complete implementer-style configuration through management APIs.

#### Scenario: Operator prepares a local demo
- **WHEN** a developer runs the OpenAPI demo reset script against the local database and service
- **THEN** existing OpenAPI lifecycle rows are removed without touching Flyway history
- **AND** a complete tenant-scoped OpenAPI lifecycle sample is recreated from structure through runtime invocation and readiness checks
