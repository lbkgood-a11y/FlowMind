## ADDED Requirements

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
The system SHALL expose internal single and batch authorization decision APIs that evaluate tenant boundary, subject roles, direct grants, registered resources, action grants, deny precedence, data scope, field rules, and required guard templates.

#### Scenario: Allow granted action
- **WHEN** a user has an active role grant for resource `LOWCODE_FORM:EXPENSE` and action `APPROVE`
- **THEN** the decision API returns an allowed function decision with the matched grant, resource, action, tenant, subject, and policy versions

#### Scenario: Deny unknown resource
- **WHEN** a caller requests a decision for an unregistered resource
- **THEN** the decision API returns denied with a fail-closed reason and does not infer permission from request path alone

#### Scenario: Deny takes precedence
- **WHEN** one matching grant allows an action and another active matching grant denies the same action
- **THEN** the decision API returns denied and includes the deny grant in the explanation

#### Scenario: Batch action decision
- **WHEN** a runtime page requests decisions for multiple document actions and fields
- **THEN** the batch decision API returns one stable decision entry per requested resource/action without requiring multiple network round trips

### Requirement: Authorization grants are decoupled from menus
The system SHALL allow roles or users to be granted resource actions independently from menu membership while preserving existing menu-derived permission behavior during migration.

#### Scenario: Resource grant without menu
- **WHEN** a role is granted `CUSTOM_DOC:CONTRACT:EXPORT` without any corresponding menu row
- **THEN** authorization decisions for contract export can allow the role based on the resource grant

#### Scenario: Existing menu permission remains valid
- **WHEN** a role still receives a legacy permission through `sys_role_menu` and `sys_menu.permission_code`
- **THEN** compatibility logic preserves existing API permission checks until the permission is migrated to direct resource grants

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
The system SHALL model domain guards as structured guard requirements owned by the business service and SHALL NOT require `service-auth` to call domain services for state-sensitive checks.

#### Scenario: Workflow task guard succeeds
- **WHEN** a central decision allows `APPROVE` and the workflow service confirms the task is pending and assigned or candidate to the current user
- **THEN** the final authorization result allows the approval action

#### Scenario: Self-approval guard fails
- **WHEN** a central decision allows `APPROVE` but the document guard detects the current user is the submitter and self-approval is disallowed
- **THEN** the final authorization result denies the action and includes the failed guard reason

#### Scenario: Archived document guard fails
- **WHEN** a central decision allows `EDIT` but the owning service detects the document is archived
- **THEN** the final authorization result denies editing without changing resource grants

### Requirement: Decisions are explainable and auditable
The system SHALL provide explainable decision output and record auditable authorization decisions for protected business operations.

#### Scenario: Explain denied decision
- **WHEN** an administrator previews why a user cannot export a form
- **THEN** the system returns the evaluated subject, resource, action, grants, data scope, field rules, guard requirements, and denial reasons without exposing unrelated tenant data

#### Scenario: Audit enforced decision
- **WHEN** a protected document operation is evaluated in enforcement mode
- **THEN** the system records actor, tenant, resource, action, allowed flag, reasons, policy versions, trace id, and owner service metadata

#### Scenario: Avoid sensitive payload logging
- **WHEN** a decision references form or document fields
- **THEN** the audit log stores field keys and policy results but not raw sensitive field values

### Requirement: Tenant context is authoritative for authorization
The system SHALL evaluate authorization against the authenticated user's real tenant context and MUST NOT rely on a hardcoded default tenant for production decisions.

#### Scenario: Token validates with tenant
- **WHEN** the gateway validates a token for a tenant user
- **THEN** the propagated security context includes the user's effective tenant id used by authorization and data-scope decisions

#### Scenario: Cross-tenant decision denied
- **WHEN** a user from tenant `T1` requests access to tenant `T2` resources without platform-global authority
- **THEN** the decision is denied before grants, field rules, or domain guards can widen access
