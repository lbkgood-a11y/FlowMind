# Lowcode Production Hardening

## Service Ownership

`service-lowcode` owns all `lc_*` runtime and metadata tables, including form
definitions, form fields, form instances, process bindings, schema hashes,
tenant columns, lifecycle timestamps, and rapid-application metadata.

`service-auth` owns RBAC, menu, role-menu, permission, and data-policy seed data.
Auth migrations may register lowcode permissions and menu entries, but they must
not create or mutate `lc_*` tables.

## Migration Boundary

`service-lowcode` runs Flyway with the dedicated history table
`flyway_schema_history_lowcode`. This lets the service initialize and evolve its
schema without requiring `service-auth` migrations to run first.

Historical auth migrations currently contain compatibility changes for lowcode:

- `V35__expense_form_data_permission_closure.sql`
- `V36__bind_expense_form_to_workflow.sql`
- `V37__backfill_expense_process_business_binding.sql`

Those migrations are compatibility debt from the expense-report pilot. New
lowcode table changes must be added only under
`trio-base-services/service-lowcode/src/main/resources/db/migration`.

## Production Baseline

The lowcode baseline now treats form definitions as tenant-aware, versioned
metadata. Published forms keep immutable schema identity through `schema_hash`,
publication timestamps, and tenant/version indexes. Form instances carry the
form version and schema hash used at submission time, plus workflow process
binding and status metadata for idempotent process integration.

The first migration goal is additive compatibility: existing demo records are
backfilled to `tenant_id = GLOBAL`, missing versions default to `1`, and audit
fields receive stable system values. Destructive cleanup of historical auth
lowcode mutations should happen only after production data has been inspected
and a rollback plan exists.

## Generic Runtime Rollback Runbook

Rollback must preserve form definitions, form instances, workflow bindings, and
published process snapshots. Do not drop `lc_*` tables, delete
`lc_form_instance` rows, or rewrite published form/application version rows.

Use the least invasive rollback first:

1. Disable the generic app center menu or remove role-menu grants for the
   app-center entry while keeping API permissions unchanged for operators.
2. Point the expense menu back to the compatibility legacy component if users
   cannot use `/lowcode/apps/expense_report`. The compatibility route and
   legacy page can coexist during rollback.
3. If a published application metadata version is unsafe, take only that
   application version offline through lowcode lifecycle APIs. Existing form
   data and workflow process instances remain intact.
4. Keep `service-lowcode` online for form detail, process binding, and pending
   workflow retry diagnostics. Turning off the whole service should be a last
   resort because workflow launch recovery depends on it.
5. After traffic is stable, publish a corrected lowcode application version and
   restore the app-center/expense menu grants.

Verification after rollback:

- Existing expense form instances are still visible through authorized
  administrative or compatibility paths.
- Pending workflow launches can still be retried or inspected.
- Published workflow packages still show the original `formDefinitionVersion`
  and do not change when lowcode application metadata is taken offline.
- No auth migration is added to mutate `lc_*` tables as part of the rollback.
