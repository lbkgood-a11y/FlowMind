## Why

`service-lowcode` can currently support a demo-grade form definition and expense-report flow, but it is not yet a production-grade rapid development service: form metadata is weakly versioned, publish is mostly a status flip, service-side authorization is incomplete, migrations are partly owned by other services, and the reusable runtime stops at hand-coded sample pages.

This change hardens the quick-development foundation so TrioBase can safely let tenants build and publish small business applications from governed metadata, then bind those applications to the existing workflow-engine release and business-closure runtime.

## What Changes

- Make lowcode form definitions tenant-aware, immutable after publication, versioned by `formKey + version`, and validated server-side before create, update, publish, and workflow snapshot usage.
- Move lowcode-owned schema changes back into `service-lowcode` Flyway migrations, including audit, tenant, process-binding, version, status, and indexing changes.
- Add service-side permission checks and data-scope checks for all public lowcode definition and instance APIs, including instance detail and process binding.
- Add a governed rapid-development application model that groups business object, forms, list/detail page metadata, actions, permissions, and optional workflow binding into one publishable unit.
- Add a generic metadata-driven business runtime page/API path for published quick-development applications so the expense page pattern no longer requires a new hard-coded Vue page per business object.
- Add publication diagnostics, audit records, and test coverage for invalid schemas, stale versions, unauthorized operations, cross-tenant access, migration ownership, and workflow-binding regressions.
- Keep workflow-engine as the owner of process packages, process publication, and business closure execution; lowcode publishes reusable forms and application metadata consumed by that runtime.

## Capabilities

### New Capabilities

- `lowcode-form-governance`: Tenant-scoped form definition lifecycle, immutable versions, server-side schema validation, workflow snapshot compatibility, and lowcode-owned migrations.
- `lowcode-form-data-runtime`: Secure form instance submission, query, detail, process binding, validation, auditing, idempotency, and tenant/data-scope isolation.
- `lowcode-application-runtime`: Publishable rapid-development applications composed of forms, list/detail page metadata, actions, permissions, and workflow bindings with a generic runtime UI/API.

### Modified Capabilities

- None.

## Impact

- `trio-base-services/service-lowcode`: schema migrations, entities, DTOs, controllers, services, validation, permission annotations, data-scope enforcement, tests.
- `trio-base-services/service-auth`: permission/menu/data-policy seed cleanup so it no longer creates or mutates lowcode-owned tables; new lowcode application/menu permissions remain registered here.
- `trio-base-services/service-workflow-engine`: internal form snapshot client compatibility and optional integration tests for the new immutable form version contract; no process package ownership change.
- `trio-base-platform/platform-gateway`: user context propagation must include tenant and role context needed by service-level isolation.
- `trio-base-frontend/apps/web-antd`: replace JSON-first form management with safer field/schema editing, add application runtime pages, and remove the need for hard-coded sample-only pages for new quick-development applications.
- `docs` and `openspec/specs/process-platform/design.md`: align service ownership language with the current implementation: workflow-engine owns process packages; lowcode owns reusable form and quick-application metadata.
