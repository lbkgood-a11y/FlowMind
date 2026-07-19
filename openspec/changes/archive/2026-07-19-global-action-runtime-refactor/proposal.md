## Why

TrioBase is moving from conventional GUI-driven CRUD toward a GUI + LUI + Agent + Workflow operating model, but business-changing operations are currently expressed through several local concepts: lowcode application actions, workflow start requests, task operations, closure effects, integration executions, and page-specific frontend calls. This creates duplicated permission checks, inconsistent status semantics, fragmented audit trails, and makes it unsafe for LUI or Agent tooling to execute the same business operation path as the GUI.

This change establishes a platform-level Global Action Runtime so every business-changing operation is expressed, validated, authorized, executed, traced, and audited through one contract before reaching lowcode runtime, workflow runtime, Temporal, closure effects, OpenAPI orchestration, or future LUI/Agent tool calls.

## What Changes

- Introduce a Global Action model for business-changing operations, including action envelope, actor, source, target, payload, context, idempotency, trace correlation, execution mode, status lifecycle, result, errors, and emitted events.
- Add a backend Action Runtime with registry, schema validation, permission/policy checks, state guards, idempotency guard, executor routing, workflow adapter, event publication, and action audit recording.
- Add a persistent Action execution record model so UI, LUI, Agent, audit views, retries, and diagnostics can query operation status by `actionId`, `correlationId`, `traceId`, and `idempotencyKey`.
- Refactor lowcode runtime actions, process instance launch, workflow task operations, closure effect retry/manual handling, and OpenAPI runtime operations to use Global Action internally.
- Add a frontend Action Client and dispatch composable so pages invoke business operations through one client-side operation flow while keeping purely local UI events outside the Global Action model.
- Prepare LUI and Agent tool calls to execute only registered Global Actions with schema validation and confirmation metadata.
- **BREAKING**: replace local action DTO semantics such as `RuntimeActionRequest`, `RuntimeActionResponse`, direct workflow start request handling, task-specific operation IDs, and closure retry responses with Global Action request/result semantics.
- **BREAKING**: deprecate and remove page-level direct business API wrappers once migrated to `dispatchAction`; legacy endpoints may exist only as temporary compatibility adapters during the migration window.
- **BREAKING**: normalize local status strings such as `FORM_SAVED`, `WORKFLOW_STARTED`, `WORKFLOW_PENDING`, task operation statuses, closure operation statuses, and OpenAPI runtime states into the Global Action status lifecycle with domain-specific details in result data.
- Remove redundant frontend API wrappers, duplicate status mapping utilities, local action request/response DTOs, and duplicated authorization/idempotency/audit code after each operation family migrates.

## Capabilities

### New Capabilities
- `global-action-runtime`: Defines the cross-platform Global Action envelope, registry, execution lifecycle, idempotency, status/result model, events, diagnostics, and frontend dispatch contract.
- `lui-agent-action-bridge`: Defines how LUI intents and Agent tool calls are constrained to registered Global Actions with schema validation, confirmation, authorization, and audit correlation.

### Modified Capabilities
- `lowcode-application-runtime`: Runtime application actions must dispatch Global Actions instead of local action DTOs and page-specific execution semantics.
- `lowcode-form-data-runtime`: Form submission, process binding, workflow status updates, and retryable form workflow operations must be correlated with Global Action records.
- `enterprise-authorization-model`: Authorization enforcement must support action-level policy evaluation using Global Action actor, target, payload, tenant, and guard context.
- `platform-audit-log`: Operation audit queries must expose Global Action identity, status, source, target, correlation, and result alongside existing HTTP audit data.
- `integration-routing-orchestration`: Runtime connector/orchestration side effects must be invocable and traceable as Global Actions when triggered by GUI, LUI, Agent, workflow, or scheduler sources.

## Impact

- Java common modules: add shared Action DTOs, enums, error/result contracts, and action correlation helpers.
- Java services: add or refactor Action Runtime components in the appropriate platform/service boundary; migrate `service-lowcode`, `service-workflow-engine`, `service-openapi`, and relevant ops/auth integration points.
- Database: add action definition/execution/event/audit tables or migrations, indexes for `actionId`, `traceId`, `correlationId`, `idempotencyKey`, actor, source, target, and status.
- Frontend: add an Action Client and replace business-changing direct calls in lowcode runtime pages, process instance pages, task dialogs, closure effect operations, OpenAPI workbench/lifecycle operations, and future LUI panels.
- API: introduce `POST /api/v1/actions`, action query endpoints, optional SSE action event stream, and compatibility adapters for existing endpoints during migration.
- Temporal: workflow-starting and workflow-signaling operations must pass through Global Action and preserve Temporal determinism, Activity idempotency, and trace propagation.
- Governance: update ArchUnit, tests, frontend lint/tests, and documentation so new business-changing operations cannot bypass the Global Action Runtime.
