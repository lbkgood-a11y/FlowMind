## 1. Authorization Core Schema

- [x] 1.1 Add `service-auth` migrations for authorization resources, actions, grants, field policies, guard templates, decision logs, and version metadata.
- [x] 1.2 Add tenant-aware entities, mappers, and DTOs for the new authorization tables.
- [x] 1.3 Add unique indexes that scope resource and action codes by tenant and owner service.
- [x] 1.4 Add compatibility migration or backfill logic from existing `sys_permission` and `sys_menu.permission_code` rows into registered resources.
- [x] 1.5 Add tenant context support to auth validation so propagated security context uses the user's real tenant instead of a hardcoded default.
- [x] 1.6 Add authorization version bumping for grant, resource, data-scope, field-policy, and guard-template changes.

## 2. Decision API And Engine

- [x] 2.1 Add shared common DTOs for `AuthorizationDecisionRequest`, `AuthorizationDecisionResponse`, data-scope result, field rule result, guard requirement, guard result, and decision reason.
- [x] 2.2 Implement `service-auth` resource registration and idempotent synchronization services.
- [x] 2.3 Implement role and direct-subject authorization grant CRUD with allow and deny effects.
- [x] 2.4 Implement decision evaluation for tenant boundary, registered resource, action grant, deny precedence, and restrictive default behavior.
- [x] 2.5 Extend effective data-scope evaluation to return service-executable scope results for all supported scope types.
- [x] 2.6 Implement field-policy evaluation for visible, masked, hidden, editable, read-only, and denied outcomes.
- [x] 2.7 Implement guard-template lookup so decisions return required domain guards without calling domain services.
- [x] 2.8 Implement internal `POST /internal/v1/authz/decide` and `POST /internal/v1/authz/batch-decide` endpoints secured by internal service tokens.
- [x] 2.9 Add decision audit logging for enforcement mode with trace id, tenant, subject, resource, action, result, reasons, and policy versions.
- [x] 2.10 Add decision preview support for administrators without recording sensitive payload values.
- [x] 2.11 Add compatibility adapters so existing `@RequirePermission` and `@RequireDataScope` continue to work during migration.

## 3. RBAC And Data Policy Migration

- [x] 3.1 Update permission resolution to include direct authorization grants without requiring `sys_role_menu`.
- [x] 3.2 Preserve legacy menu-derived permission checks for existing management endpoints.
- [x] 3.3 Update `DataPolicyService` to evaluate against effective tenant context instead of fixed `default`.
- [x] 3.4 Implement deny-precedence tests for mixed allow and deny grants.
- [x] 3.5 Implement restrictive-default tests for unknown resources, missing grants, and missing data policies.
- [x] 3.6 Add diagnostics for stale resources whose owner service no longer synchronizes them.

## 4. Lowcode Governance Integration

- [x] 4.1 Extend lowcode form field metadata to include sensitivity classification, mask strategy, and default field authorization hints.
- [x] 4.2 Add a lowcode authorization sync client for idempotently registering forms, applications, actions, fields, and guards with `service-auth`.
- [x] 4.3 Update form publication to synchronize authorization resources before the form becomes runtime-visible.
- [x] 4.4 Add retryable failure handling for publication when authorization resource synchronization fails.
- [x] 4.5 Update application publication validation to reject actions that cannot map to supported authorization actions.
- [x] 4.6 Add tests for lowcode publish authorization sync, duplicate sync, and sync failure behavior.

## 5. Lowcode Runtime Enforcement

- [x] 5.1 Map lowcode runtime operations to canonical actions such as `VIEW`, `CREATE`, `EDIT`, `SUBMIT`, `APPROVE`, `EXPORT`, `DESIGN`, and `PUBLISH`.
- [x] 5.2 Update form instance create, detail, list, update, workflow binding, status update, and export paths to call the decision API.
- [x] 5.3 Compile authorization data-scope results into tenant-safe lowcode form instance predicates.
- [x] 5.4 Fail closed when lowcode receives an unsupported or unresolved data-scope result.
- [x] 5.5 Apply field read rules before list, detail, workflow, and export responses are serialized.
- [x] 5.6 Apply field write rules before create, edit, submit, approval, and correction payloads are accepted.
- [x] 5.7 Update lowcode application descriptors to batch-authorize application, page, action, and field visibility.
- [x] 5.8 Add tests for self scope, organization scope, hidden fields, masked fields, denied writes, and denied exports.

## 6. Workflow And Domain Guard Integration

- [x] 6.1 Add common guard result composition utilities for combining central decisions with service-local guard results.
- [x] 6.2 Add workflow guard checks for pending task status, candidate or assignee membership, and claimed-by-other-user denial.
- [x] 6.3 Add no-self-approval guard support for workflow-backed lowcode form actions.
- [x] 6.4 Update workflow task operations to expose guard failure reasons usable by lowcode runtime descriptors and diagnostics.
- [x] 6.5 Add tests for allowed candidate approval, non-candidate denial, claimed task denial, and self-approval denial.

## 7. Handwritten Business Document Integration

- [x] 7.1 Define a custom document authorization manifest format for handwritten services.
- [x] 7.2 Add a startup or deployment synchronization path for handwritten service resources.
- [x] 7.3 Add a reference handwritten document integration using the shared decision API and local guards.
- [x] 7.4 Add tests that a custom document can be authorized without menu entries.
- [x] 7.5 Document service-side enforcement patterns for DTO masking, write validation, and data-scope predicate compilation.

## 8. Frontend And Administration Experience

- [x] 8.1 Update role authorization management to configure function permissions separately from menu navigation.
- [x] 8.2 Add resource tree or grouped resource views for lowcode apps, lowcode forms, fields, workflow actions, and custom documents.
- [x] 8.3 Add data-range configuration using business labels for self, organization, assigned organizations, participated, candidate tasks, and all.
- [x] 8.4 Add field-rule configuration for visible, masked, hidden, editable, read-only, and denied outcomes.
- [x] 8.5 Add guard-template configuration using business-language templates instead of raw expressions.
- [x] 8.6 Add decision preview UI for explaining allowed and denied decisions to authorized administrators.
- [x] 8.7 Update lowcode runtime UI to consume allowed actions and field rules from descriptors.

## 9. Verification And Rollout

- [x] 9.1 Add unit tests for decision evaluation, deny precedence, tenant denial, data-scope results, field rules, guard requirements, and audit output.
- [x] 9.2 Add integration tests covering gateway-auth context, internal decision APIs, lowcode publication sync, and lowcode runtime enforcement.
- [x] 9.3 Add workflow integration tests covering central decision plus local guard composition.
- [x] 9.4 Add regression tests proving legacy `@RequirePermission`, menu-derived permissions, and existing data-scope behavior remain compatible.
- [x] 9.5 Add migration and rollback documentation for enabling authorization enforcement feature flags.
- [x] 9.6 Add administrator documentation for the four-tab configuration model: function permissions, data range, field rules, and business rules.
- [x] 9.7 Run targeted backend tests, frontend checks for affected pages, and `openspec validate --all --strict`.
