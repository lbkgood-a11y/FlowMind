## 1. Management Query Foundation

- [x] 1.1 Define allow-listed lifecycle asset types, stable summary/detail DTOs, pagination, filtering, and redaction rules
- [x] 1.2 Implement tenant-scoped catalog queries for design and implementation assets
- [x] 1.3 Implement tenant-scoped catalog queries for products, applications, subscriptions, approvals, and policies
- [x] 1.4 Implement lifecycle readiness summary and prerequisite blocker calculation
- [x] 1.5 Add permission-protected catalog/readiness controllers and unit/integration tests

## 2. Backend-Driven Menu Closure

- [x] 2.1 Add service-auth permissions required by lifecycle catalog endpoints
- [x] 2.2 Add V46 hierarchical OpenAPI menus for overview, design, implementation, exposure, and operations
- [x] 2.3 Grant administrator menus while retaining hidden button permissions and test backend menu output
- [x] 2.4 Apply V46 locally and verify Flyway history, visibility, component paths, and role grants

## 3. Frontend Lifecycle Console

- [x] 3.1 Add typed catalog, readiness, detail, and lifecycle-action API clients
- [x] 3.2 Add backend-compatible routes for every OpenAPI lifecycle submenu
- [x] 3.3 Implement lifecycle overview with dependency readiness and guided navigation
- [x] 3.4 Implement reusable paged asset management page with search, detail, create/edit payload, and permission-aware actions
- [x] 3.5 Configure structures, mappings, value maps, connectors, routes/releases, orchestrations, callbacks, products, applications, subscriptions/approvals, and policies
- [x] 3.6 Split execution center and callback quarantine into dedicated operational routes and preserve their functional behavior

## 4. Lifecycle Acceptance

- [x] 4.1 Add backend tests for allow-list rejection, tenant isolation, pagination, redaction, and readiness blockers
- [x] 4.2 Add frontend type checks and tests for route/menu coverage, permission-aware actions, and lifecycle navigation
- [x] 4.3 Execute a complete contract-to-application-to-subscription-to-policy-to-runtime readiness acceptance path
- [x] 4.4 Run service-openapi, gateway, service-auth migration, frontend, and OpenSpec validation
