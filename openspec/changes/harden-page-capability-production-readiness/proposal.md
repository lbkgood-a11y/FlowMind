## Why

The page-capability authorization flow now forms a safe management and runtime loop, but production readiness is still limited by default-tenant assumptions, per-role compatibility queries, incomplete organization ownership on lowcode records, and acceptance tests that can skip real persistence. These gaps must be closed before large multi-tenant deployments can rely on the new workbench as their only authorization management path.

## What Changes

- Remove `default` tenant assumptions from manifests, menu projection, role authorization data, and compatibility assessment; require the effective request or job tenant at every tenant-scoped boundary.
- Make compatibility assessment set-based and bounded so large tenants do not issue evidence and grant queries per role.
- Add database-backed production acceptance tests that exercise catalog activation, role publication evidence, tenant isolation, migration review, cutover gating, and runtime equivalence against PostgreSQL.
- Add verified organization ownership to lowcode form instances and enforce `OWN_ORG`, `OWN_ORG_AND_CHILDREN`, and `ASSIGNED_ORGS` for list and detail access without relying on frontend filtering.
- Keep fail-closed behavior for missing organization ownership and provide explicit diagnostic reasons rather than silently widening access.
- Add scale, tenant-isolation, organization-scope, and direct-bypass regression tests plus operational documentation.

## Capabilities

### New Capabilities

- `authorization-production-readiness`: Multi-tenant authorization boundaries, scalable compatibility assessment, mandatory persistence acceptance, and evidence-based cutover requirements.

### Modified Capabilities

- `enterprise-authorization-model`: Require tenant-scoped projections and set-based compatibility verification without default-tenant fallback.
- `lowcode-form-data-runtime`: Persist record organization ownership and enforce organization data scopes for list and detail operations.

## Impact

- `service-auth` tenant resolution, page manifest synchronization, role/menu projections, compatibility APIs, cutover services, migrations, and integration tests.
- `service-lowcode` form instance schema, submission metadata, query predicates, detail enforcement, DTOs, and authorization tests.
- Administrator authorization diagnostics and rollout documentation.
- PostgreSQL-backed CI acceptance environment and test profile.
