## Context

Page-capability authorization is already the management source and compiles immutable evidence into runtime grants. The remaining production risks are cross-cutting: several call sites still fall back to tenant `default`, the compatibility dashboard performs evidence and grant queries inside a role loop, PostgreSQL acceptance can be skipped when Docker is unavailable, and lowcode instances do not persist organization ownership even though organization scopes are exposed.

The design must preserve fail-closed behavior, the owner-service boundary, immutable releases, and the existing GUI workflow for non-technical implementation personnel.

## Goals / Non-Goals

**Goals:**

- Make tenant identity explicit and authoritative across interactive requests, manifests, jobs, projections, simulations, and cutover assessment.
- Bound compatibility assessment to a fixed number of database queries per tenant.
- Execute production acceptance against PostgreSQL as a required CI profile rather than silently treating a skipped test as acceptance.
- Persist authoritative organization ownership on lowcode instances and enforce resolved organization scopes in list and detail paths.
- Preserve zero-unintended-expansion and no-dual-write cutover guarantees.

**Non-Goals:**

- Replacing resource/action runtime grants or the existing page-capability compiler.
- Moving organization ownership tables between services in this change.
- Automatically inferring organization ownership for old records from untrusted form payload fields.
- Adding a general-purpose ABAC expression engine.

## Decisions

### 1. Tenant-neutral manifests with explicit tenant materialization

System manifests will be templates without a production `default` tenant. Startup synchronization will enumerate configured active tenants or accept an explicit tenant from a tenant-scoped job and materialize the same stable manifest for each tenant. Interactive authorization services will reject a missing tenant unless the caller has an authenticated platform-global context.

This is preferred over retaining `default` because a fallback converts missing context into valid access. It is also preferred over duplicating manifest beans per tenant because tenants are dynamic.

### 2. Set-based compatibility assessment

The dashboard will load enabled roles, active releases, compiled grant evidence, runtime grants, unresolved reviews, drift, and audit counters using tenant-scoped set queries. It will group and compare them in memory. Query count will remain constant with role count; role-status rows can be paged in the API after the aggregate is computed.

This is preferred over caching the current N+1 implementation because cached unsafe or stale compatibility evidence is unsuitable for a cutover gate.

### 3. PostgreSQL acceptance is an explicit build profile

A `production-acceptance` Maven profile will require a PostgreSQL connection and fail when it is absent. Testcontainers remains optional for developer integration tests, but the production acceptance job uses the same Flyway migrations and verifies real constraints, tenant isolation, publication evidence, compatibility comparison, and mode cutover.

This separates “developer environment cannot run Docker” from “production acceptance passed.”

### 4. Lowcode records own organization attribution

`lc_form_instance` will add `owner_org_id` and an ownership provenance field. New submissions use the authenticated subject organization returned by the governed authorization decision; the client cannot supply or override this value. Existing rows remain `UNRESOLVED` until an idempotent reconciliation job resolves the submitter's primary organization through the organization owner API.

Organization-scoped list queries add a tenant-and-`owner_org_id` predicate using resolved decision organization IDs. Detail access performs the same membership check. Empty or unresolved scope evidence returns no data.

### 5. Cutover remains server-enforced and irreversible through normal APIs

The compatibility service remains the only source for cutover readiness. The management-mode API repeats the assessment and refuses downgrade after cutover. Emergency recovery uses immutable release rollback, not reopening legacy writes.

## Risks / Trade-offs

- [Risk] Existing lowcode rows have no trustworthy organization owner. → Mark them `UNRESOLVED`, exclude them from organization scopes, and provide an idempotent reconciliation job with counts and failures.
- [Risk] Tenant enumeration can create a large startup burst. → Synchronize manifests in bounded batches and make activation idempotent; do not activate or recompile role releases implicitly.
- [Risk] Set-based compatibility loads large grant sets. → Select only comparison columns, cap role-detail output, and keep aggregate counters independent from UI pagination.
- [Risk] Removing tenant fallback exposes previously hidden missing-context callers. → Return stable diagnostics, update tests and internal callers, and retain explicit platform-global job APIs.
- [Risk] Required PostgreSQL acceptance slows CI. → Isolate it in the release gate profile while retaining fast unit suites for pull requests.

## Migration Plan

1. Deploy additive authorization and lowcode migrations and indexes.
2. Deploy explicit tenant resolution and set-based dashboard code while tenants remain in `MIGRATION`.
3. Start lowcode ownership reconciliation and monitor unresolved counts; organization scopes stay fail-closed for unresolved rows.
4. Run the required PostgreSQL production-acceptance profile for at least two tenants.
5. Re-run the administrator上线验收 dashboard and cut over only when all blockers are zero.
6. Roll back application code if necessary; keep additive columns. Restore permissions through immutable authorization release rollback, never by legacy dual writes.

## Open Questions

- None. Primary organization ordering follows the organization owner service's existing `is_primary` and creation-order contract.
