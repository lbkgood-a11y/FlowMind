## MODIFIED Requirements

### Requirement: Form submissions validate against published schema
The lowcode service SHALL validate submitted form instance data against the selected published form version before inserting the instance, and business-changing submissions SHALL be correlated to a Global Action execution.

#### Scenario: Submit valid data
- **WHEN** a user submits data matching the published form schema through a Global Action
- **THEN** the system stores the instance with tenant, form definition ID, form key, form version, schema hash, submitter, status, action id, trace id, and audit metadata

#### Scenario: Submit invalid data
- **WHEN** a user submits data missing required fields or containing unsupported additional fields
- **THEN** the system rejects the Global Action with structured field validation errors and creates no instance

#### Scenario: Submit unpublished form
- **WHEN** a user submits data for a form key whose latest tenant-visible version is not published
- **THEN** the system rejects the Global Action before inserting form data

### Requirement: Process binding is idempotent and lowcode-owned
The lowcode service SHALL own process binding columns on form instances and SHALL bind an instance to a workflow process idempotently while recording the Global Action correlation that caused the binding.

#### Scenario: Bind unbound instance
- **WHEN** an authorized Global Action binds a form instance to a process instance ID and process key
- **THEN** the system stores the binding, workflow status, action id, trace id, audit metadata, and prevents another instance from binding to the same process instance ID

#### Scenario: Retry same binding
- **WHEN** the same process binding request is repeated for an already-bound instance with the same action correlation
- **THEN** the system returns the existing binding without creating duplicate state or duplicate audit rows

#### Scenario: Bind to different process
- **WHEN** an instance is already bound to process `P1` and a caller attempts to bind it to process `P2`
- **THEN** the system rejects the Global Action with a conflict

### Requirement: Form instance status is auditable
Form instance runtime changes SHALL be auditable, including submit, workflow binding, workflow status update, manual correction, and offline handling, and each business-changing audit entry SHALL reference the causing Global Action when available.

#### Scenario: Audit submit
- **WHEN** a form instance is submitted through a Global Action
- **THEN** the system records created and updated audit metadata using the authenticated user context, action id, source, and trace id

#### Scenario: Audit workflow binding
- **WHEN** a process binding or workflow status update is applied
- **THEN** the system records the operator, timestamp, action id when available, traceId when available, previous status, and new status

### Requirement: Workflow-backed form actions combine authorization and workflow guards
The lowcode service SHALL combine central authorization decisions, Global Action policy evaluation, and workflow or document guard results for workflow-backed form actions.

#### Scenario: Approval candidate can approve
- **WHEN** the decision API allows `APPROVE`, the Global Action policy allows the action target, and the workflow service confirms the current user is an active candidate or assignee for a pending task
- **THEN** the lowcode runtime exposes and accepts the approval action through Global Action dispatch

#### Scenario: Non-candidate cannot approve
- **WHEN** the decision API allows `APPROVE` but the workflow guard reports the user is not a candidate or assignee
- **THEN** the Global Action is rejected with a guard failure reason and the workflow task is not changed

#### Scenario: Submitter cannot self-approve
- **WHEN** the decision API allows `APPROVE` but a no-self-approval guard detects the current user submitted the form instance
- **THEN** the Global Action is rejected without changing the workflow task
