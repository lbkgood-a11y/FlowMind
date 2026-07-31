## 1. Persistence and domain model

- [x] 1.1 Add tenant-scoped page capability catalog, target mapping, dependency, role intent, release, compiled evidence, drift, and audit database migrations with constraints and indexes.
- [x] 1.2 Implement backend entities, repositories, enums, and DTOs for capability categories, readiness, draft lifecycle, releases, mappings, and compilation evidence.
- [x] 1.3 Add optimistic versioning and tenant-boundary checks for catalog and role intent writes.
- [x] 1.4 Add persistence tests for uniqueness, dependency references, immutable releases, active-release selection, and cross-tenant rejection.

## 2. Catalog registration and diagnostics

- [x] 2.1 Define a versioned page capability manifest contract for menus, business labels, categories, dependencies, target actions, scope support, field support, and guards.
- [x] 2.2 Implement idempotent manifest synchronization and catalog version activation without modifying active role projections.
- [x] 2.3 Validate dependency cycles, target existence and lifecycle, owner enforcement capabilities, and cross-tenant references.
- [x] 2.4 Expose implementation-facing catalog APIs that omit technical codes and administrator diagnostic APIs with full evidence.
- [x] 2.5 Register production manifests for user, tenant, audit-log, login-session, system-parameter, role, and menu management pages.
- [x] 2.6 Add contract tests proving registered page operations correspond to actual frontend controls and verified backend actions.

## 3. Role intent, validation, and compilation

- [x] 3.1 Implement draft creation, editing, automatic dependency resolution, dependent-removal validation, and business-summary generation.
- [x] 3.2 Implement page default data scope and explicit operation-scope overrides with support validation and fail-closed normalization.
- [x] 3.3 Implement field restriction and business-constraint intent linked to verified owner capabilities.
- [x] 3.4 Implement deterministic compilation from resolved page capability intent to resource/action grants and data, field, and guard projections with release evidence.
- [x] 3.5 Implement validation tokens bound to tenant, role, draft version, catalog version, actor authority, compiled diff, and expiry.
- [x] 3.6 Implement atomic publication that retains the prior active release on failure and blocks stale or incomplete validation.
- [x] 3.7 Implement immutable release rollback using stored compiled projections rather than current mappings.
- [x] 3.8 Add unit and integration tests for dependencies, compilation determinism, deny precedence, stale validation, atomic failure, rollback, and tenant isolation.

## 4. Drift, migration, and audit

- [x] 4.1 Implement mapping drift detection and impact analysis for affected roles and users without automatic recompilation.
- [x] 4.2 Implement legacy grant and policy analysis with exact, partial, ambiguous, and unmapped migration results.
- [x] 4.3 Generate review-required migration drafts and compare compiled decisions against current effective decisions with no-silent-expansion checks.
- [x] 4.4 Record draft, dependency, validation, publication, failure, rollback, impact, and migration audit events with business and technical evidence.
- [x] 4.5 Add migration, drift, impact, audit-redaction, and rollback integration tests.

## 5. Implementation-person role workbench

- [x] 5.1 Replace the resource/action-first role flow with menu-page selection and separate page access, read, and business-operation sections.
- [x] 5.2 Show automatic dependencies, locked required capabilities, and dependent-removal confirmation without technical terminology.
- [x] 5.3 Implement page default scope, optional operation overrides, field restrictions, and business constraints using natural-language controls and summaries.
- [x] 5.4 Implement draft status, blocking validation, compiled business diff, affected-user impact, publish confirmation, failure recovery, release history, and rollback.
- [x] 5.5 Implement actual-user and current-role simulation that explains access, read, operations, scopes, fields, guards, and final outcome in business language.
- [x] 5.6 Add an administrator-only mapping diagnostic workbench and keep resource/action codes out of the normal role workflow.
- [x] 5.7 Add accessibility, loading, empty, conflict, stale, failure, and large-tree states following the global TrioBase page layout standard.
- [x] 5.8 Add frontend component and interaction tests for non-technical role configuration, dependencies, scopes, publishing, validation, and rollback.

## 6. Runtime enforcement integration

- [x] 6.1 Link dynamic menus and route guards to the active release's compiled grants and fail closed on missing evidence.
- [x] 6.2 Link page and row operation availability to batch authorization decisions from the active release.
- [x] 6.3 Verify owner services enforce compiled actions, query scopes, field reads and writes, and business guards independently of frontend controls.
- [x] 6.4 Add bypass, stale-projection, unsupported-scope, field-enforcement, guard, and cross-tenant contract tests.

## 7. Production rollout and acceptance

- [x] 7.1 Add feature flags and tenant-level management-mode selection while preventing dual writes after cutover.
- [x] 7.2 Add compatibility dashboards for catalog readiness, migration coverage, decision equivalence, drift, publication failures, and rollback.
- [x] 7.3 Execute end-to-end scenarios for read-only, create-without-history, read-versus-operation scopes, approval guards, field masking, export denial, and direct API bypass.
- [x] 7.4 Document product manifest ownership, implementation-person workflow, platform diagnostics, migration, publication, rollback, and incident handling.
- [x] 7.5 Complete production acceptance with zero unintended permission expansion before enabling the new workbench by default.
