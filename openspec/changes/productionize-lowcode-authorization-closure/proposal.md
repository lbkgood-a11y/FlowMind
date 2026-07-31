## Why

Lowcode forms and applications already register semantic authorization resources and enforce decisions at runtime, but legacy URL-permission fallback, non-atomic publication synchronization, and fragmented page/resource authorization leave paths that can drift or fail open. Production use requires one fail-closed, retryable, observable lifecycle from publish through grant, execution, revocation, and offline.

## What Changes

- **BREAKING** remove runtime fallback from semantic authorization decisions to legacy URL permission codes for published lowcode applications and actions.
- Introduce a durable lowcode authorization publication outbox with idempotent delivery, retry, status inspection, and reconciliation for form/application publish and offline lifecycle events.
- Keep drafts non-runtime-visible until the required authorization resource snapshot is acknowledged, and fail closed while synchronization is pending or failed.
- Add application authorization bundles that grant the runtime page dependency, `LOWCODE_APP` VIEW, selected `LOWCODE_FORM` actions, data scope, and optional field policies as one administrator operation.
- Add lifecycle diagnostics and repair controls to the enterprise authorization workbench without allowing administrators to invent resources or actions.
- Ensure revocation and offline transitions remove runtime discoverability and execution immediately through lifecycle-aware authorization decisions.
- Add cross-service acceptance coverage for publish, bundle grant, runtime allow/deny, field enforcement, data scope, workflow guards, revocation, retry, reconciliation, and offline.

## Capabilities

### New Capabilities

- `lowcode-authorization-operations`: Durable synchronization, authorization bundles, lifecycle diagnostics, reconciliation, and production acceptance for generated lowcode resources.

### Modified Capabilities

- `lowcode-form-governance`: Publication and offline transitions become authorization-acknowledged, retryable lifecycle operations rather than best-effort synchronous registration.
- `enterprise-authorization-model`: Published lowcode runtime decisions become strictly semantic and fail closed, and administrators can grant an owner-defined lowcode application authorization bundle atomically.

## Impact

- `service-lowcode`: form/application lifecycle services, authorization resource synchronization, outbox persistence/worker, runtime decision fallback removal, diagnostics, and acceptance tests.
- `service-auth`: idempotent resource lifecycle acknowledgement, atomic bundle grant API/service, authorization diagnostics, audit, and reconciliation contracts.
- `web-antd`: lowcode publish status and enterprise authorization bundle/diagnostic workbench.
- Database migrations for lowcode-owned outbox/delivery state and auth-owned bundle audit/idempotency state.
- Deployment requires both services to support the new lifecycle contract before legacy fallback is removed.
