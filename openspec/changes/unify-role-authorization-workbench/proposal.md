## Why

Role management, data permission, and enterprise authorization all use role as their primary context but currently require administrators to switch pages and repeatedly select the same role. A unified workbench reduces navigation and makes the complete authorization state of one role understandable and operable in one place.

## What Changes

- Replace the three separate menu entries with one “角色与授权” workbench using a persistent role tree on the left and nine authorization tabs on the right.
- Move complete role CRUD, page/function grants, data policies, field policies, lowcode bundles, guards, decision preview, and diagnostics into independently loaded workbench tabs.
- Preserve owner API and permission boundaries; tabs are visible and writable only when the current administrator has the corresponding permissions.
- Preserve `/system/role`, `/system/data-permission`, `/system/authz`, and `/system/authorization` as compatible routes that redirect into the appropriate workbench tab.
- Persist `roleId` and `tab` in the URL so refresh, bookmarks, and redirects restore the same context.
- Standardize every tab on adaptive table sizing, internal scrolling, and a visible bottom pagination component.

## Capabilities

### New Capabilities

- `role-authorization-workbench`: Unified role-centered navigation, tab composition, route compatibility, permission-aware loading, and responsive table behavior.

### Modified Capabilities

- None. Existing role and authorization service contracts remain unchanged.

## Impact

- `trio-base-frontend/apps/web-antd/src/views/system`: new workbench shell and extracted role/authorization tab components.
- `trio-base-frontend/apps/web-antd/src/router/routes/modules/system.ts`: one primary menu entry plus compatibility redirects.
- Existing role, data-policy, and authorization APIs are reused without backend ownership or database changes.
