## ADDED Requirements

### Requirement: Form submissions validate against published schema
The lowcode service SHALL validate submitted form instance data against the selected published form version before inserting the instance.

#### Scenario: Submit valid data
- **WHEN** a user submits data matching the published form schema
- **THEN** the system stores the instance with tenant, form definition ID, form key, form version, schema hash, submitter, status, and audit metadata

#### Scenario: Submit invalid data
- **WHEN** a user submits data missing required fields or containing unsupported additional fields
- **THEN** the system rejects the submission with structured field validation errors and creates no instance

#### Scenario: Submit unpublished form
- **WHEN** a user submits data for a form key whose latest tenant-visible version is not published
- **THEN** the system rejects the submission

### Requirement: Form instance access enforces tenant and data scope
The lowcode service SHALL enforce tenant isolation and data-scope authorization for form instance submit, list, detail, and process-binding operations.

#### Scenario: Query own data
- **WHEN** a caller has `SELF` query scope for a form resource
- **THEN** the list and detail APIs return only instances submitted by that caller

#### Scenario: Query all data
- **WHEN** a caller has `ALL` query scope for a form resource
- **THEN** the list and detail APIs can return all instances in the caller's tenant for that form

#### Scenario: Detail denied
- **WHEN** a caller requests an instance outside their tenant or data scope
- **THEN** the lowcode service returns an authorization error instead of the instance payload

### Requirement: Process binding is idempotent and lowcode-owned
The lowcode service SHALL own process binding columns on form instances and SHALL bind an instance to a workflow process idempotently.

#### Scenario: Bind unbound instance
- **WHEN** an authorized caller binds a form instance to a process instance ID and process key
- **THEN** the system stores the binding, workflow status, audit metadata, and prevents another instance from binding to the same process instance ID

#### Scenario: Retry same binding
- **WHEN** the same process binding request is repeated for an already-bound instance
- **THEN** the system returns the existing binding without creating duplicate state

#### Scenario: Bind to different process
- **WHEN** an instance is already bound to process `P1` and a caller attempts to bind it to process `P2`
- **THEN** the system rejects the request with a conflict

### Requirement: Form instance status is auditable
Form instance runtime changes SHALL be auditable, including submit, workflow binding, workflow status update, manual correction, and offline handling.

#### Scenario: Audit submit
- **WHEN** a form instance is submitted
- **THEN** the system records created and updated audit metadata using the authenticated user context

#### Scenario: Audit workflow binding
- **WHEN** a process binding or workflow status update is applied
- **THEN** the system records the operator, timestamp, traceId when available, previous status, and new status

### Requirement: Form instance queries are production paginated
The lowcode service SHALL page form instance queries at the database layer and SHALL expose stable sorting and filtering suitable for generic runtime lists.

#### Scenario: Query page
- **WHEN** a caller requests page 2 of a form instance list
- **THEN** the database query returns only that page in stable submitted-time order with total count

#### Scenario: Filter by workflow status
- **WHEN** a caller filters instances by workflow status or form status
- **THEN** the response contains only matching tenant-visible instances
