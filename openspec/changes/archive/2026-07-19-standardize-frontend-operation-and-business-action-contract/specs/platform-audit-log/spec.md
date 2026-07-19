## ADDED Requirements

### Requirement: Document timeline correlates platform and business events
The system SHALL expose a document timeline read model that correlates operation audit entries, Global Action executions, Action events, owner-service domain events, workflow task events, attachment events, import/export events, trace id, correlation id, actor, and target document identity.

#### Scenario: View purchase order timeline
- **WHEN** a user opens a purchase order timeline
- **THEN** the system returns ordered timeline entries for create, edit, submit, approve, workflow task changes, attachment changes, action results, and related operation audit records that the user is authorized to see

#### Scenario: Timeline correlates action
- **WHEN** a document action has a Global Action id and trace id
- **THEN** timeline entries include the action id, action type, normalized status, actor, owner execution reference, correlation id, and trace id

### Requirement: Domain services emit bounded document events
Owner services SHALL emit or persist bounded domain events for document changes that need to appear in document timelines.

#### Scenario: Draft field update
- **WHEN** a user saves a document draft
- **THEN** the owner service records a bounded document event with actor, target document, event type, result, changed field keys, trace id, and correlation id without storing raw sensitive values

#### Scenario: Lifecycle status update
- **WHEN** an Action changes a document lifecycle status
- **THEN** the owner service records a document event that references the Global Action id and new domain status

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
