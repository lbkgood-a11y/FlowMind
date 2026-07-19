## Context

The current authorization stack has useful foundations:

- `platform-gateway` validates access tokens through `service-auth` and propagates user, tenant, role, permission, and policy version headers.
- `common-core` provides `SecurityContextHolder`, `@RequirePermission`, `@RequireDataScope`, and `DataScopeProvider`.
- `service-auth` stores RBAC roles, permissions, menus, role-menu grants, and data policies.
- `service-lowcode` already resolves data scope for form instance `CREATE` and `QUERY`.
- `service-workflow-engine` owns workflow task state, candidates, assignees, and approval transitions.

The main production gap is that permissions are still centered on menu/API permission codes, while rapid-development forms and handwritten document services need a shared business authorization model. The model must stay simple for enterprise administrators, but strong enough for dynamic lowcode resources, custom resources, data scopes, field rules, workflow tasks, and explainable decisions.

## Goals / Non-Goals

**Goals:**

- Provide one tenant-scoped authorization language for lowcode forms, lowcode runtime applications, workflow-backed documents, and handwritten business documents.
- Decouple permission grants from menu membership while keeping menus as a compatible navigation and button model.
- Add resource registration and synchronization so dynamic lowcode publication does not require manual auth Flyway seeds.
- Return a structured authorization decision that includes function permission, data scope, field rules, required domain guards, and explainable reasons.
- Keep domain-state checks inside the owning business service instead of making `service-auth` depend on every business domain.
- Preserve current `@RequirePermission`, `@RequireDataScope`, data-policy, menu, and gateway behavior during migration.

**Non-Goals:**

- Do not introduce a full free-form ABAC expression engine in the first production step.
- Do not move workflow task ownership, form data ownership, or custom document business rules into `service-auth`.
- Do not make frontend visibility a security boundary; services remain responsible for final enforcement.
- Do not remove existing permission codes or role-menu behavior in the first implementation phase.

## Decisions

### 1. Use RBAC plus structured resource authorization, not full ABAC

TrioBase will keep RBAC as the main administrative model and add structured resource, action, data-scope, field, and guard concepts.

Canonical resources use stable business codes:

```text
LOWCODE_APP:<appKey>
LOWCODE_FORM:<formKey>
LOWCODE_FORM_INSTANCE:<formKey>
FIELD:<formKey>.<fieldKey>
WORKFLOW_TASK:<processKey>
CUSTOM_DOC:<documentType>
```

Canonical actions use product language:

```text
VIEW, CREATE, EDIT, DELETE, SUBMIT, APPROVE, REJECT, TRANSFER, EXPORT, DESIGN, PUBLISH, OFFLINE
FIELD_READ, FIELD_WRITE
```

Alternative considered: a generic ABAC expression language. It was rejected for the first step because arbitrary expressions are harder for enterprise administrators to understand, test, audit, and migrate. Guard templates can later evolve toward ABAC-like conditions without exposing raw expressions as the default product surface.

### 2. Add an authorization registry and grant model in `service-auth`

`service-auth` becomes the source of truth for registered resources, actions, field policies, data-scope policies, grants, and decision traces.

Suggested logical model:

- `sys_auth_resource`: tenant, resource code, type, owner service, business object id, lifecycle status, display name.
- `sys_auth_action`: resource/action pair, action category, description, status.
- `sys_auth_grant`: subject type, subject id, resource code, action code, effect, status.
- `sys_auth_field_policy`: subject, resource, field key, read mode, write mode, mask strategy, status.
- `sys_auth_guard_template`: guard code, owner service, supported resource types, configurable parameters, description.
- `sys_auth_decision_log`: request, subject, resource, action, result, reasons, policy versions, trace id.

Existing `sys_permission` and `sys_role_permission` can be adapted into this model. Existing `sys_menu.permission_code` remains compatible, but new authorization grants should not require a menu row.

Alternative considered: continue using hidden menu buttons for every permission. This was rejected because dynamic lowcode publication and custom document actions would create large numbers of fake menu entries and blur navigation with authorization.

### 3. Use a central decision API with local service guard composition

`service-auth` exposes internal decision APIs:

```text
POST /internal/v1/authz/decide
POST /internal/v1/authz/batch-decide
```

The central decision evaluates:

- tenant boundary
- subject roles and direct grants
- resource/action registration
- allow/deny grants, with deny taking precedence
- data-scope policies
- field policies
- required guard templates
- decision reasons and policy versions

Business services evaluate domain guards locally and combine them with the central result:

```text
central decision: can this subject generally APPROVE this resource?
local guard: is this task pending, assigned/candidate to this user, and not self-approval?
final decision: allowed only if both pass
```

This keeps `service-auth` fast and domain-neutral. It also avoids long synchronous chains from auth into workflow, lowcode, or custom services.

Alternative considered: make `service-auth` call each business service to evaluate guards. This was rejected because it would couple auth to every domain and create fragile synchronous authorization chains.

### 4. Make data-scope decisions executable by services

The decision response includes a data-scope result with stable scope types:

```text
NONE, SELF, OWN_ORG, OWN_ORG_AND_CHILDREN, ASSIGNED_ORGS, PARTICIPATED, CANDIDATE_TASKS, ALL
```

Services remain responsible for compiling this into safe database predicates using their own schema:

- lowcode forms map `SELF` to `submitted_by = currentUser`.
- organization scopes map to owned or assigned organization ids.
- workflow task lists map `CANDIDATE_TASKS` to candidate/assignee tables.
- handwritten services map the same scopes to their owner, department, participant, or tenant columns.

If a service cannot compile a scope safely, it must fail closed.

### 5. Enforce field rules service-side

Field authorization is part of the decision result. Read modes:

```text
VISIBLE, MASKED, HIDDEN
```

Write modes:

```text
EDITABLE, READONLY, DENIED
```

Lowcode applies these rules to JSON form data before returning list/detail responses and before accepting create/edit/approval payloads. Handwritten services apply the same semantics to DTO mapping and validation. Frontend uses field rules for rendering, but services enforce them again.

### 6. Lowcode publication synchronizes authorization resources

When a form or runtime application is published, `service-lowcode` declares its required authorization resources to `service-auth` through an internal synchronization API. Publication succeeds only when resource synchronization succeeds or is explicitly retriable without exposing a partially authorized runtime app.

Lowcode should generate predictable resource/action defaults from metadata:

- app view/open actions
- page view actions
- document create/view/edit/submit/export actions
- configured workflow actions
- field read/write entries for each published field
- designer lifecycle actions for draft/publish/offline

Existing seeded permissions remain as bootstrap permissions for lowcode management pages.

### 7. Handwritten services declare manifests

Handwritten business services should declare document resources in code or configuration manifests. Startup or deployment synchronization registers these resources with `service-auth`.

Example manifest shape:

```json
{
  "service": "service-contract",
  "resources": [
    {
      "code": "CUSTOM_DOC:CONTRACT",
      "name": "合同单据",
      "actions": ["VIEW", "CREATE", "EDIT", "SUBMIT", "APPROVE", "EXPORT", "ARCHIVE"],
      "fields": ["amount", "customerName", "paymentTerms", "attachment"],
      "guards": ["DOCUMENT_STATUS", "WORKFLOW_CANDIDATE", "NO_SELF_APPROVAL"]
    }
  ]
}
```

This gives custom services the same authorization model without forcing them into the lowcode metadata schema.

### 8. Version decisions for cache and invalidation

Authorization responses include role, grant, data-policy, field-policy, and guard-template versions. Gateway token validation can continue propagating current version headers, and services can use short-lived caches for decision results only when versions match.

State-sensitive guard checks are not cacheable unless the owning service explicitly marks them safe.

## Risks / Trade-offs

- [Risk] Resource registry drift between lowcode/custom services and `service-auth` → require idempotent sync, owner service fields, stale-resource status, and health/diagnostic endpoints.
- [Risk] Permission UI becomes too complex → present four business tabs: function permissions, data range, field rules, business rules.
- [Risk] Central decision API becomes a latency hotspot → support batch decisions, short-lived versioned caches, and local compatibility checks for simple API permissions.
- [Risk] Data-scope compilation differs across services → provide common DTOs and test fixtures, but keep schema-specific predicate construction inside each service.
- [Risk] Field masking can be bypassed by old endpoints → add migration tasks to identify and protect document/form response endpoints before exposing production data.
- [Risk] Deny/allow combination surprises administrators → document deny precedence and surface decision reasons in preview and audit logs.

## Migration Plan

1. Add `service-auth` resource registry, grants, field policies, decision APIs, and compatibility adapters without changing existing permissions.
2. Backfill existing `sys_permission` rows and menu permission codes into registered API/menu resources.
3. Update token validation and internal clients to include real tenant context and authorization version metadata.
4. Update `service-lowcode` publication to synchronize lowcode resources/actions/fields.
5. Update lowcode runtime list/detail/submit/action endpoints to call authorization decisions and enforce data and field rules.
6. Update workflow task operations to combine central authorization with local task guards.
7. Add custom-service manifest support and migrate the first handwritten document module as a reference.
8. Add frontend role configuration and decision preview screens after service-side behavior is stable.

Rollback strategy:

- Keep existing `sys_menu`, `sys_permission`, `@RequirePermission`, and `@RequireDataScope` paths active.
- Gate new decision enforcement behind service-level feature flags during rollout.
- If decision API errors occur, production document operations fail closed while management pages can temporarily use legacy checks where explicitly configured.

## Open Questions

- Should direct user grants be included in the first implementation, or should the UI expose only role grants while the data model supports both?
- Which handwritten document service should become the reference integration after lowcode forms?
- Should lowcode field masking strategies start with fixed presets only, or include tenant-configurable mask formats in the first release?
