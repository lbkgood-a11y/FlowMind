## Why

OpenAPI runtime can publish and run durable integrations, but its orchestration DSL can only invoke external connectors today. Platform business APIs such as the leave request form already exist as owner-hosted Global Actions, so exposing them through OpenAPI currently has a functional gap between route orchestration and the owning service action runtime.

## What Changes

- Add a first-class `OWNER_ACTION` orchestration step for invoking owner-hosted Global Actions from `service-api-runtime`.
- Preserve microservice data ownership: OpenAPI runtime orchestrates, maps context, and records execution evidence; the owner service still validates, authorizes, idempotently executes, audits, and returns the business result.
- Use the lowcode leave submit-and-launch action as the canonical end-to-end example: an open route accepts a leave payload, dispatches `lowcode.form.submit` with `submitAndLaunch`, and returns the lowcode/workflow execution result through the OpenAPI execution record.
- Reject the shortcut of modeling internal owner actions as generic outbound HTTP connectors, because it weakens action semantics, evidence correlation, idempotency, and owner-boundary clarity.
- Add runtime configuration for approved owner action endpoints instead of allowing orchestration definitions to embed arbitrary URLs or credentials.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `integration-routing-orchestration`: Add governed owner-hosted Global Action invocation as a supported durable orchestration step.

## Impact

- `service-openapi` orchestration validation accepts and governs the new `OWNER_ACTION` step fields.
- `service-api-runtime` workflow and activities dispatch owner-hosted action requests, propagate tenant/trace/idempotency context, and persist sanitized step evidence.
- Runtime configuration gains an allow-listed owner service endpoint map for platform action dispatch.
- `service-openapi` gains a local seed migration for the `leave.submit` DEV route, mapping, orchestration, release, application client, and subscription.
- Documentation gains a leave request open API flow showing route payload, orchestration step, owner action request, headers, seeded ids, and expected execution result.
