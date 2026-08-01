## Why

Field policies can currently be configured centrally even when an Owner API does not consume the resulting field rules, producing a dangerous false authorization closure. TrioBase needs one mandatory, testable Owner field-governance contract and complete adoption by every existing field-bearing resource before future features can register configurable fields.

## What Changes

- Define a platform-wide Owner field-governance contract covering read filtering, masking, hidden fields, write denial, decision evidence, and fail-closed behavior.
- Require every field-bearing authorization resource to advertise verified read/write enforcement capabilities before field policies can be configured.
- Provide reusable common adapters and decision integration helpers so Owner services do not reimplement masking semantics.
- Integrate all existing field-bearing resources: `USER`, `ORG_UNIT`, published lowcode forms, and custom document runtime resources.
- Add registry coverage diagnostics that distinguish registered, verified, partially enforced, stale, and non-compliant resources.
- Add architecture/contract tests that block new field metadata when the Owner has no verified adapter.
- Enforce field policy rules on list, detail, create, update, export, and governed AI/tool reads where applicable.

## Capabilities

### New Capabilities

- `owner-field-governance`: Mandatory Owner contract, runtime enforcement, coverage diagnostics, and production readiness rules for field-level authorization.

### Modified Capabilities

- None.

## Impact

- `trio-base-common/common-core` and `common-dto`: shared field-governance contracts and helpers.
- `service-auth`: USER enforcement, registry policy gates, diagnostics, and verified-adapter declarations.
- `service-org`: ORG_UNIT enforcement and Owner declaration.
- `service-lowcode` and `service-api-runtime`: alignment and regression verification of existing enforcement.
- Authorization resource metadata/migrations: correct Owner and enforcement capability projection.
- CI tests and OpenSpec governance documentation for all future Owner services.
