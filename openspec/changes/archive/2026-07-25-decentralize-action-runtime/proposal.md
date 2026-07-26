## Why

The current `service-action` implementation has become a synchronous execution facade for lowcode, workflow, and OpenAPI actions, which reintroduces a central hot path for business mutations and creates loopback calls from services to themselves. TrioBase needs to preserve the Global Action contract for GUI/LUI/Agent consistency while moving execution, idempotency, authorization, audit, and Temporal coordination back into the owning services.

## What Changes

- Keep `common-action` as the shared Global Action contract and extend it with reusable validation, idempotency key, status transition, redaction, and audit event contracts.
- Add a Spring starter-style owner runtime so services can self-host action validation, authorization guards, idempotency, audit recording, and action result normalization.
- Move lowcode, workflow-engine, and OpenAPI action definitions and execution endpoints into their owner services.
- Update AI Agent and frontend action clients so candidate validation and dispatch target the owner-hosted action endpoints or discovery facade instead of depending on `service-action`.
- Move action timeline/query concerns out of the execution hot path and into an owner-emitted audit/read-model projection.
- **BREAKING** Remove `service-action` as the required public `/api/v1/actions` execution service after a dual-run migration window; gateway routes and service-to-service `service-action` clients are deleted when no callers remain.

## Capabilities

### New Capabilities

- `owner-hosted-action-runtime`: Defines the owner-service hosted runtime contract for action definition exposure, candidate validation, dispatch, idempotency, authorization, audit, and result normalization.

### Modified Capabilities

- `global-action-runtime`: Changes Global Action from a central `service-action` execution facade to a shared envelope plus owner-hosted runtime model.
- `lui-agent-action-bridge`: Changes LUI/Agent candidate validation and dispatch so AI never depends on a central business action service and still executes only registered, confirmed actions.
- `platform-audit-log`: Requires action audit/timeline events to be emitted by owner services or projections after `service-action` persistence is removed from the hot path.

## Impact

- Affects `trio-base-common/common-action`, owner services under `trio-base-services/service-lowcode`, `service-workflow-engine`, and `service-openapi`, the gateway route configuration, and `trio-base-ai/ai-agent-orchestrator`.
- Removes `trio-base-services/service-action` only after replacement endpoints, candidate dispatch, AI integration, and action/timeline audit behavior are verified.
- Changes public action submission behavior for clients that currently call `/api/v1/actions` or `/api/v1/actions/candidates/*`; they must migrate to owner-hosted action endpoints or the replacement action discovery/dispatch client.
- Preserves the Global Action request/result model, normalized lifecycle, confirmation requirements, sensitive payload redaction, trace correlation, and microservice ownership of business state.
