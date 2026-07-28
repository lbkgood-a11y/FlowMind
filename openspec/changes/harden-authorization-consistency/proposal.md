## Why

Authorization grants are now the single source of function access, but role membership changes can leave cached permissions stale, runtime permission queries do not consistently enforce tenant boundaries, and large role edits are saved through many independent requests. These gaps can produce delayed permission changes, partial grants, or cross-tenant exposure, so they must be closed before the authorization center is production-ready.

## What Changes

- Make the authenticated tenant authoritative for authorization management and runtime permission resolution.
- Invalidate effective-permission caches immediately when user-role membership changes and make cache failures fall back safely to the database.
- Add a transactional role function-grant replacement API with optimistic version checking and one authorization-version bump per logical change.
- Return persisted grant counts and versions so the frontend can verify the saved state.
- Update the role authorization UI to save a complete permission set atomically instead of issuing one request per grant.
- Add regression tests for tenant isolation, deny precedence, role reassignment, cache invalidation, concurrent updates, and rollback on invalid grants.

## Capabilities

### New Capabilities

- `authorization-consistency`: Atomic grant replacement, cache coherence, and version-aware authorization refresh behavior.

### Modified Capabilities

- `enterprise-authorization-model`: Strengthen tenant-authoritative runtime and management behavior and require immediate effective-permission invalidation after role membership changes.

## Impact

- `service-auth` authorization controllers, registry service, user-role service, runtime permission mapper, cache and version services.
- Role authorization API contract and the `web-antd` role authorization workbench.
- Authorization unit/integration tests; no separate legacy permission store is reintroduced.
