# lowcode-form-data-runtime Specification

## Purpose
TBD - created by archiving change lowcode-production-hardening. Update Purpose after archive.
## Requirements
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

### Requirement: Form instance queries are production paginated
The lowcode service SHALL page form instance queries at the database layer and SHALL expose stable sorting and filtering suitable for generic runtime lists.

#### Scenario: Query page
- **WHEN** a caller requests page 2 of a form instance list
- **THEN** the database query returns only that page in stable submitted-time order with total count

#### Scenario: Filter by workflow status
- **WHEN** a caller filters instances by workflow status or form status
- **THEN** the response contains only matching tenant-visible instances

### Requirement: Form instance operations enforce authorization decisions
The lowcode service SHALL enforce enterprise authorization decisions for form instance create, view, edit, submit, workflow binding, approval-related actions, status update, delete, and export operations.

#### Scenario: Create allowed by authorization
- **WHEN** a user submits a create request for a published form and the decision API allows `CREATE`
- **THEN** the lowcode service validates the payload, applies field write rules, and stores the instance in the user's tenant

#### Scenario: Edit denied by authorization
- **WHEN** a user requests to edit a form instance without `EDIT` authorization or with a failed document status guard
- **THEN** the lowcode service rejects the mutation without changing the instance

#### Scenario: Export denied by authorization
- **WHEN** a user requests form instance export without `EXPORT` authorization
- **THEN** the lowcode service denies the export even if the user can view individual records

### Requirement: Form instance queries compile data scope safely
The lowcode service SHALL compile authorization data-scope decisions into tenant-safe form instance predicates and fail closed when the scope cannot be safely compiled.

#### Scenario: Self scope filters submitted instances
- **WHEN** the decision returns `SELF` query scope for a form
- **THEN** list and detail operations return only instances submitted or owned by the current user in the current tenant

#### Scenario: Organization scope filters instances
- **WHEN** the decision returns organization-scoped access with resolved organization ids
- **THEN** list and detail operations return only matching tenant instances whose ownership metadata falls within the resolved organizations

#### Scenario: Unsupported scope fails closed
- **WHEN** the decision returns a scope that lowcode cannot compile for form instance storage
- **THEN** the lowcode service returns no records or an authorization error rather than widening access

### Requirement: Form instance responses enforce field read rules
The lowcode service SHALL apply field read authorization before returning form instance data in list, detail, workflow, and export responses.

#### Scenario: Hidden field omitted
- **WHEN** a field decision marks `bankCard` as `HIDDEN`
- **THEN** the response payload omits `bankCard` from returned form data

#### Scenario: Masked field redacted
- **WHEN** a field decision marks `phone` as `MASKED`
- **THEN** the response payload includes only the masked value according to the configured mask strategy

#### Scenario: Export uses field rules
- **WHEN** a user exports form instances with some fields hidden or masked
- **THEN** the exported payload applies the same field read rules as service responses

### Requirement: Form instance mutations enforce field write rules
The lowcode service SHALL apply field write authorization before accepting create, edit, submit, approval, or correction payloads.

#### Scenario: Unauthorized field write rejected
- **WHEN** a user submits a payload that changes a field with write mode `DENIED`
- **THEN** the lowcode service rejects the request and reports the unauthorized field key

#### Scenario: Read-only field cannot be changed
- **WHEN** a user edits an instance and includes a changed value for a `READONLY` field
- **THEN** the lowcode service rejects the mutation or ignores that field according to the endpoint contract and records the decision reason

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

