## 1. Authoritative Tenant Boundaries

- [x] 1.1 Remove production `default` tenant fallback from authorization decisions and simulations and return a stable fail-closed reason when tenant context is missing
- [x] 1.2 Make page-capability manifest synchronization tenant-neutral and require explicit tenant materialization
- [x] 1.3 Make role authorization projections and compatibility assessment use the explicit effective tenant
- [x] 1.4 Add missing-tenant and cross-tenant regression tests

## 2. Scalable Compatibility and Cutover Evidence

- [x] 2.1 Replace per-role evidence and grant reads with set-based tenant queries and in-memory grouping
- [x] 2.2 Expose bounded role details and compatibility query statistics for operational diagnostics
- [x] 2.3 Add multi-role constant-query-count and tenant-isolation tests

## 3. Lowcode Organization Ownership

- [x] 3.1 Add verified organization ownership and provenance columns, indexes, entity fields, and response metadata for form instances
- [x] 3.2 Capture the authenticated subject primary organization on new submissions and ignore client ownership input
- [x] 3.3 Compile organization data scopes into matching list and detail predicates with unresolved ownership failing closed
- [x] 3.4 Add an idempotent ownership reconciliation service and diagnostic counts
- [x] 3.5 Add organization list, detail, unresolved-record, and client-override regression tests

## 4. Production Persistence Acceptance

- [x] 4.1 Add a `production-acceptance` Maven profile that requires PostgreSQL and fails instead of skipping when unavailable
- [x] 4.2 Extend database-backed acceptance for migrations, immutable release evidence, tenant isolation, exact runtime equivalence, and server-side cutover gating
- [x] 4.3 Document the release-gate command, required environment, diagnostics, and reconciliation runbook

## 5. Verification

- [x] 5.1 Run focused service-auth and service-lowcode unit and integration suites
- [x] 5.2 Run OpenSpec validation and record all tasks complete only after production-hardening acceptance passes
- [x] 5.3 Materialize and activate the tenant-neutral system catalog on first authenticated tenant use when startup prewarming is not configured
