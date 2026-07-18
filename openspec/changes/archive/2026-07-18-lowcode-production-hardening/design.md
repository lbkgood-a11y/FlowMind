## Context

`service-lowcode` is currently a form metadata MVP. It stores form definitions, field definitions, and submitted form instances, exposes a published form snapshot endpoint for `service-workflow-engine`, and powers a hand-coded expense-report demo page. The workflow side is more mature: process packages are owned by `service-workflow-engine`, publish creates immutable flow/form/business-closure snapshots, and business object closure execution is driven by registered executors.

The production gap is therefore not the workflow engine itself. The gap is the rapid-development layer around it: lowcode metadata is not tenant-scoped, published forms are mutable in practice, server-side definition authorization is missing, lowcode Flyway is disabled, and adding a new business object still requires a hard-coded frontend page and bespoke glue.

This design keeps the current service boundary:

- `service-lowcode` owns reusable form definitions, form instance records, rapid-development application metadata, and generic application runtime APIs.
- `service-workflow-engine` owns process packages, process version publication, Temporal runtime, business closure plans, and side-effect executor dispatch.
- `service-auth` owns RBAC/menu/data-policy records, but does not create or mutate lowcode-owned tables.

## Goals / Non-Goals

**Goals:**

- Make lowcode form metadata production-safe through tenant isolation, immutable versioning, lifecycle governance, server-side validation, and service-owned migrations.
- Make form data runtime safe through schema validation, data-scope checks, instance detail protection, idempotent process binding, and auditability.
- Introduce a publishable rapid-development application model that can render generic list/detail/form/action pages from metadata.
- Replace the expense-report-only pattern with a reusable runtime path while preserving the existing expense acceptance scenario.
- Align docs and tests with the current ownership model.

**Non-Goals:**

- Do not move process package ownership back into `service-lowcode`.
- Do not build an arbitrary code, SQL, URL, connector, or Prompt execution platform.
- Do not build a full visual drag-and-drop page builder beyond the metadata and runtime needed for production MVP.
- Do not generalize every business executor. New business object side effects still require registered Java executors.
- Do not introduce an independent catalog service in this change.

## Decisions

### 1. Lowcode Owns Its Database Migrations

`service-lowcode` will enable Flyway and own all `lc_*` schema changes. Existing auth migrations that create or alter lowcode tables become compatibility-only migration debt and must not grow further.

Implementation direction:

- Add a lowcode migration table, preferably `flyway_schema_history_lowcode`, to avoid version collision in the shared Postgres database.
- Add lowcode-owned migrations for missing process binding columns, tenant fields, form versioning indexes, application metadata tables, and audit fields.
- Keep auth migrations responsible only for `sys_permission`, `sys_menu`, `sys_role_menu`, and data-policy seed records.

Alternative considered: keep auth as the fixture/migration orchestrator for sample data. This remains convenient locally but is unsafe for independent service deployment and rollback.

### 2. Forms Use Immutable Version Rows

Published form rows are immutable. A logical form is identified by `tenant_id + form_key`; each published version is a separate row with a monotonically increasing `version`.

Rules:

- At most one `DRAFT` per `tenant_id + form_key`.
- `PUBLISHED` and `OFFLINE` rows cannot be edited.
- Creating a new version derives a draft from the latest non-draft version.
- `form_definition_id + version + schema hash` are persisted into any runtime consumer snapshot.

Alternative considered: mutate one row and increment `version`. That keeps APIs simple but breaks published workflow snapshots and makes audit/rollback unreliable.

### 3. Server-Side Validation Is Mandatory

Frontend validation remains useful for responsiveness, but `service-lowcode` is the source of truth for form definition and submitted data validation.

Implementation direction:

- Reuse the same supported field/widget contract as workflow form publication: `string`, `number`, `integer`, `boolean`; widgets `string`, `textarea`, `number`, `money`, `integer`, `boolean`, `enum/select`, `date`.
- Validate JSON Schema and UI Schema on create/update/publish.
- Validate submitted data against the published form schema before inserting the instance.
- Return structured field errors for data validation failures.

Alternative considered: rely on workflow-engine validation when a form launches a process. This misses standalone form submissions and leaves invalid data in lowcode before workflow starts.

### 4. Permission And Tenant Context Move To Service Boundaries

All public lowcode endpoints require service-side authorization. Frontend permission checks remain UI affordances only.

Implementation direction:

- Add `@RequirePermission` to definition, application, instance detail, and process-binding APIs.
- Keep data-scope evaluation for form instance submit/query/detail/bind.
- Propagate `X-Tenant-Id` and roles from gateway/auth validation into downstream services.
- Store `tenant_id` on form definitions, form instances, and application metadata; every query filters by current tenant unless explicitly platform-global.

Alternative considered: keep tenant in data-policy only. That still permits metadata leakage and makes uniqueness constraints tenant-blind.

### 5. Applications Are Publishable Metadata Units

A rapid-development application is a governed metadata bundle, not a generated code artifact. It references existing published forms, business object catalog entries, list/detail layout metadata, actions, permissions, and optional workflow launch binding.

Suggested core tables:

- `lc_application`: logical app key, tenant, display name, lifecycle, latest version.
- `lc_application_version`: immutable published/draft metadata snapshot.
- `lc_application_page`: list/detail/create page definitions, column and field visibility, query defaults.
- `lc_application_action`: action code, display name, permission code, action type, form/process binding metadata.

Metadata fields may be stored as JSON, but only under explicit JSON Schema validation. No application metadata may contain arbitrary script, SQL, URL, executor class name, connector credentials, or free Prompt execution definitions.

Alternative considered: generate Vue files from app metadata. That creates fast demos but undermines rollback, permission review, and runtime safety.

### 6. Generic Runtime First, Designer Later

The first production MVP should provide generic runtime APIs and pages:

- List published applications available to the current user.
- Render list and detail pages from published metadata.
- Render create/edit forms from published form snapshots.
- Trigger approved actions such as submit form, start workflow, bind process, or open process detail.

The form builder UI can move from raw JSON to field-based editing incrementally, but the runtime contract should not depend on a complete drag-and-drop builder.

Alternative considered: build a rich form/page designer first. That improves UX but delays the production safety layer and keeps runtime behavior under-specified.

### 7. Workflow Integration Stays Snapshot-Based

When an app action starts a workflow, lowcode passes only validated form data, business binding metadata, process key/version assumptions, and an idempotency key. `service-workflow-engine` still decides process package publication, form snapshot validation, participant resolution, and closure execution.

For published lowcode forms referenced by workflow packages, `service-workflow-engine` continues using the internal published snapshot endpoint. The endpoint must return immutable version metadata and reject draft/offline references.

## Risks / Trade-offs

- [Migration overlap with existing auth migrations] → Add additive lowcode migrations that tolerate existing columns, then stop adding lowcode table changes to auth migrations.
- [Breaking existing expense demo data] → Preserve the existing `expense` form and `expense_report` flow as seed/backfill input, then publish a matching lowcode application runtime entry.
- [Tenant context not available in current token validation] → Extend auth validation and gateway propagation first; default missing tenant to `GLOBAL` only for legacy records and tests.
- [Metadata JSON becomes another unsafe DSL] → Validate every metadata blob against allowlisted schemas and forbid executable fields at API and publish time.
- [Generic runtime is less polished than bespoke pages] → Keep runtime dense and operational; allow app-specific custom pages later only as optional extensions.
- [Form version migration is complex] → Use additive columns and backfill version `1`; only after data is clean replace the old `uk_lc_form_definition_key` with tenant/version-aware indexes.

## Migration Plan

1. Enable lowcode Flyway with a separate history table and additive migrations.
2. Backfill `tenant_id = 'GLOBAL'`, `version = 1`, lifecycle status, audit fields, and process-binding columns for existing records.
3. Add new unique indexes for tenant-aware logical drafts and immutable published versions.
4. Add service-side permission annotations and data-scope checks before exposing new runtime APIs.
5. Add app metadata tables and seed an expense-report application equivalent to the current hard-coded page.
6. Update frontend to prefer generic application runtime navigation while keeping the existing expense route temporarily as a compatibility link.
7. Update docs and OpenSpec process-platform wording to reflect the final ownership model.
8. After verification, deprecate hard-coded expense runtime code and auth migrations that mutate lowcode tables.

Rollback strategy: all schema changes should be additive until the compatibility route is removed. If runtime rollout fails, disable the generic app menu and continue using the existing expense page while preserving migrated metadata.

## Open Questions

- Should tenant-specific lowcode definitions support fallback to `GLOBAL` templates in the first production MVP, or should fallback be limited to workflow business object catalog only?
- Should generic runtime support update/delete form instance actions immediately, or only create/query/detail/start-workflow for the first cut?
- Should lowcode application publication automatically create menu records, or produce a registration proposal that platform admins approve through service-auth?
