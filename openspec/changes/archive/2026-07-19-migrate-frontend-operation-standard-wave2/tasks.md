## 1. Inventory and Shared Support

- [x] 1.1 Create a second-wave migration inventory for system, process, lowcode, OpenAPI, and operations pages.
- [x] 1.2 Identify migrated-page exceptions and pages excluded from this wave, such as core auth, fallback, dashboard demo, and low-priority demos.
- [x] 1.3 Add or adjust shared page/action helpers only when at least two second-wave pages need the same behavior.
- [x] 1.4 Update frontend operation migration documentation with second-wave scope and acceptance criteria.

## 2. System Management Pages

- [x] 2.1 Migrate user management to shared page/query/toolbar/table and standard drawer/detail patterns.
- [x] 2.2 Migrate organization and department management pages to shared compact management patterns.
- [x] 2.3 Migrate menu management to shared page shell while preserving its existing workbench-specific logic.
- [x] 2.4 Migrate dictionary, config, session, audit log, and authz pages to shared compact management patterns.
- [x] 2.5 Keep management CRUD operations on domain APIs and document any intentional non-Action exceptions.

## 3. Process Pages

- [x] 3.1 Migrate process task list and task operation surfaces to shared page/action primitives.
- [x] 3.2 Migrate process package list to shared compact management patterns.
- [x] 3.3 Apply standard drawer/detail/loading/feedback behavior to process designer shell where feasible without changing designer canvas internals.
- [x] 3.4 Ensure process lifecycle actions continue through Action Client and refresh returned scopes.

## 4. Lowcode Pages

- [x] 4.1 Migrate lowcode form list to shared compact management patterns.
- [x] 4.2 Migrate lowcode runtime center and runtime app surfaces to shared page/document/action primitives where feasible.
- [x] 4.3 Remove or document remaining compatibility-only lowcode surfaces that are intentionally excluded from normal navigation.
- [x] 4.4 Ensure lowcode lifecycle transitions use Action Client while query and draft-edit operations remain domain APIs.

## 5. OpenAPI and Operations Pages

- [x] 5.1 Migrate OpenAPI lifecycle overview and resource pages to shared compact workbench/detail patterns.
- [x] 5.2 Migrate OpenAPI operations execution and quarantine pages to shared table/toolbar/action patterns.
- [x] 5.3 Migrate operations pages for announcement, file, import-export, job, and message to shared compact management patterns.
- [x] 5.4 Keep OpenAPI management CRUD operations lightweight and route runtime state-changing operations through Action Client or an Action-backed owner wrapper.

## 6. Governance and Verification

- [x] 6.1 Extend frontend guard tests with the second-wave migrated page list and direct lifecycle-bypass checks.
- [x] 6.2 Add a migrated-page standard test that verifies governed pages import shared TrioBase operation primitives or have a documented exception.
- [x] 6.3 Run frontend typecheck and targeted unit/source tests for migrated pages and shared helpers.
- [x] 6.4 Run `openspec validate migrate-frontend-operation-standard-wave2 --strict`.
