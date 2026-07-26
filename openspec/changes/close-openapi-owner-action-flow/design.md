## Context

Lowcode already exposes `lowcode.form.submit` through the owner-hosted action runtime at `/api/v1/lowcode-runtime/actions/dispatch`. The seeded leave application uses `appKey=leave`, `formKey=leave`, action code `submitAndLaunch`, and process key `leave_request`; the action submits a form instance and starts the published workflow.

OpenAPI runtime currently executes orchestration steps through connector invocation, mapping, branching, parallel execution, waits, and compensation. Connector invocation is intentionally designed for external HTTP targets and is governed by connector URL, credential, and network policy. Reusing it for platform owner services would work only as a transport hack and would lose the Global Action contract.

## Goals / Non-Goals

**Goals:**

- Allow an OpenAPI route orchestration to dispatch an owner-hosted Global Action as a durable step.
- Keep the owner service responsible for payload schema validation, guard evaluation, idempotency, audit events, and business execution.
- Propagate OpenAPI runtime context into the action request: tenant, trace id, release/execution correlation, application client actor, and idempotency key.
- Record owner action status, action id, owner service, owner execution reference, refresh scopes, retryability, and bounded errors in OpenAPI execution evidence.
- Provide a documented leave request route example that runs through OpenAPI runtime, lowcode action dispatch, and workflow launch.

**Non-Goals:**

- Adding arbitrary internal URL calls to orchestration definitions.
- Moving lowcode form submission or workflow launch logic into OpenAPI runtime.
- Replacing connector invocation for true external partner APIs.
- Building a public developer portal or frontend wizard for this exact leave route in this change.

## Decisions

### 1. Add `OWNER_ACTION` instead of overloading `INVOKE`

`INVOKE` remains an external connector step. `OWNER_ACTION` carries action-specific fields such as `ownerService`, `actionType`, `targetType`, `targetId`, `executionMode`, `payloadPointer`, and optional `idempotencyKeyPointer`.

Alternative considered: register lowcode as an HTTP connector. This was rejected because orchestration definitions would hide a platform action behind generic network semantics and would not clearly preserve action audit, owner service identity, or action result normalization.

### 2. Keep endpoint resolution out of orchestration JSON

The step declares `ownerService`, and runtime resolves it through an allow-listed configuration map. The DSL never accepts `url`, `credential`, or authorization fields for owner action steps.

Alternative considered: add `actionEndpoint` directly to the step. This was rejected because it creates a new SSRF and topology-coupling surface.

### 3. Build `GlobalActionRequest` inside an Activity

The Temporal Workflow remains deterministic and only chooses the next step. The Activity reads the step and payload, builds the action request, performs HTTP dispatch, and records evidence.

Alternative considered: create the action request in Workflow code. This was rejected because future context enrichment or time-based metadata would risk violating Temporal determinism.

### 4. Use OpenAPI idempotency as the root key

For an owner action step, the effective idempotency key is either a configured pointer/static value or `openapi:{rootIdempotencyKey}:{executionId}:{stepKey}`. This gives each owner step deterministic duplicate suppression without collapsing multiple side effects in the same orchestration.

### 5. Treat non-success owner results as step failures

`SUCCEEDED` completes the step. `ACCEPTED` and `RUNNING` are allowed to complete only when the step declares an async mode in the future; the first implementation treats them as accepted evidence but not a completed synchronous business result. `FAILED` and `REJECTED` fail the step and use the step failure policy.

## Risks / Trade-offs

- [Owner endpoint auth differs by service] → Resolve only allow-listed owner endpoints and reuse the standard `GlobalActionRequest` context; services keep their own permission/guard enforcement.
- [In-memory owner action idempotency is not production-grade] → The step passes stable keys now; owner services can later replace the common in-memory store with persistent stores without changing OpenAPI runtime.
- [Action payload mapping can become too expressive] → The first version supports JSON Pointer plus static object merge only, leaving complex transforms in existing mapping steps.
- [Async owner actions need callback semantics] → This change closes synchronous lowcode submit-and-launch; long-running owner action result polling can be added as a separate step type or wait/signal pattern later.

## Migration Plan

1. Deploy the additive DSL validator and runtime activity support.
2. Configure `service-lowcode` owner action endpoint in `service-api-runtime`.
3. Apply the local seed migration or publish an equivalent leave request route whose orchestration maps the inbound payload into the lowcode action payload and uses `OWNER_ACTION`.
4. Validate an execution by checking OpenAPI execution status, step attempts, lowcode action audit, lowcode form instance, and workflow process instance.

Rollback removes or disables routes containing `OWNER_ACTION`; connector, callback, mapping, and existing orchestration behavior remains unchanged.

## Open Questions

None for the leave request flow. Async owner action completion semantics should be designed separately when an owner action intentionally returns `ACCEPTED` without a terminal business result.
