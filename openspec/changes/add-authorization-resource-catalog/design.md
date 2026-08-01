## Context

The authorization registry already stores resource, action, field, guard, lifecycle, enforcement, Owner, and synchronization metadata and exposes paginated read APIs. The frontend currently consumes subsets of that registry inside role-scoped authorization tabs, while the existing “能力目录” represents page capabilities rather than business authorization resources. The new page must preserve Owner authority and reuse existing APIs.

## Goals / Non-Goals

**Goals:**

- Provide one searchable, filterable, paginated catalog of all authorization resources.
- Expose complete child metadata and synchronization health in a detail drawer.
- Match user-management adaptive table height, width overflow, refresh icon, and fixed pagination behavior.
- Add backend-driven menu and page-capability declarations so access remains permission governed.

**Non-Goals:**

- Manually creating or editing Owner-owned resource definitions in the administration UI.
- Changing authorization decision semantics or resource synchronization contracts.
- Combining page capabilities with business resources into one storage model.

## Decisions

### 1. Use the existing paginated registry endpoint as the list source

The page uses `GET /api/v1/authz/resources` for server-side pagination and filters. This avoids loading the full resource tree and keeps the catalog usable as the registry grows. The tree endpoint remains useful to existing role authorization editors.

### 2. Resolve complete detail from existing registry projections

The selected resource is enriched from the resource tree/configuration projection, which already contains actions, fields, and guards. No new cross-owner database reads or duplicated resource tables are introduced.

### 3. Keep the catalog read-only

Registration continues through Owner manifests, lowcode publication, migrations, or the governed internal synchronization API. The UI offers refresh and diagnostics only; this prevents administrators from declaring a resource that the Owner runtime cannot enforce.

### 4. Reuse established adaptive table composition

The view forms a full flex height chain, uses table-internal vertical and horizontal scrolling, and keeps explicit server pagination in a fixed footer. Filters collapse naturally on narrow screens and the detail drawer remains independently scrollable.

### 5. Publish navigation from both frontend and backend declarations

A frontend route enables direct development navigation, while a Flyway menu projection and page-capability manifest make the entry available in the backend-driven production menu. Access maps to existing authorization registry GET permission and a dedicated page access resource.

### 6. Separate page intent from runtime registration in navigation

The user-facing pages are named “页面能力目录” and “资源注册中心” and placed below a “权限治理” catalog node. Runtime target tags link to the registration center through a resource-code query, while resource details derive reverse references from the active page-capability diagnostics projection. This reuses governed projections instead of introducing another relationship table.

## Risks / Trade-offs

- [Tree projection can be larger than one page] → Load it only when opening detail and cache it for the current refresh cycle.
- [Resource changes between list and detail requests] → Display the latest projection and expose the last synchronization timestamp.
- [Backend-driven menu may be cached in an existing session] → Increment authorization/menu versions and require route refresh after deployment.
- [Legacy resources may lack fields or guards] → Show explicit empty states rather than treating missing optional children as an error.

## Migration Plan

1. Add the frontend route, page, and API typings.
2. Add an idempotent Flyway migration for menu/page access metadata.
3. Extend the system page-capability manifest and activate its next version.
4. Build and restart `service-auth`, then verify the route and catalog APIs.
5. Roll back by hiding the menu entry; existing registry data and APIs remain unchanged.

## Open Questions

- None.
