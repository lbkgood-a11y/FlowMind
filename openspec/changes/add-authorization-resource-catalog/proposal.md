## Why

Authorization resources are currently visible only indirectly inside role configuration tabs, so administrators cannot inspect the complete registration state, ownership, actions, fields, guards, lifecycle, or synchronization freshness in one place. A dedicated catalog is required to make the governed resource registry observable and operable at production scale.

## What Changes

- Add a standalone “资源注册中心” system-management page backed by the existing paginated authorization resource API.
- Provide keyword, Owner service, resource type, and lifecycle filters with adaptive table sizing and persistent bottom pagination.
- Provide a resource detail drawer containing actions, fields, guards, enforcement state, metadata, and synchronization information.
- Expose stale-registration status and a refresh action without allowing administrators to forge Owner-owned resource definitions.
- Publish the page through frontend routing and the backend-driven menu/page-capability projection.
- Rename “能力目录” to “页面能力目录”, rename “授权资源目录” to “资源注册中心”, and group both under “权限治理”.
- Add bidirectional navigation between page-capability runtime targets and their registered resources.

## Capabilities

### New Capabilities

- `authorization-resource-catalog`: Governed discovery and inspection of registered authorization resources and their child metadata.

### Modified Capabilities

- None.

## Impact

- `trio-base-frontend/apps/web-antd/src/views/system`: new authorization resource catalog page.
- `trio-base-frontend/apps/web-antd/src/router/routes/modules/system.ts`: new route.
- `trio-base-frontend/apps/web-antd/src/api/system/authorization.ts`: resource catalog query types/API usage.
- `trio-base-services/service-auth`: menu and page-capability declarations/migration for backend-driven navigation.
- Existing `/api/v1/authz/resources`, `/resources/tree`, and `/resources/stale` owner APIs remain authoritative; no cross-owner writes are introduced.
