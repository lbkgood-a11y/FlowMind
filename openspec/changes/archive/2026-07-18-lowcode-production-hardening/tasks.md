## 1. Migration Ownership And Baseline Schema

- [x] 1.1 Enable Flyway for `service-lowcode` with a dedicated lowcode Flyway history table and local/test configuration
- [x] 1.2 Add lowcode-owned additive migration for `tenant_id`, lifecycle status normalization, audit fields, and version metadata on form definitions
- [x] 1.3 Add lowcode-owned migration for form instance process binding columns, workflow status, trace/audit metadata, and unique process binding index
- [x] 1.4 Add tenant/version-aware unique indexes for `tenant_id + form_key + version` and at most one draft per `tenant_id + form_key`
- [x] 1.5 Backfill existing lowcode records to `tenant_id = GLOBAL`, `version = 1`, and stable audit metadata
- [x] 1.6 Stop adding lowcode table mutations to auth migrations and document existing auth lowcode migrations as compatibility debt
- [x] 1.7 Add migration integration tests proving `service-lowcode` can create its schema without relying on `service-auth`

## 2. Security Context And API Authorization

- [x] 2.1 Extend auth token validation DTO/service to include tenant ID and roles needed by downstream services
- [x] 2.2 Extend platform-gateway `JwtAuthFilter` to forward `X-Tenant-Id`, `X-User-Roles`, and version headers when available
- [x] 2.3 Add `@RequirePermission` to public lowcode form definition create/list/detail/update/publish/offline/version APIs
- [x] 2.4 Add `@RequirePermission` to form instance submit/list/detail/process-binding APIs
- [x] 2.5 Add method-level authorization tests for lowcode definition and instance controllers
- [x] 2.6 Add gateway tests proving tenant and role context propagation is preserved

## 3. Form Definition Lifecycle And Validation

- [x] 3.1 Introduce tenant-aware form definition, field definition, and response DTO fields without breaking existing callers
- [x] 3.2 Add create/update APIs that validate form key, lifecycle state, schema JSON, UI Schema JSON, and field metadata server-side
- [x] 3.3 Add a lowcode form schema validator aligned with the workflow supported field/widget registry
- [x] 3.4 Implement derive-new-version from the latest non-draft form definition
- [x] 3.5 Enforce published/offline immutability and reject in-place edits to non-draft definitions
- [x] 3.6 Implement publish validation that records immutable schema hash and publication audit metadata
- [x] 3.7 Implement offline lifecycle for published form versions without affecting existing workflow snapshots
- [x] 3.8 Update published snapshot endpoint to return tenant, version, schema hash, and immutable published-only data
- [x] 3.9 Add service tests for duplicate drafts, cross-tenant form keys, invalid schema, invalid widget, publish, offline, and derive-new-version

## 4. Form Data Runtime Hardening

- [x] 4.1 Persist form version, tenant ID, schema hash, and submitter audit metadata on every form instance
- [x] 4.2 Validate submitted form data against the selected published form version before inserting
- [x] 4.3 Return structured field errors for lowcode form submission validation failures
- [x] 4.4 Move form instance list pagination to database-level pagination with stable sorting
- [x] 4.5 Enforce tenant and DataScope checks on form instance detail API
- [x] 4.6 Enforce tenant and DataScope checks on process binding API
- [x] 4.7 Make repeated identical process binding requests idempotent and conflicting bindings reject cleanly
- [x] 4.8 Add workflow status update/audit support for process-bound instances
- [x] 4.9 Add service and repository tests for validation, pagination, detail denial, idempotent binding, and cross-tenant denial

## 5. Rapid Application Metadata Model

- [x] 5.1 Add lowcode application core tables for logical app, immutable app version, page metadata, and action metadata
- [x] 5.2 Add application entities, mappers, DTOs, and lifecycle constants
- [x] 5.3 Define and implement allowlisted JSON Schemas for app page metadata, list columns, filters, detail sections, and actions
- [x] 5.4 Reject app metadata containing arbitrary script, SQL, URL, dynamic class name, connector credentials, or free Prompt execution fields
- [x] 5.5 Implement create/update/derive/publish/offline APIs for application drafts and versions
- [x] 5.6 Validate app publication against published form references, known form fields, permissions, and optional workflow bindings
- [x] 5.7 Add application lifecycle and validation tests including invalid references and unsafe metadata

## 6. Generic Application Runtime APIs

- [x] 6.1 Add API to list tenant-visible published lowcode applications for the current user
- [x] 6.2 Add API to fetch a published application runtime descriptor by app key/version
- [x] 6.3 Add API to query form instances through an application list descriptor with tenant, permission, and data-scope enforcement
- [x] 6.4 Add API to fetch a form instance detail through an application descriptor
- [x] 6.5 Add runtime action endpoint for create-and-save form submissions
- [x] 6.6 Add runtime action endpoint for submit-and-launch workflow using configured process binding and stable idempotency keys
- [x] 6.7 Add runtime retry endpoint for pending workflow launch state
- [x] 6.8 Add integration tests for available apps, list/detail runtime, submit, launch, workflow failure recovery, and stale workflow version

## 7. Frontend Form Governance UI

- [x] 7.1 Update lowcode form API types for tenant, version, lifecycle, schema hash, and immutable publication metadata
- [x] 7.2 Replace JSON-first create flow with a safer field-oriented editor for supported MVP field types and widgets
- [x] 7.3 Keep read-only JSON Schema/UI Schema preview for diagnostics
- [x] 7.4 Add derive-new-version, publish, offline, and version history actions to form management UI
- [x] 7.5 Surface server-side form definition and data validation errors in business language
- [x] 7.6 Add frontend tests for widget registry, form editor validation, publish/offline visibility, and version derivation

## 8. Frontend Generic Application Runtime

- [x] 8.1 Add lowcode application API client types and runtime descriptor parsing
- [x] 8.2 Add generic application center route that lists published applications visible to the user
- [x] 8.3 Add generic application list page renderer driven by published metadata columns, filters, status, and row actions
- [x] 8.4 Add generic create/detail form renderer using published form snapshots and existing dynamic form registry
- [x] 8.5 Add generic submit-and-launch workflow action with pending workflow retry state
- [x] 8.6 Add route compatibility from the current expense page/menu to the published generic application runtime
- [x] 8.7 Add component tests for runtime descriptor rendering, permission-hidden actions, workflow pending state, and validation errors

## 9. Expense Sample Migration

- [x] 9.1 Add lowcode seed/backfill for an `expense_report` rapid-development application matching the current expense behavior
- [x] 9.2 Register or update auth permissions, menus, role-menu grants, and data policies for the generic expense application
- [x] 9.3 Verify the migrated app uses the existing published `expense` form and `expense_report` workflow package
- [x] 9.4 Preserve the existing expense acceptance flow during migration
- [x] 9.5 Add Playwright smoke covering generic expense app open, submit, workflow launch, pending retry, approval, and closure visibility

## 10. Workflow Integration Compatibility

- [x] 10.1 Update workflow lowcode internal client/DTO usage to tolerate added snapshot fields
- [x] 10.2 Add workflow-engine integration tests proving published form snapshots remain immutable across lowcode new-version publication
- [x] 10.3 Add tests proving workflow publication rejects draft/offline lowcode form references
- [x] 10.4 Ensure process business closure paths remain owned by workflow-engine and do not call lowcode for side-effect execution

## 11. Documentation And Governance

- [x] 11.1 Update `docs/api/process-runtime-mvp.md` with the correct lowcode internal snapshot path and immutable form version contract
- [x] 11.2 Update `docs/process-runtime-local-development.md` with lowcode Flyway and startup requirements
- [x] 11.3 Update `openspec/specs/process-platform/design.md` to remove stale language that assigns process package CRUD/publication to lowcode
- [x] 11.4 Add lowcode production hardening documentation covering ownership, lifecycle, tenant isolation, and metadata safety
- [x] 11.5 Add runbook notes for rolling back generic application runtime while preserving existing form data

## 12. Verification

- [x] 12.1 Run targeted Maven tests for `service-lowcode`, `service-auth`, `platform-gateway`, and `service-workflow-engine`
- [x] 12.2 Run lowcode and workflow migration integration tests against PostgreSQL
- [x] 12.3 Run frontend lowcode/process unit tests and typecheck
- [x] 12.4 Run Playwright smoke for the migrated generic expense application
- [x] 12.5 Run `openspec validate lowcode-production-hardening --strict`
