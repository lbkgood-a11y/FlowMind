# platform-audit-log Specification

## Purpose
TBD - created by archiving change platform-governance-foundation. Update Purpose after archive.
## Requirements
### Requirement: Operation Audit Recording
The system SHALL record auditable platform management and business-changing operations with actor, request, resource, Global Action identity when available, result, latency, and trace metadata, regardless of whether the action is executed by a central facade or an owner-hosted runtime.

#### Scenario: Successful operation audit
- **WHEN** an authenticated user performs a protected management API operation or owner-hosted Global Action
- **THEN** the system records an audit log containing user ID, username, permission code, HTTP method or action source, path or action type, target resource, result status, latency, client IP, user agent, action id when available, correlation id when available, and TraceId

#### Scenario: Failed operation audit
- **WHEN** a protected management API operation or owner-hosted Global Action fails with a business or system error
- **THEN** the system records an audit log with failure status, normalized action status when available, and a bounded error message

#### Scenario: Public endpoint excluded from operation audit
- **WHEN** a public endpoint such as login or health check is called
- **THEN** the system does not create an operation audit log unless a dedicated login log requirement applies

### Requirement: Operation Audit Query
The system SHALL allow authorized administrators to query operation audit logs by user, action, path, result, time range, Global Action id, action type, source, target, correlation id, trace id, and idempotency key.

#### Scenario: Query audit logs
- **WHEN** an authorized administrator queries audit logs with filters and pagination
- **THEN** the system returns matching audit records ordered by operation time descending

#### Scenario: Query by action id
- **WHEN** an authorized administrator searches for a specific Global Action id
- **THEN** the system returns the related operation audit record and its bounded action execution summary

#### Scenario: Unauthorized audit query rejected
- **WHEN** a user lacks the audit log read permission
- **THEN** the backend rejects the audit log query and the frontend hides the audit log page

### Requirement: Operation Audit Detail
The system SHALL provide a detail view for an operation audit record including request parameters summary, response summary, Global Action summary when available, error message, TraceId, and operator metadata.

#### Scenario: View audit detail
- **WHEN** an authorized administrator opens an audit log detail for an HTTP-only operation
- **THEN** the system returns request parameters summary, response summary, error message, TraceId, and operator metadata for that audit record

#### Scenario: View action audit detail
- **WHEN** an authorized administrator opens an audit log detail linked to a Global Action
- **THEN** the system returns action id, action type, source, actor, target, normalized status, idempotency key, correlation id, owner service, execution reference, result summary, and redacted payload summary

### Requirement: Document timeline correlates platform and business events
The system SHALL expose a document timeline read model that correlates operation audit entries, owner-hosted Global Action results, owner-service domain events, workflow task events, attachment events, import/export events, trace id, correlation id, actor, and target document identity through the `service-business-catalog` projection table without requiring cross-service database reads.

#### Scenario: View purchase order timeline
- **WHEN** a user opens a purchase order timeline
- **THEN** the system returns ordered timeline entries for create, edit, submit, approve, workflow task changes, attachment changes, owner-hosted action results, and related operation audit records that the user is authorized to see

#### Scenario: Timeline correlates action
- **WHEN** a document action has a Global Action id and trace id
- **THEN** timeline entries include the action id, action type, normalized status, actor, owner execution reference, correlation id, and trace id

#### Scenario: Timeline query uses local projection
- **WHEN** the frontend queries a document timeline
- **THEN** `service-business-catalog` reads only `bc_document_timeline_event` with database pagination and does not query owner-service audit tables or historical `act_*` tables

### Requirement: Domain services emit bounded document events
Owner services SHALL emit or persist bounded domain and action events for document changes that need to appear in document timelines.

#### Scenario: Draft field update
- **WHEN** a user saves a document draft
- **THEN** the owner service records a bounded document event with actor, target document, event type, result, changed field keys, trace id, and correlation id without storing raw sensitive values

#### Scenario: Lifecycle status update
- **WHEN** an owner-hosted Action changes a document lifecycle status
- **THEN** the owner service records a document event that references the Global Action id and new domain status

#### Scenario: Owner event projected to timeline
- **WHEN** an owner service completes a domain operation, workflow task event, or owner-hosted Global Action
- **THEN** it sends a bounded `BusinessTimelineEventRecord` to `service-business-catalog` or publishes an equivalent reliable outbox event with a stable event id for idempotent projection

### Requirement: Timeline protects sensitive data
Document timeline entries SHALL be redacted and filtered according to field authorization, audit sensitivity, tenant boundary, and data-scope rules.

#### Scenario: Sensitive field changed
- **WHEN** a sensitive field is modified
- **THEN** the timeline shows that the field changed only if the viewer is authorized and does not expose raw sensitive values

#### Scenario: Unauthorized timeline access
- **WHEN** a user lacks permission to view a document or timeline
- **THEN** the backend rejects the timeline query or returns no document-specific events

### Requirement: Timeline queries support document target and correlation filters
The system SHALL allow authorized timeline queries by target type, target id, tenant id, action id, workflow execution reference, correlation id, trace id, event type, actor, and time range.

#### Scenario: Query by target
- **WHEN** the frontend requests a timeline for target type `SCM_PURCHASE_ORDER` and a purchase order id
- **THEN** the backend returns timeline entries for that document ordered by event time and sequence

#### Scenario: Query by trace
- **WHEN** an administrator investigates a trace id
- **THEN** the system can locate related document timeline entries, Action executions, and operation audit records without exposing unauthorized tenant data

### Requirement: Historical action tables are not timeline runtime dependencies
The system SHALL migrate, archive, or drop historical `act_*` action and timeline records outside the live document timeline query path after new action execution writes move to owner services.

#### Scenario: Historical table removed from projection
- **WHEN** `service-action` historical tables are no longer part of the supported migration window
- **THEN** `service-business-catalog` removes local `act_*` projection tables and the live timeline API continues to read from `bc_document_timeline_event`

#### Scenario: Archived action investigation
- **WHEN** an administrator needs pre-migration action history after the projection cleanup
- **THEN** the investigation uses an archive, backup, or dedicated audit export rather than adding runtime cross-service reads back into `service-business-catalog`
