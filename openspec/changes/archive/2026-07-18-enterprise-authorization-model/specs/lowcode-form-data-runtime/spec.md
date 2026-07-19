## ADDED Requirements

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
The lowcode service SHALL combine central authorization decisions with workflow or document guard results for workflow-backed form actions.

#### Scenario: Approval candidate can approve
- **WHEN** the decision API allows `APPROVE` and the workflow service confirms the current user is an active candidate or assignee for a pending task
- **THEN** the lowcode runtime exposes and accepts the approval action

#### Scenario: Non-candidate cannot approve
- **WHEN** the decision API allows `APPROVE` but the workflow guard reports the user is not a candidate or assignee
- **THEN** the lowcode runtime denies the approval action with a guard failure reason

#### Scenario: Submitter cannot self-approve
- **WHEN** the decision API allows `APPROVE` but a no-self-approval guard detects the current user submitted the form instance
- **THEN** the lowcode runtime denies the approval action without changing the workflow task
