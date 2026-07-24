## 1. Runtime Baseline

- [x] 1.1 Update `ai-agent-orchestrator` to a supported stable LangGraph 1.x line, pin reproducible Poetry dependencies, and remove or explicitly isolate the unused Temporal Python dependency from production worker behavior.
- [x] 1.2 Create the FastAPI application factory, configuration model, dependency wiring, health/readiness endpoints, and environment-specific settings for the orchestrator.
- [x] 1.3 Add architecture checks that reject direct external model clients, Python Temporal Worker registration, arbitrary HTTP/SQL/shell/dynamic-code tools, and business mutation clients outside approved adapters.

## 2. Core Contracts and Registries

- [x] 2.1 Implement versioned Pydantic models for trusted request context, AgentState, actor, slots, evidence, ActionCandidate references, interrupts, usage, and structured errors.
- [x] 2.2 Implement a versioned graph registry that resolves graph id/version, pins a version per thread, and rejects incompatible checkpoint restoration without an explicit migration.
- [x] 2.3 Implement a typed node contract with input/output schema, timeout, retry eligibility, sensitivity classification, and stable observability name.
- [x] 2.4 Implement an allowlisted tool registry for `READ`, `RETRIEVAL`, and `ACTION_CANDIDATE` tools with validated inputs/outputs and bounded result sizes.
- [x] 2.5 Implement the standard SSE event envelope and event types with monotonic sequence, schema version, trace context, heartbeat, and safe forward compatibility.

## 3. Durable Agent Run Service

- [x] 3.1 Add PostgreSQL LangGraph checkpoint/store configuration and migrations using tenant, thread, and graph-version namespaces while allowing in-memory storage only in tests and local ephemeral mode.
- [x] 3.2 Add checkpoint state minimization, sensitive-field redaction/reference conversion, configurable retention, and thread deletion support.
- [x] 3.3 Implement create, inspect, event-stream, resume, and cancel Agent Run endpoints with authentication, authorization, idempotent run creation, and bounded error responses.
- [x] 3.4 Implement persisted SSE event replay using `Last-Event-ID` or cursor so reconnection never creates a new run or repeats a business action.
- [x] 3.5 Implement missing-input and confirmation interrupts, cancellation propagation, and configurable step, time, model-call, token, and cost budgets.

## 4. Governed Service Integrations

- [x] 4.1 Implement the LLM Gateway client with streaming and structured-output support, TraceId propagation, timeout/cancellation handling, and no provider-specific credentials in the orchestrator.
- [x] 4.2 Implement the RAG client with knowledge-space authorization, evidence/citation contracts, bounded context assembly, and a safe no-evidence response policy.
- [x] 4.3 Implement authenticated read-tool adapters that re-evaluate RBAC and data-scope policy on every protected call and never trust identity or tenant values from model output.
- [x] 4.4 Implement the `service-action` ActionCandidate availability/validation adapter and map registered definition, schema, permission, guard, confirmation, and error metadata into Agent events.
- [x] 4.5 Implement Action status correlation so accepted Global Actions and Java Temporal workflow references can be streamed back without making checkpoint state authoritative for business results.

## 5. Initial Graphs and Business Verticals

- [x] 5.1 Implement the top-level Router Graph with deterministic routing boundaries and explicit `knowledge-answer`, `business-assistant`, and `unsupported` outcomes.
- [x] 5.2 Implement the knowledge-answer graph with authorized retrieval, evidence validation, cited generation, no-evidence handling, and streaming output.
- [x] 5.3 Implement the shared Business Assistant Graph for intent recognition, slot collection, candidate construction, availability validation, confirmation interrupt, and result correlation.
- [x] 5.4 Register the leave-application domain schema, read tools, ActionDefinitions, missing-slot prompts, candidate mapping, and success/failure presentation metadata.
- [x] 5.5 Register the expense-reimbursement domain schema, read tools, ActionDefinitions, missing-slot prompts, candidate mapping, and success/failure presentation metadata.
- [x] 5.6 Ensure every first-phase state-changing LUI/Agent action pauses for explicit user confirmation and is submitted only through the existing Global Action bridge.

## 6. Frontend LUI Integration

- [x] 6.1 Implement the active frontend application's Agent SSE client/adapter with incremental rendering, heartbeat handling, cancellation, cursor-based reconnect, and terminal-state handling.
- [x] 6.2 Add registered and schema-validated UI components for evidence, missing-slot input, ActionCandidate preview, confirmation, Action status, and bounded errors.
- [x] 6.3 Connect LUI events to the application state/action layer so GUI updates are traceable and no Agent output directly mutates DOM or business form state.
- [x] 6.4 Integrate candidate confirmation with the Action Client and refresh scopes returned by Global Action, including accepted/running long workflows.

## 7. Security and Observability

- [x] 7.1 Correct the LLM Gateway redaction/logging order so raw Prompt content is never logged before masking, and replace raw previews with approved redacted summaries, hashes, or references.
- [x] 7.2 Propagate trusted tenant, actor, TraceId, correlationId, and run/thread references through LLM, RAG, read tools, ActionCandidate, Global Action, owner services, and Temporal headers.
- [x] 7.3 Add OpenTelemetry spans, structured metrics, and dashboards for run/node latency, first-token time, tokens/cost, cache, retries, interrupts, cancellation, candidate validation, and final action outcomes.
- [x] 7.4 Add Prompt Injection, malicious retrieval, unknown component, identity override, sensitive persistence, bounded error, authorization, and default-tenant isolation tests.

## 8. Evaluation, Operations, and Release

- [x] 8.1 Build versioned leave and expense golden datasets covering intent, slot extraction, evidence, ActionCandidate, confirmation, refusal, authorization denial, recovery, and adversarial cases.
- [x] 8.2 Add graph compile/branch tests, checkpoint restart tests, version migration tests, SSE ordering/replay tests, cancellation tests, and Global Action idempotency integration tests.
- [x] 8.3 Define approved quality thresholds and hard zero-tolerance safety invariants in CI so unsafe graph, Prompt, model-policy, tool-schema, or state releases cannot be promoted.
- [x] 8.4 Add orchestrator container/configuration, local dependency startup, health probes, resource limits, timeout/backpressure settings, and an operations runbook.
- [x] 8.5 Roll out behind feature flags, complete canary observation and rollback rehearsal, and record production acceptance for both leave and expense end-to-end flows.
