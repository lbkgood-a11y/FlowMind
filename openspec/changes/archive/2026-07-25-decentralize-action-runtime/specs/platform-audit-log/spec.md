## MODIFIED Requirements

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

### Requirement: Document timeline correlates platform and business events
The system SHALL expose a document timeline read model that correlates operation audit entries, Global Action executions, Action events, owner-service domain events, workflow task events, attachment events, import/export events, trace id, correlation id, actor, and target document identity without requiring `service-action` to own new timeline writes.

#### Scenario: View purchase order timeline
- **WHEN** a user opens a purchase order timeline
- **THEN** the system returns ordered timeline entries for create, edit, submit, approve, workflow task changes, attachment changes, owner-hosted action results, and related operation audit records that the user is authorized to see

#### Scenario: Timeline correlates action
- **WHEN** a document action has a Global Action id and trace id
- **THEN** timeline entries include the action id, action type, normalized status, actor, owner execution reference, correlation id, and trace id

### Requirement: Domain services emit bounded document events
Owner services SHALL emit or persist bounded domain and action events for document changes that need to appear in document timelines.

#### Scenario: Draft field update
- **WHEN** a user saves a document draft
- **THEN** the owner service records a bounded document event with actor, target document, event type, result, changed field keys, trace id, and correlation id without storing raw sensitive values

#### Scenario: Lifecycle status update
- **WHEN** an owner-hosted Action changes a document lifecycle status
- **THEN** the owner service records a document event that references the Global Action id and new domain status

## ADDED Requirements

### Requirement: Historical action tables remain readable during migration
The system SHALL preserve authorized read access to historical `act_*` action and timeline records during the migration window even after new action execution writes move to owner services.

#### Scenario: Query historical action audit
- **WHEN** an administrator queries an action audit record created before `service-action` removal
- **THEN** the system can return the historical record from the retained table or archival projection without requiring new dispatch through `service-action`

#### Scenario: New owner event appears in timeline
- **WHEN** an owner-hosted action emits a new document event after migration
- **THEN** the document timeline projection includes the new event alongside any historical `act_*` records that are still within the retention window

