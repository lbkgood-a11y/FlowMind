## Context

`sys_auth_grant` is the function-authorization source and menu membership is a projection. Runtime validation now caches effective role and permission sets, but user-role changes do not invalidate that cache. Several effective-permission SQL queries omit tenant predicates, management APIs accept caller-supplied tenant identifiers, and the role workbench persists large selections as independent grant requests.

## Goals / Non-Goals

**Goals:**

- Enforce the authenticated tenant throughout runtime and management authorization paths.
- Make user-role changes visible immediately to token validation and route generation.
- Replace a role's complete ALLOW function-grant set atomically with optimistic concurrency control.
- Keep DENY grants and non-function policies intact.
- Give the frontend an authoritative persisted count and authorization version.

**Non-Goals:**

- Reintroducing legacy permission or writable role-menu tables.
- Changing semantic business action codes to URL permissions.
- Adding role inheritance or an external policy engine.

## Decisions

1. Runtime permission mapper methods take both tenant ID and user ID. Tenant identity comes from the persisted active user/security context, never an arbitrary request parameter.
2. Authorization management resolves the requested tenant against the authenticated context. Only platform administrators may explicitly target a different tenant.
3. User-role replacement explicitly evicts that user's permission cache. Grant mutations continue to invalidate all cached entries through the global GRANT version.
4. Redis cache reads and writes fail open to the database path, while authorization evaluation itself remains fail-closed.
5. A `PUT /api/v1/authz/roles/{roleId}/function-grants` endpoint accepts the complete desired ALLOW set and an optional expected grant version. The service validates the role, tenant, resources and actions before mutating, applies the diff in one transaction, preserves DENY grants, and bumps versions once.
6. The frontend uses the replacement endpoint and reloads the role profile after success. A version conflict prompts a reload instead of silently overwriting another administrator's changes.

## Risks / Trade-offs

- [Global grant version invalidates more users than necessary] → Keep correctness first; introduce per-subject versions only if scale measurements justify it.
- [Existing clients continue using single-grant endpoints] → Keep endpoints compatible while migrating the role workbench to atomic replacement.
- [Cross-tenant platform administration needs an exception] → Restrict it to the platform ADMIN authority and cover both allowed and denied cases in tests.
- [Transaction performs many row operations] → Validate first, batch database operations where practical, and bump versions once after the final state is durable.

## Migration Plan

1. Deploy backend tenant checks, cache invalidation and replacement API while retaining existing endpoints.
2. Deploy the frontend workbench using the replacement API.
3. Verify active sessions reflect role changes and grant counts match role profiles.
4. Roll back the frontend independently if needed; the compatible single-grant APIs remain available.

## Open Questions

- Whether high-scale deployments later require per-tenant or per-subject grant versions instead of one global grant version.
