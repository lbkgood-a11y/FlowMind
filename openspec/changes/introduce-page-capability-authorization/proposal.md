## Why

Role authorization currently exposes resource/action contracts as the primary configuration source, but implementation personnel normally know only the customer's menus, page functions, and desired user responsibilities. Explaining the technical model more clearly does not close that usability gap, and a UI-only translation would leave runtime enforcement, mapping drift, publishing, validation, and audit incomplete.

## What Changes

- Introduce a business-facing page capability catalog that distinguishes page access, read capabilities, and operation capabilities using the same language as the actual product UI.
- Let implementation personnel configure roles by menu page, visible/readable content, business operations, data scope, field restrictions, and business constraints without seeing resource codes, action codes, endpoints, or service names.
- Persist role-to-page-capability intent as the desired configuration source and compile it into existing resource/action grants for runtime enforcement.
- Add explicit capability dependencies, per-operation scope overrides, mapping readiness diagnostics, versioning, drift detection, impact analysis, and fail-closed compilation.
- Add draft, validate, publish, failure, rollback, and audit lifecycle support so incomplete mappings never silently become production permissions.
- Require menus, routes, buttons, backend actions, query scopes, field rules, and business guards to consume one published authorization result.
- Add actual-user and current-role validation with business-language explanations before and after publication.
- Provide migration and compatibility checks that preserve existing effective permissions without silently expanding access.

## Capabilities

### New Capabilities

- `page-capability-authorization`: Business-facing page capability catalog, role intent configuration, compilation, publishing, validation, drift management, and production authorization workflow.

### Modified Capabilities

- `enterprise-authorization-model`: Treat published page-capability intent as the management source while retaining resource/action grants as the runtime projection and enforcement contract.
- `menu-management-workbench`: Let product developers register page capabilities and let platform administrators diagnose mappings without exposing technical mapping maintenance to implementation personnel.

## Impact

- Authorization entities, migrations, services, compilation and decision APIs in `service-auth`.
- Authorization manifests and owner-service registrations for page operations, dependencies, scope support, field support, and guards.
- Role management, menu management, authorization diagnostics, validation, and audit views in `trio-base-frontend/apps/web-antd`.
- Existing role grants and policies, which require migration snapshots, compatibility comparison, rollback, and no-permission-expansion checks.
- Route guards, UI operation visibility, backend action enforcement, data policies, field adapters, and guard evaluation contracts.
- Contract, integration, migration, frontend, and end-to-end production acceptance tests.
