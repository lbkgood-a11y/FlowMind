## Context

The current role page already contains role CRUD and parts of authorization editing, while data permission and enterprise authorization each maintain another role selector and independent layout. The workbench must consolidate navigation without coupling backend owners or loading all authorization datasets eagerly.

## Goals / Non-Goals

**Goals:**

- One role-centered master-detail shell with a searchable role tree and nine tabs.
- URL-restorable role/tab context and backward-compatible routes.
- Permission-aware tab visibility, lazy data loading, consistent adaptive tables and pagination.
- Reuse current APIs and progressively reuse existing tab components.

**Non-Goals:**

- Combining role, data-policy, and authorization backend APIs.
- Changing RBAC semantics, grant compilation, or service ownership.
- Loading every tab when the workbench opens.

## Decisions

### 1. Make `/system/role-workbench` the canonical route

The new route owns the menu entry “角色与授权”. Existing routes redirect with `tab=basic`, `tab=data`, or `tab=function`. This preserves bookmarks while avoiding multiple menu destinations.

### 2. Use a shell with independently owned tab components

The shell owns role list/search, selected role, active tab, permission visibility, and URL synchronization. Each tab owns its API calls, pagination, loading and mutations. This avoids extending the already large role page into a larger monolith.

### 3. Deliver nine stable tabs

The tabs are `basic`, `page`, `function`, `data`, `field`, `lowcode`, `guard`, `preview`, and `diagnostics`. Existing enterprise authorization components are reused where possible; data policy and role basic behavior are migrated without changing their APIs.

### 4. Keep role context explicit

Role selection updates `roleId` in the query string. Tab selection updates `tab`. A missing or inaccessible tab falls back to the first visible tab. A deleted or unavailable role clears the role context rather than silently selecting another role.

### 5. Standardize table layout

Each data-heavy tab uses the shared compact table frame, table-internal scrolling, explicit bottom pagination, and `max-content` horizontal scrolling. The workbench forms a complete flex height chain so pagination remains visible.

### 6. Keep configuration tabs inside one application page

Role and tab selections are workbench-local state. The shell mirrors them into the current browser URL with `history.replaceState` for refresh restoration, without invoking Vue Router navigation. This prevents the application tab manager from treating each internal configuration state as another page.

## Risks / Trade-offs

- [Existing role page contains tightly coupled drawer state] → initially reuse it for full CRUD through a basic-role embedded mode, then extract incrementally behind the workbench shell.
- [Nine tabs can overflow] → use scrollable tabs and preserve active tab in the URL.
- [Permission combinations can leave no tabs] → show an explicit unauthorized empty state.
- [Legacy redirects can loop] → redirect only legacy routes to the canonical route and never redirect back.

## Migration Plan

1. Add the workbench shell and canonical route without removing legacy components.
2. Wire nine tabs to existing components and APIs.
3. Redirect legacy routes to canonical tab deep links and expose only one menu entry.
4. Verify role CRUD, tab permissions, URL restore, pagination, and responsive layout.
5. Keep old component files during rollout for rollback; remove them only in a later cleanup.

## Open Questions

- None. The confirmed layout and tab taxonomy are sufficient for implementation.
