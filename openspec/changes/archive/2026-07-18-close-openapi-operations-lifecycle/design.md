## Context

The OpenAPI domain services already own lifecycle state transitions and security checks. The gap is the management surface: backend mode ignores static frontend menus, V39-V45 expose only one workbench entry, most assets lack paged query endpoints, and the workbench renders placeholder cards instead of lifecycle controls.

## Goals / Non-Goals

**Goals:**

- Make every governed OpenAPI asset discoverable through tenant-scoped paged management APIs.
- Provide backend-driven navigation matching the dependency order of an API lifecycle.
- Provide functional pages for asset inspection and lifecycle actions while reusing existing typed mutation endpoints.
- Show readiness blockers between contract, mapping, connector, release, product, application, subscription, policy, and runtime stages.
- Preserve permission, tenant, audit, secret-redaction, and public-route-disabled constraints.

**Non-Goals:**

- Building an external developer portal.
- Replacing the existing domain services or weakening dual approval.
- Adding arbitrary mapping scripts or BPMN semantics.
- Enabling public runtime automatically.

## Decisions

### 1. Add a unified read-only asset catalog over existing mappers

`OpenApiLifecycleCatalogService` exposes a fixed allow-list of asset types and maps entities to a stable `LifecycleAssetItem`. This avoids twelve nearly identical controllers while preventing arbitrary table or class selection. MyBatis tenant interception remains authoritative.

### 2. Keep mutations on existing typed endpoints

The UI calls existing structure, mapping, connector, route, orchestration, callback, product, application, subscription, approval, and policy endpoints. A generic mutation endpoint is rejected because it would weaken DTO validation and permission clarity.

### 3. Use one reusable Vue lifecycle resource page

Routes provide an asset type and lifecycle metadata to a shared page. The page supplies paged search, JSON detail inspection, create/update payload dialogs, and configured lifecycle actions. Specialized execution and callback-quarantine pages remain optimized tables.

### 4. Publish a hierarchical backend menu

V46 creates catalog nodes for overview, design, implementation, exposure, and operations, plus visible leaf menus. Existing hidden permission nodes remain under the root and continue carrying API permission codes.

### 5. Compute lifecycle readiness without side effects

A summary endpoint counts assets and reports missing prerequisites. It never publishes, activates, approves, or enables gateway routes automatically.

## Risks / Trade-offs

- [Generic pages may be less ergonomic than bespoke editors] → Use schema-driven JSON forms now and retain specialized editors as incremental replacements.
- [Entity schemas differ] → Return a stable summary plus redacted JSON detail, never resolved credentials.
- [Menu migration can affect existing grants] → Preserve `OA_MGMT_100`, add deterministic IDs, and grant only existing administrator role by default.
- [Backend list endpoints expose cross-tenant data] → Require read permissions and rely on the existing tenant interceptor with explicit tests.

## Migration Plan

1. Deploy service-openapi query/readiness endpoints.
2. Apply service-auth V46 menu migration and grant administrator menus.
3. Deploy frontend routes/pages.
4. Re-login to refresh backend menus.
5. Run a complete lifecycle acceptance test with public runtime disabled.

Rollback hides V46 leaf menus while retaining permission nodes; query endpoints are additive and can remain deployed.

## Open Questions

None.
