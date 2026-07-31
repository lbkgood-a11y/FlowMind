## Why

The role authorization page exposes menus, function grants, data policies, field rules, guard templates, and decision preview as peer concepts, leaving implementation personnel unable to understand the effective result or safely validate a configuration. Field policies are especially misleading because the decision engine can calculate them while most owner services do not enforce them at runtime.

## What Changes

- Replace the independent menu and function tabs with a guided “function and menu” workflow that shows the function selection and its derived menu result together, including unmapped and ancestor-only explanations.
- Redesign data-scope configuration as a resource/action → scope/dimension → effective-summary workflow with conflict and impact explanations.
- Rename and redesign field rules as field-access rules, and expose whether each resource enforces read hiding, masking, and write denial at runtime.
- Separate platform guard-template administration from role configuration; role configuration only shows the business constraints implied by granted actions.
- Replace raw user-ID entry in decision preview with searchable actual-user selection and add a current-role simulation mode.
- Return an explainable, layered preview covering function access, menu visibility, data scope, field rules, guard requirements, and final outcome.
- Require owner services that advertise field-policy support to enforce decision field rules for reads and writes.

## Capabilities

### New Capabilities

- `role-authorization-workbench`: Guided implementation workflow for function/menu, data scope, field access, business constraints, and authorization validation.

### Modified Capabilities

- `enterprise-authorization-model`: Add role-subject simulation and explicit runtime field-policy enforcement capability metadata and contracts.
- `menu-management-workbench`: Expose menu-to-resource/action mappings and derivation diagnostics needed by role authorization configuration.

## Impact

- Frontend role management and authorization-center views under `trio-base-frontend/apps/web-antd/src/views/system`.
- Authorization management and decision APIs in `service-auth`, including role simulation and explainable decision responses.
- Authorization resource manifests and registry metadata for field-policy enforcement support.
- Owner-service adapters that consume `fieldRules`, initially low-code and selected business/API runtimes.
- Permission configuration documentation, tests, and implementation acceptance scenarios.
