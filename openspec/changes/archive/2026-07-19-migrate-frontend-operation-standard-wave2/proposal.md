## Why

The first operation-standard change created the shared Action/page/document primitives and migrated three pilot pages, but most existing pages still use local layout, toolbar, table, drawer, and action patterns. TrioBase needs the second wave now so users experience consistent, compact operation flows across daily system, process, lowcode, OpenAPI, and operations pages before new MDM, SCM, WMS, and CRM modules are added.

## What Changes

- Expand the frontend operation standard from pilot pages to a governed second migration wave.
- Migrate high-traffic existing pages to `BusinessPageScaffold`, compact query/toolbar/table primitives, and consistent drawer/detail/footer behavior.
- Apply shared Action UI and `refreshScopes` handling to lifecycle actions that already have Global Action definitions.
- Keep management CRUD, query, pagination, sort, and draft-edit operations on lightweight domain APIs while standardizing their layout and feedback.
- Add page-family migration coverage for system management, process runtime, lowcode runtime/form pages, OpenAPI operations, and operations management pages.
- Add frontend governance tests that treat migrated pages as protected: lifecycle actions must not bypass the Action Client and migrated pages should use the standard page primitives.
- Update migration documentation with the second-wave page inventory, status, and remaining backlog.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `frontend-operation-standard`: Extend the existing page migration requirement from pilot migration to second-wave page-family migration, including layout, action, refresh, and governance expectations for more existing pages.
- `global-action-runtime`: Clarify that newly migrated lifecycle operations in process, lowcode, and OpenAPI pages must continue dispatching through the Action Client while management/query/draft operations remain lightweight.

## Impact

- Frontend pages under `trio-base-frontend/apps/web-antd/src/views/system`, `views/process`, `views/lowcode`, `views/openapi`, and `views/operations`.
- Shared frontend primitives under `trio-base-frontend/apps/web-antd/src/shared`.
- Frontend Action client/composables and guard tests.
- Documentation in `docs/frontend-operation-migration.md`.
- No Vben shell, router architecture, menu architecture, request client, theme base, or backend Action runtime contract changes are intended in this phase.
