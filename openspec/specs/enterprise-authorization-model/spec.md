# enterprise-authorization-model Specification

## Purpose
TBD - created by archiving change enterprise-authorization-model. Update Purpose after archive.
## Requirements
### Requirement: Tenant-scoped authorization resource registry
The system SHALL maintain a tenant-scoped authorization resource registry for menu entries, API operations, lowcode applications, lowcode forms, lowcode form fields, workflow task actions, and handwritten business document resources.

#### Scenario: Register lowcode resources
- **WHEN** a lowcode form or application publication synchronizes resources for tenant `T1`
- **THEN** the registry stores stable resource codes, owner service, business object id, actions, fields, guard templates, lifecycle status, and display metadata scoped to tenant `T1`

#### Scenario: Register handwritten document resources
- **WHEN** a handwritten business service synchronizes a custom document authorization manifest
- **THEN** the registry stores the declared document resource, actions, fields, and supported guard templates without requiring a menu entry

#### Scenario: Prevent cross-tenant resource collision
- **WHEN** two tenants declare the same business resource code
- **THEN** the registry treats them as separate tenant-scoped resources unless the resource is explicitly platform-global

### Requirement: Authorization decisions evaluate grants fail-closed
The system SHALL expose internal single and batch authorization decision APIs that evaluate tenant boundary, subject roles, direct grants, registered resources, action grants, deny precedence, data scope, field rules, required guard templates, and Global Action context.

#### Scenario: Allow granted action
- **WHEN** a user has an active role grant for resource `LOWCODE_FORM:EXPENSE` and action `APPROVE`
- **THEN** the decision API returns an allowed function decision with the matched grant, resource, action, tenant, subject, Global Action context, and policy versions

#### Scenario: Deny unknown resource
- **WHEN** a caller requests a decision for an unregistered resource or unregistered Global Action target
- **THEN** the decision API returns denied with a fail-closed reason and does not infer permission from request path alone

#### Scenario: Deny takes precedence
- **WHEN** one matching grant allows an action and another active matching grant denies the same action
- **THEN** the decision API returns denied and includes the deny grant in the explanation

#### Scenario: Batch action decision
- **WHEN** a runtime page requests decisions for multiple document actions, fields, and Global Action definitions
- **THEN** the batch decision API returns one stable decision entry per requested resource/action without requiring multiple network round trips

### Requirement: Authorization grants are the single function authorization source
The system SHALL store role and user function authorization only in `sys_auth_grant`; menu membership SHALL be a read-only projection derived from grants and menu permission metadata.

#### Scenario: Resource grant without menu
- **WHEN** a role is granted `CUSTOM_DOC:CONTRACT:EXPORT` without any corresponding menu row
- **THEN** authorization decisions for contract export can allow the role based on the resource grant

#### Scenario: Menu visibility is projected from grants
- **WHEN** a role has an active allow grant matching `sys_menu.permission_code`
- **THEN** role detail and dynamic routes can show the corresponding menu and ancestors without writing any role-menu authorization row

### Requirement: Data-scope decisions are executable by services
The system SHALL return data-scope decisions using stable scope types and resolved identifiers that owning services can compile into safe database predicates.

#### Scenario: Resolve organization scope
- **WHEN** a user receives `OWN_ORG_AND_CHILDREN` query scope for a document resource
- **THEN** the decision contains the resolved organization identifiers and policy evidence needed by the service to filter records

#### Scenario: No matching data policy
- **WHEN** no active data policy matches the subject, resource, and action
- **THEN** the decision returns a restrictive data scope that services MUST treat as no data access

#### Scenario: Unsupported scope compilation
- **WHEN** a service receives a scope type it cannot safely compile for its storage model
- **THEN** the service MUST fail closed instead of widening the query

### Requirement: Field authorization decisions control read and write behavior
The system SHALL return field-level authorization decisions for document and form fields, including read mode, write mode, mask strategy, matched policy, and denial reason.

#### Scenario: Mask readable field
- **WHEN** a role can read a sensitive field only in masked mode
- **THEN** the field decision marks the field as `MASKED` and includes the mask strategy to apply before response serialization

#### Scenario: Hide unreadable field
- **WHEN** a role lacks read permission for a field
- **THEN** the field decision marks the field as `HIDDEN` and services MUST omit it from response payloads

#### Scenario: Reject forbidden write
- **WHEN** a request payload changes a field whose write mode is `READONLY` or `DENIED`
- **THEN** the owning service rejects the mutation or ignores the unauthorized field according to the endpoint contract and records the reason

### Requirement: Domain guards compose with central decisions
The system SHALL model domain guards as structured guard requirements owned by the business service and SHALL NOT require `service-auth` to call domain services for state-sensitive checks; Global Action execution MUST compose central decisions with owner-service guard results before mutation.

#### Scenario: Workflow task guard succeeds
- **WHEN** a central decision allows `APPROVE` and the workflow service confirms the task is pending and assigned or candidate to the current user
- **THEN** the final Global Action authorization result allows the approval action

#### Scenario: Self-approval guard fails
- **WHEN** a central decision allows `APPROVE` but the document guard detects the current user is the submitter and self-approval is disallowed
- **THEN** the final Global Action authorization result denies the action and includes the failed guard reason

#### Scenario: Archived document guard fails
- **WHEN** a central decision allows `EDIT` but the owning service detects the document is archived
- **THEN** the final Global Action authorization result denies editing without changing resource grants

### Requirement: Decisions are explainable and auditable
The system SHALL provide explainable decision output and record auditable authorization decisions for protected business operations, including the Global Action identity and bounded action context when enforcement happens through the Action Runtime.

#### Scenario: Explain denied decision
- **WHEN** an administrator previews why a user cannot export a form
- **THEN** the system returns the evaluated subject, resource, action, Global Action type when available, grants, data scope, field rules, guard requirements, and denial reasons without exposing unrelated tenant data

#### Scenario: Audit enforced decision
- **WHEN** a protected document operation is evaluated in enforcement mode through Global Action
- **THEN** the system records action id, actor, tenant, resource, action, allowed flag, reasons, policy versions, trace id, and owner service metadata

#### Scenario: Avoid sensitive payload logging
- **WHEN** a decision references form or document fields from an action payload
- **THEN** the audit log stores field keys and policy results but not raw sensitive field values

### Requirement: Tenant context is authoritative for authorization
The system SHALL evaluate authorization against the authenticated user's real tenant context and MUST NOT rely on a hardcoded default tenant for production decisions.

#### Scenario: Token validates with tenant
- **WHEN** the gateway validates a token for a tenant user
- **THEN** the propagated security context includes the user's effective tenant id used by authorization and data-scope decisions

#### Scenario: Cross-tenant decision denied
- **WHEN** a user from tenant `T1` requests access to tenant `T2` resources without platform-global authority
- **THEN** the decision is denied before grants, field rules, or domain guards can widen access

### Requirement: Authorization decisions support action availability semantics
Authorization decisions used for frontend action availability SHALL provide visible, enabled, disabled reason, evidence, policy versions, and guard requirement metadata suitable for rendering consistent business action controls.

#### Scenario: Action denied but visible
- **WHEN** a user can view a document but lacks permission to approve it
- **THEN** the availability result can mark the approve action visible but disabled with a backend-provided disabled reason

#### Scenario: Action hidden
- **WHEN** an action is not applicable to the user's role or page context
- **THEN** the availability result marks the action not visible so the frontend omits it from primary, row, and more-action surfaces

### Requirement: Central authorization composes with owner-service state guards
The system SHALL compose central authorization decisions with owner-service guard results for state-sensitive action availability and execution.

#### Scenario: Permission allowed but state guard denied
- **WHEN** central authorization allows `SUBMIT` but the owner service detects the document is already submitted
- **THEN** the final candidate availability is disabled or rejected with the owner guard reason and the document state is not changed

#### Scenario: Permission and guard allowed
- **WHEN** central authorization allows `APPROVE` and the owner service confirms the task or document is actionable by the current actor
- **THEN** the final action availability is enabled and dispatchable

### Requirement: Business permissions use semantic resource and action codes
Business document permissions SHALL use semantic resource and action codes rather than relying only on URL permission strings.

#### Scenario: Purchase order action permission
- **WHEN** SCM registers purchase order permissions
- **THEN** it uses stable semantic codes for actions such as view, create, edit, submit, approve, reject, cancel, close, and export

#### Scenario: System management endpoint
- **WHEN** a system management page uses URL-style resource and action permissions
- **THEN** the behavior is backed by `sys_auth_resource`, `sys_auth_action`, and `sys_auth_grant` rather than a separate permission store

### Requirement: Field decisions drive frontend rendering and backend enforcement
Field authorization decisions SHALL drive frontend visible/editable/required/masked field rendering while owner services remain the enforcement source for reads and writes.

#### Scenario: Field readonly
- **WHEN** a field decision marks a document field readonly for the current actor and status
- **THEN** the frontend renders it readonly and the owner service rejects unauthorized write attempts even if a caller bypasses the frontend

#### Scenario: Field hidden
- **WHEN** a field decision marks a sensitive field hidden
- **THEN** the owner service omits it from responses and the frontend does not render the field

### Requirement: Batch decisions support page action rendering
The authorization system SHALL support batch evaluation for page action rendering so a frontend page can request decisions for multiple actions and fields without repeated round trips.

#### Scenario: Document page action batch
- **WHEN** a document page loads actions and fields for a purchase order
- **THEN** the backend can evaluate all relevant resource/action and field decisions in one batch and return stable entries for the frontend to render
