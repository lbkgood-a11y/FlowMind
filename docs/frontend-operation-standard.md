# TrioBase Frontend Operation Standard Baseline

This document records the baseline decisions for `standardize-frontend-operation-and-business-action-contract`.

## Page Pattern Inventory

### Action-heavy workflow/runtime pages

These pages already contain lifecycle operations and must migrate first to backend-provided action availability and shared Action UI primitives.

| Page | Pattern | Current signal |
| --- | --- | --- |
| `process/instance/list.vue` | Workflow list + start/detail drawers | Uses `useActionDispatch`; also owns form validation, messages, drawers, table, and refresh logic |
| `process/task/list.vue` | Task list | Uses task operation flows through `TaskActionDialog` |
| `process/components/TaskActionDialog.vue` | Task action modal | Uses `useActionDispatch` for approve/reject/transfer/add-sign |
| `lowcode/runtime/app.vue` | Runtime document/list page | Uses `useActionDispatch`; mixes create/retry actions, form validation, table, drawers, and workflow feedback |
| `openapi/workbench/index.vue` | Multi-table workbench | Uses `useActionDispatch` for orchestration; mixes start modal, execution table, quarantine table, and resolution operation |

### Complex management pages

These pages are not all Global Action candidates, but they have the most duplicated query/table/drawer/confirm/message logic and should migrate to standard management operation patterns.

| Page | Pattern | Current signal |
| --- | --- | --- |
| `system/role/list.vue` | Complex single page with authorization sub-panels | 2500+ lines; table, authorization config, previews, drawers, confirms |
| `system/user/list.vue` | User management | 1300+ lines; table, drawers, status/delete confirm, organization fields |
| `system/org/list.vue` | Master-detail organization management | 1200+ lines; organization table/tree, user table, assignment drawer |
| `system/menu/list.vue` | Menu/permission management | 800+ lines; tree/table, drag/sort, permission preview, drawer |
| `system/data-permission/list.vue` | Policy management | 800+ lines; authorization resource forms and drawer |

### Master-detail or multi-table pages

These pages need standard parent/child selection and region refresh semantics.

| Page | Pattern |
| --- | --- |
| `system/dictionary/list.vue` | Dictionary type + dictionary item |
| `system/session/list.vue` | Active sessions + login logs |
| `system/org/list.vue` | Organization dimension + users |
| `operations/job/list.vue` | Job definitions + execution logs |
| `operations/message/list.vue` | Admin messages + inbox |
| `data/hybrid-query/index.vue` | Datasets/documents/query result |
| `openapi/workbench/index.vue` | Executions + quarantine callbacks |

### Standard single-table/config pages

These can migrate after the pilot primitives stabilize.

`operations/announcement/list.vue`, `operations/file/list.vue`, `operations/import-export/list.vue`, `system/config/list.vue`, `system/audit-log/list.vue`, `lowcode/form/list.vue`, `process/package/list.vue`, `openapi/lifecycle/resource.vue`, and similar pages.

### Low-priority or legacy pages

`demos/*`, dashboard analytics widgets, core auth/profile/fallback pages, `lowcode/runtime/expense-compat.vue`, and placeholder pages should not block the first standardization wave.

## Operation Classification

| Category | Examples | Backend path |
| --- | --- | --- |
| Local UI Operation | Open drawer, close modal, switch tab, select rows, edit unsaved input | Local Vue state |
| Query Operation | Filter, paginate, sort, refresh list, load detail | Owner domain query API |
| Management Operation | Create/update/delete users, roles, menus, dictionaries, jobs, config | Owner management API with standard management operation wrapper |
| Business Action | Submit, approve, reject, transfer, add-sign, cancel, close, retry, start workflow, execute orchestration | Global Action through Action Client |

## Current Action Usage

Already using `useActionDispatch`:

- `views/process/instance/list.vue`
- `views/process/components/TaskActionDialog.vue`
- `views/lowcode/runtime/app.vue`
- `views/openapi/workbench/index.vue`

Known lifecycle or state-changing operations that still need classification before migration:

- Workflow package publish/offline/version actions in `process/package/list.vue`
- Lowcode form publish/offline/derive actions in `lowcode/form/list.vue`
- OpenAPI lifecycle asset actions in `openapi/lifecycle/resource.vue`
- Operations job trigger/enable/disable and import-export cancel operations
- System role/user/menu enable/disable/delete operations as management operations unless promoted to business document actions

## First Migration Wave

Default pilot set:

1. `process/instance/list.vue` as the Action-heavy workflow page.
2. `system/role/list.vue` as the complex management page.
3. `openapi/workbench/index.vue` as the multi-table workbench page.

The first wave is considered successful when these pages use shared page/action primitives where practical, rely on backend action availability for lifecycle actions, and have tests guarding lifecycle-action bypass.
