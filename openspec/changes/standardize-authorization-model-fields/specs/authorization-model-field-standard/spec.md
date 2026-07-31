## ADDED Requirements

### Requirement: Every persisted model has stable identity and time metadata
Every mutable persisted entity SHALL contain `id`, `created_at`, and `updated_at`; `id` SHALL be globally stable and SHALL NOT encode tenant or business meaning. Immutable event and audit models SHALL contain `id` and `occurred_at` or `created_at`, and MUST NOT expose a mutable `updated_at` contract unless correction is an explicitly audited operation.

#### Scenario: Create a mutable business entity
- **WHEN** an owner service persists a new mutable business entity
- **THEN** it assigns a non-null stable `id`, `created_at`, and `updated_at`

#### Scenario: Persist immutable audit event
- **WHEN** a service records an immutable authorization audit event
- **THEN** it stores stable identity and occurrence time without treating the event as an ordinary mutable row

### Requirement: Tenant-scoped models have explicit non-null tenant identity
Every tenant-owned business fact, permission subject, role, subject relation, authorization resource, action, grant, policy, audit record, action execution, and tenant read-model projection SHALL contain a non-null `tenant_id`. Platform-global definitions SHALL use an explicit `GLOBAL` scope value or a separate explicit `scope_type`; null or blank tenant identity MUST NOT mean global access.

#### Scenario: Persist tenant role
- **WHEN** tenant `T1` creates a role
- **THEN** the role row stores `tenant_id = T1` and all role codes and references are constrained inside `T1`

#### Scenario: Persist global definition
- **WHEN** the platform publishes a globally reusable definition
- **THEN** the row stores an explicit global scope and is not represented by a null tenant

### Requirement: Mutable governed models record actor metadata
Mutable business facts, configuration, permission subjects, resources, grants, and policies SHALL contain `created_by` and `updated_by`. System-originated changes SHALL use a registered system actor identifier rather than null. Models that support recoverable deletion SHALL additionally contain `deleted`, `deleted_at`, and `deleted_by`, or an equivalent explicitly documented deletion state.

#### Scenario: Administrator changes a grant
- **WHEN** an administrator changes an authorization grant
- **THEN** the grant records the authenticated actor in `updated_by` and advances `updated_at`

#### Scenario: System synchronizes a resource
- **WHEN** an owner service synchronizes authorization resources without an interactive user
- **THEN** the rows identify the registered service actor instead of storing a null actor

### Requirement: Concurrent mutable aggregates expose a version field
Every aggregate that can be edited concurrently, cached, published, or projected SHALL contain a monotonic `version` or domain-specific version field. Conditional updates SHALL compare the expected version in the database transaction; timestamps MUST NOT be the sole concurrency token.

#### Scenario: Concurrent policy edit
- **WHEN** two administrators update the same policy using the same expected version
- **THEN** only one update advances the persisted version and the other receives a conflict

### Requirement: Data-scoped facts expose typed ownership columns
A model protected by SELF or organization data scopes SHALL expose stable typed columns that the owner service can compile into predicates. SELF scope SHALL use `owner_id`, `created_by`, `submitted_by`, or another registered owner column; organization scope SHALL use `org_unit_id` or another registered organization column. A JSON payload path MUST NOT be the only tenant, owner, or organization boundary.

#### Scenario: Compile SELF data scope
- **WHEN** a query is authorized with SELF scope
- **THEN** the owner service can bind the current user to a registered typed ownership column without parsing arbitrary JSON

#### Scenario: Required organization field is absent
- **WHEN** an organization-scoped query targets a model without a registered organization column or safe owner projection
- **THEN** authorization fails closed and the query is not executed unfiltered

### Requirement: Business-state models expose explicit lifecycle status
Business documents, users, roles, resources, actions, policies, definitions, and projections with lifecycle semantics SHALL contain a typed `status` or `lifecycle_status`. Status values SHALL be constrained by an enum, check constraint, or governed dictionary and MUST NOT be inferred solely from deletion metadata.

#### Scenario: Disable role without deleting history
- **WHEN** an administrator disables a role
- **THEN** the role keeps its identity and references while its explicit status prevents new authorization effects

### Requirement: Global Action mutations carry correlation and idempotency fields
Models created or changed by a Global Action SHALL persist `action_id` and `trace_id`. Models that initiate or own an externally retryable side effect SHALL also persist `idempotency_key` with a tenant-scoped uniqueness constraint; workflow-bound models SHALL persist the relevant `process_instance_id`, `workflow_id`, or owner execution reference.

#### Scenario: Retry state-changing action
- **WHEN** the same tenant retries an action with the same action type and idempotency key
- **THEN** the owner service resolves the existing execution instead of creating a duplicate side effect

### Requirement: Permission subjects and relations have tenant-verifiable identity
User models SHALL contain at least `id`, `tenant_id`, stable login identifier, credential reference or password hash, and `status`. Role models SHALL contain at least `id`, `tenant_id`, `role_code`, `role_name`, and `status`. User-role and comparable subject relation models SHALL contain `tenant_id`, both subject identifiers, audit metadata, and a tenant-scoped unique constraint over the relation.

#### Scenario: Assign cross-tenant role
- **WHEN** a user from `T1` is assigned a role whose persisted `tenant_id` is `T2`
- **THEN** the database or owner service rejects the relation before it affects permissions

### Requirement: Authorization registry models expose deterministic keys
Authorization resources SHALL contain `tenant_id`, `resource_code`, `resource_type`, `owner_service`, `lifecycle_status`, and audit metadata. Resource actions SHALL contain `tenant_id`, `resource_code`, `action_code`, `action_category`, and `status`. Field registry entries SHALL contain `tenant_id`, `resource_code`, `field_key`, field classification metadata, and `status`. Their natural keys SHALL be unique inside the tenant.

#### Scenario: Two tenants register the same resource code
- **WHEN** tenants `T1` and `T2` register the same resource code
- **THEN** both rows can exist while duplicate active registration inside one tenant is rejected

### Requirement: Grant and policy models are explainable and time-bounded
Authorization grants and policies SHALL contain `tenant_id`, subject type and identifier, resource and action identifiers, `effect`, `status`, audit metadata, and stable identity. Policy models SHALL additionally persist combination semantics and policy type. If temporary authorization is supported, `valid_from` and `valid_until` SHALL be explicit typed timestamps rather than embedded conditions.

#### Scenario: Explain denied action
- **WHEN** an action is denied by a role grant or policy
- **THEN** the decision can identify the tenant, subject, resource, action, effect, policy identity, status, and applicable version without inspecting unstructured text

### Requirement: Audit and decision records preserve evidence without secrets
Authorization audit and decision records SHALL contain `id`, `tenant_id`, actor or subject identity, resource, action, decision/effect, reason code, policy or authorization versions, `trace_id`, owner service, and occurrence time. When applicable they SHALL contain `action_id`. They MUST NOT persist raw passwords, tokens, credentials, or unredacted sensitive business field values.

#### Scenario: Record field authorization decision
- **WHEN** a sensitive field is hidden or masked
- **THEN** the audit record stores the field key, applied rule, reason, and versions without storing the raw field value

### Requirement: Read models preserve source and freshness metadata
Tenant read-model projections SHALL contain `tenant_id`, a stable projection identity, source aggregate or business key, `source_event_id` or equivalent replay cursor, projection version or offset, and `updated_at`. A projection MUST NOT become an authorization source of truth unless explicitly designated and consistency-governed.

#### Scenario: Replay duplicate event
- **WHEN** a projection receives an event whose source event ID was already applied
- **THEN** it detects the duplicate and does not duplicate the projected fact

### Requirement: Model field contracts are enforced automatically
CI SHALL verify required columns, nullability, unique constraints, indexes, entity mappings, and prohibited null-as-global conventions for governed models. Exceptions SHALL require an explicit model classification and reviewed waiver; silently omitting fields is not permitted.

#### Scenario: Role migration omits tenant field
- **WHEN** a migration introduces or modifies a role table without non-null `tenant_id` and a tenant-scoped role-code constraint
- **THEN** the architecture or schema contract test blocks the change

