## Why

TrioBase already has RBAC, menu-derived permission codes, tenant headers, and data-scope policies, but rapid-development forms, runtime applications, workflow tasks, and handwritten business documents do not yet share one production-grade authorization contract. This change makes permissions understandable for enterprise administrators while giving lowcode and custom services the same runtime decision model for functions, data, fields, and domain guards.

## What Changes

- Introduce a centralized enterprise authorization model in `service-auth` for tenant-scoped resource registration, grants, decision evaluation, batch decision, and explainable denial reasons.
- Decouple authorization grants from menu membership so menus remain navigation entries while resources/actions become the durable authorization surface.
- Support both lowcode-generated resources and handwritten service-declared resources under the same resource/action/field/data-scope vocabulary.
- Extend data-scope evaluation beyond `SELF` and `ALL` consumption so organization scopes, assigned organizations, deny policies, and restrictive defaults are consistently enforced by runtime services.
- Add structured field authorization for document/form fields, including visible, hidden, masked, editable, and read-only outcomes.
- Add a domain-guard contract so workflow candidate checks, self-approval prevention, document status rules, archived locks, and amount-threshold rules can participate in an authorization decision without becoming generic RBAC rules.
- Make lowcode form and application publication register or synchronize required authorization resources automatically instead of relying on manual migration seeds for every dynamic document.
- Add decision trace/audit output so operators can explain why a user can or cannot see, edit, approve, export, or publish a document.
- Preserve existing `@RequirePermission`, `@RequireDataScope`, menu, role, and data-policy behavior during migration; no breaking API removal is intended in the first implementation phase.

## Capabilities

### New Capabilities
- `enterprise-authorization-model`: Defines tenant-scoped resource registration, role/user grants, authorization decisions, data-scope and field decisions, domain-guard integration, lowcode/custom resource onboarding, and decision explainability.

### Modified Capabilities
- `lowcode-form-governance`: Published lowcode forms must declare and synchronize authorization resources and field metadata required by the authorization model.
- `lowcode-application-runtime`: Published runtime applications must expose only applications, pages, and actions allowed by authorization decisions.
- `lowcode-form-data-runtime`: Form instance submit, list, detail, update, workflow binding, approval-related actions, export, and returned fields must enforce authorization decisions.

## Impact

- `trio-base-services/service-auth`: New authorization registry, grant, decision, field-policy, guard-template, internal decision APIs, migration path from menu-derived permissions, and decision audit records.
- `trio-base-common/common-core`: New shared authorization decision DTOs, annotations or clients, and compatibility adapters for current permission and data-scope checks.
- `trio-base-services/service-lowcode`: Publish-time authorization resource synchronization, runtime decision calls, field filtering/masking, action visibility decisions, and migration from fixed `CREATE`/`QUERY` checks to canonical actions.
- `trio-base-services/service-workflow-engine`: Domain-guard participation for task candidate, assignee, task status, and self-approval checks when document workflow actions are evaluated.
- Handwritten business services: A standard way to declare document resources and call the same decision API for custom document operations.
- `platform-gateway`: Continues to validate tokens and propagate user context; may propagate authorization/cache versions but should not own business authorization decisions.
- Frontend: Role configuration, lowcode app descriptors, buttons, fields, and explain/preview screens consume decision results instead of duplicating permission logic.
