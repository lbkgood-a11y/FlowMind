## 1. Backend menu-page association

- [x] 1.1 Add a nullable indexed `page_code` column to `sys_menu` and backfill known system-management menus from their stable menu keys
- [x] 1.2 Extend the menu entity, create/update requests, API response mapping, and persistence service to round-trip `pageCode`
- [x] 1.3 Add backend tests covering create, update, read, and legacy menus without a page binding

## 2. Backend page capability query

- [x] 2.1 Add exact optional `pageCode` filtering to the Page Capability Catalog endpoint and service
- [x] 2.2 Add tests proving tenant/catalog scoping, page filtering, ordering, and empty results without menu-button inference

## 3. Menu management workbench

- [x] 3.1 Extend frontend menu API models and form state with `pageCode`, and load the backend Page Capability Catalog with distinct loading and error states
- [x] 3.2 Replace manual permission-node creation/editing in the primary menu workflow with a backend-driven page selector and read-only page-function list grouped by category
- [x] 3.3 Display readiness, dependency, unmapped-page, and legacy permission-node compatibility information without exposing manual permission-code entry to implementers
- [x] 3.4 Add frontend model/component tests for page grouping, capability filtering, readiness presentation, and catalog load failure

## 4. Verification and documentation

- [x] 4.1 Update menu/authorization documentation to describe backend-owned page functions and the implementation-personnel workflow
- [x] 4.2 Run focused backend and frontend tests, validate the OpenSpec change, and resolve regressions

## 5. Catalog bootstrap and inspection

- [x] 5.1 Bootstrap the default tenant catalog at startup and lazily materialize a missing authenticated-tenant catalog for catalog-dependent APIs
- [x] 5.2 Expose tenant-scoped catalog lifecycle summaries and add backend bootstrap/query tests
- [x] 5.3 Add a dedicated Page Capability Catalog menu, route, read-only workbench, and frontend API models
- [x] 5.4 Run focused backend/frontend verification and validate the extended OpenSpec change
- [x] 5.5 Align system Manifest menu keys with persisted navigation keys, add a forward repair migration, and verify catalog activation readiness
