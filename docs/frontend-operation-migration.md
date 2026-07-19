# TrioBase Frontend Operation Migration

## Operation Boundaries

Use Domain APIs for:
- Query, pagination, sorting, detail reads, and option loading.
- Draft saves that do not change lifecycle status.
- Management CRUD on platform configuration pages when the operation is not a business document lifecycle action.

Use Global Action for:
- Submit, approve, reject, cancel, close, confirm, retry, execute, workflow start, workflow signal, and external state-changing invocation.
- Any operation where backend action availability, owner guard, audit, timeline, idempotency, or workflow correlation is required.

Use shared page primitives for:
- Query bar, toolbar, table frame, split/master-detail/multi-table layout.
- Document header, status, action bar, editable grid, footer summary, and timeline panel.

## Migrated Pilot Pages

- `process/instance/list.vue`: lifecycle start and closure effects stay behind Action Client; post-action refresh uses backend refresh scopes.
- `system/role/list.vue`: complex management layout now enters through `BusinessPageScaffold` while preserving existing management APIs.
- `openapi/workbench/index.vue`: multi-table workbench uses shared layout/table primitives; orchestration start refreshes regions from Action result scopes.

## Second-Wave Migration Scope

Second-wave migrated pages are recorded in `apps/web-antd/src/shared/page/migrated-page-registry.ts`. The registry is the source of truth for source-level governance tests and should be updated whenever a page joins or leaves the governed set.

System management:
- `system/user/list.vue`: single-table management page; use shared scaffold, query bar, toolbar, table frame, and drawer/detail conventions.
- `system/org/list.vue` and `system/dept/list.vue`: master-detail management pages; keep organization APIs lightweight.
- `system/menu/list.vue`: workbench-style management page; keep menu workbench internals but enter through the standard page shell.
- `system/role/list.vue`: migrated pilot master-detail page.
- `system/dictionary/list.vue`, `system/config/list.vue`, `system/session/list.vue`, `system/audit-log/list.vue`, `system/authz/index.vue`: compact management/query pages.

Process:
- `process/task/list.vue`: task lifecycle operations must remain Action-routed through the task dialog and shared action surfaces.
- `process/instance/list.vue`: migrated pilot with backend candidate availability for closure effects.
- `process/package/list.vue`: compact management page.
- `process/designer/index.vue`: standard page shell only; designer canvas internals remain domain-specific.

Lowcode:
- `lowcode/form/list.vue`: compact management page.
- `lowcode/runtime/center.vue`: compact runtime entry list.
- `lowcode/runtime/app.vue`: document/runtime surface; lifecycle transitions must use Action Client.
- `lowcode/runtime/expense-compat.vue`: compatibility bridge, not a normal operation page.

OpenAPI:
- `openapi/workbench/index.vue`: migrated pilot multi-table workbench.
- `openapi/lifecycle/overview.vue`: multi-region lifecycle overview.
- `openapi/lifecycle/resource.vue`: resource detail/configuration surface.
- `openapi/operations/executions.vue` and `openapi/operations/quarantine.vue`: delegate to the migrated workbench entry.

Operations:
- `operations/announcement/list.vue`, `operations/file/list.vue`, `operations/import-export/list.vue`, `operations/job/list.vue`, and `operations/message/list.vue`: compact management/query pages.

## Second-Wave Exceptions

Excluded from this wave:
- `_core/**`: Vben shell authentication, profile, and fallback pages.
- `dashboard/**`: analytic surfaces; standardization should use a dashboard-specific pattern later.
- `demos/**`: non-production examples.
- Nested OpenAPI lifecycle editor components: inherit the operation shell from `lifecycle/resource.vue`.

## Acceptance Criteria

- Governed pages import shared TrioBase operation primitives unless they have a documented exception in the registry.
- Lifecycle actions in migrated process, lowcode, and OpenAPI pages use the Action Client or an Action-backed owner wrapper.
- Query, pagination, sorting, option loading, management CRUD, and draft edits remain on owner/domain APIs.
- Pages keep compact ERP spacing and avoid adding new page-local layout shells when shared primitives already cover the pattern.

## Migration Waves

1. System pages: keep management APIs, migrate layout/query/toolbar/table/drawer patterns.
2. Process pages: move task and closure lifecycle operations to candidate availability and shared Action UI.
3. Lowcode pages: keep runtime query/draft APIs, route submit/retry/workflow transitions through Global Action.
4. OpenAPI pages: separate management CRUD from state-changing runtime actions; migrate workbench regions first.
5. Operations pages: apply standard table/query/drawer density and timeline links for import/export/file/job events.
6. Future MDM/SCM/WMS/CRM: start every document page from Business Object Catalog metadata, batch Action availability, document primitives, and timeline read model.

## Page Density Defaults

- Content gutter: 8px.
- Region gap: 6px.
- Control height: 30px.
- Table row height: 34px.
- Radius: 4px.
- Query columns: 3 desktop, 1 mobile.
- Drawer width: 760px default for dense editors.
- Sticky action area: 42px.
