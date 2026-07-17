## Why

The OpenAPI integration backend implements governed lifecycle operations, but the internal UI exposes only execution search and callback quarantine while the remaining lifecycle assets are static cards. Operators therefore cannot complete an API lifecycle from contract design through application onboarding, subscription, policy publication, release, monitoring, and rollback using the product interface.

## What Changes

- Add tenant-scoped paged management queries for structures, mappings, value maps, connectors, routes/releases, orchestrations, callbacks, API products, applications/clients, subscriptions/approvals, and traffic/policy snapshots.
- Add a backend-driven OpenAPI menu hierarchy organized as lifecycle overview, API design, API implementation, API exposure, and API operations.
- Replace placeholder asset cards with functional management pages supporting create, inspect, lifecycle transitions, publish, activate, suspend/revoke, approve, upgrade, rollback, and diagnostic operations according to permissions.
- Add a lifecycle overview that shows asset readiness and guides an operator through the dependency order required to publish and expose an API.
- Keep public runtime disabled by default and surface gateway/policy readiness before an API can be considered externally available.
- Add API, permission, migration, frontend, and end-to-end tests for the complete operator journey.

## Capabilities

### New Capabilities

- `openapi-operations-console`: Backend management queries, lifecycle menu/navigation, functional internal management pages, dependency readiness, and the complete operator workflow for governing and exposing an API.

### Modified Capabilities

None. Existing OpenAPI domain lifecycle rules remain unchanged; this change closes their management and operations surface.

## Impact

- `service-openapi` gains paged/query endpoints and lifecycle summary DTOs/services.
- `service-auth` gains a new Flyway migration for visible lifecycle menus and role grants.
- `trio-base-frontend/apps/web-antd` gains OpenAPI lifecycle routes, API clients, stores/components, and management pages.
- Existing OpenAPI permissions are reused and extended only where list/detail operations need distinct enforcement.
- Tests cover tenant isolation, pagination, permission visibility, lifecycle action wiring, route release activation/rollback, application onboarding, subscription approval, policy publication, and execution observability.
