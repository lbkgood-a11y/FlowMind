# integration-routing-orchestration Specification

## Purpose
Define governed connector registration, deterministic routing, immutable releases, shared OpenAPI runtime contracts, runtime-plane execution boundaries, callbacks, admission control, and observable execution evidence.

## Requirements

### Requirement: Separate OpenAPI control-plane and runtime contracts
The system SHALL keep OpenAPI lifecycle governance in `service-openapi` while exposing shared runtime DTOs, entities, enums, type handlers, and resolution interfaces through `openapi-common`.

#### Scenario: Runtime depends on shared contract
- **WHEN** a gateway or runtime adapter needs to resolve an active release, check subscription access, interpret execution state, or persist callback/runtime evidence
- **THEN** it depends on `openapi-common` contracts such as `ReleaseResolver`, `SubscriptionAccessChecker`, `CallbackProfileResolver`, `CompiledRouteRelease`, `RuntimeAdmissionContext`, and shared runtime entities instead of depending directly on `service-openapi` service classes

#### Scenario: Control plane owns governance and admission
- **WHEN** `service-openapi` owns the authoritative route, subscription, callback profile, policy, and release publication lifecycle
- **THEN** it serves governed management/internal admission APIs without exposing arbitrary public runtime execution endpoints by default

#### Scenario: Runtime adapter owns runtime lifecycle
- **WHEN** OpenAPI runtime routes, callbacks, or Temporal workers are enabled
- **THEN** they run in the dedicated `service-api-runtime` process using task queue `service-api-runtime`, shared `openapi-common` contracts, and runtime-local resolvers for published projections, while `service-openapi` remains a control-plane service without Temporal worker dependencies

#### Scenario: Runtime does not depend on control-plane implementation packages
- **WHEN** `service-api-runtime` compiles or runs
- **THEN** it MUST NOT depend on `com.triobase.service.openapi..` classes or the `service-openapi` Maven artifact, and architecture tests MUST fail if the dependency is reintroduced

#### Scenario: Shared runtime primitives are single-sourced
- **WHEN** control-plane or runtime-plane code validates JSON payloads, accesses JSON Pointer paths, validates Mapping DSL safety, interprets mapping operations, redacts sensitive evidence, or verifies gateway trust headers
- **THEN** it MUST use the `openapi-common` shared primitives and MUST NOT define service-local copies of `JsonPayloadValidator`, `JsonTreeAccess`, `MappingSecurityValidator`, `MappingOperation`, `MappingRuleRequest`, `TransformationResult`, `SensitiveDataRedactor`, or `GatewayTrustVerifier`

#### Scenario: Shared module remains logic-light
- **WHEN** a developer adds OpenAPI runtime support
- **THEN** `openapi-common` contains only serializable contracts, MyBatis-compatible shared persistence types, deterministic mapping/redaction/gateway-verification primitives, credential contracts, no-I/O outbound authentication assembly, and narrow resolver interfaces, and does not contain outbound HTTP execution, Temporal workflow implementation, credential provider implementation, or business policy mutation logic

### Requirement: Register governed connector endpoints
The system SHALL register versioned connector endpoints with approved base URL, operation path, HTTP method, timeout, authentication profile reference, network policy, and read-only/state-changing classification.

#### Scenario: Register endpoint with secret reference
- **WHEN** an authorized manager registers an endpoint with a valid credential reference
- **THEN** the system stores only the reference and never stores or returns the resolved secret

#### Scenario: Supply runtime URL
- **WHEN** an invocation attempts to override the registered target URL
- **THEN** the system rejects the override and does not perform a network call

### Requirement: Define and publish deterministic routes
The system SHALL allow `service-openapi` to resolve a stable route key by tenant, environment, enabled state, effective time, priority, and constrained predicates to one immutable integration release.

#### Scenario: Resolve one matching route
- **WHEN** an invocation matches a single highest-priority published route
- **THEN** the system resolves that route's pinned release snapshot

#### Scenario: Detect ambiguous publication
- **WHEN** two candidate routes could match the same context with equal priority
- **THEN** the system blocks publication until ambiguity is removed

### Requirement: Publish immutable integration releases
The system SHALL publish an immutable snapshot that pins all structure, mapping, value-map, connector, route, callback profile, policy, and orchestration versions required for runtime admission or execution.

#### Scenario: Invoke after draft changes
- **WHEN** a draft dependency changes after a release was published
- **THEN** existing runtime invocations continue using the published pinned version

#### Scenario: Roll back active release
- **WHEN** an authorized operator rolls a route back to a prior compatible published release
- **THEN** the active release pointer changes atomically and the action is audited

#### Scenario: Runtime consumes compiled release
- **WHEN** a runtime adapter receives an invocation for a route key
- **THEN** it obtains the active immutable `CompiledRouteRelease` through the shared `ReleaseResolver` contract and executes only against the pinned release content

### Requirement: Restrict synchronous invocation
The system SHALL permit synchronous direct invocation only in a governed runtime adapter for a single operation declared read-only with a timeout below 500 milliseconds and no state-changing integration semantics.

#### Scenario: Invoke eligible read operation
- **WHEN** an authenticated caller invokes an eligible published read route
- **THEN** the runtime validates admission with `openapi-common` contracts, maps the request, calls the registered endpoint once, maps and validates the response, and returns within the configured timeout behavior

#### Scenario: Attempt synchronous state change
- **WHEN** a caller synchronously invokes a route classified as state-changing, multi-step, or long-running
- **THEN** the runtime rejects synchronous mode and requires an owner-hosted Global Action or durable orchestration path

### Requirement: Execute durable integrations through owner-governed runtime
The system SHALL execute state-changing, retryable, long-running, callback-based, or multi-step integrations through an owner-governed runtime path, and orchestration starts or cancellation requests triggered by GUI, LUI, Agent, scheduler, event, workflow, or API mutation sources SHALL be submitted as owner-hosted Global Actions.

#### Scenario: Start orchestration
- **WHEN** an authenticated caller starts a published orchestration route with a valid canonical payload through a Global Action
- **THEN** the owner-hosted OpenAPI action validates admission, creates or attaches to an execution reference, and propagates trace, tenant, actor, action, and idempotency context to the runtime implementation

#### Scenario: Cancel orchestration
- **WHEN** an authenticated caller requests cancellation for a non-terminal orchestration execution
- **THEN** the system dispatches `integration.orchestration.cancel`, signals or marks the owning runtime execution through the owner-hosted OpenAPI action contract, and records action id, trace id, actor, target execution, reason, and idempotency key

#### Scenario: Perform network I/O
- **WHEN** an orchestration step invokes an external endpoint
- **THEN** the network call occurs in runtime execution code outside deterministic workflow logic; when Temporal is used, the call occurs in an Activity and not in Workflow code

### Requirement: Support declarative orchestration steps
The system SHALL support validated invoke, transform, conditional branch, parallel group, wait/timer, and compensation references without arbitrary executable code.

#### Scenario: Validate orchestration graph
- **WHEN** a draft definition contains an unreachable step, missing reference, invalid cycle, or unsupported operation
- **THEN** publication is blocked with graph validation errors

#### Scenario: Run compensation
- **WHEN** a completed state-changing step declares compensation and a later step fails under the configured policy
- **THEN** the workflow invokes eligible compensations in deterministic reverse order and records each result

### Requirement: Enforce idempotency and retry policies
The system SHALL require idempotency handling for state-changing runtime steps and SHALL apply explicit timeout and retry policies from approved presets; Global Action idempotency SHALL prevent duplicate orchestration dispatch before owner execution starts.

#### Scenario: Repeat invocation key
- **WHEN** the same tenant, action type, route release, and idempotency key are submitted again
- **THEN** the system returns or attaches to the existing action and integration execution rather than creating a duplicate side effect

#### Scenario: Retry transient failure
- **WHEN** a runtime step receives a configured retryable transport failure
- **THEN** the runtime retries according to the pinned policy and records each sanitized attempt with trace and action correlation; when Temporal is used, retry policy is applied by the Activity configuration

### Requirement: Secure and observe runtime execution
The system SHALL expose runtime routes only through governed enforcement points such as `platform-gateway` or a dedicated runtime adapter, enforce authorization and rate limits, propagate TraceId, redact secrets and sensitive payload fields, retain searchable execution status and sanitized step evidence, and correlate state-changing executions with Global Action records.

#### Scenario: Trace an invocation
- **WHEN** a request enters through the gateway with a trace context and creates a Global Action
- **THEN** the same trace and action context is available in route resolution, runtime context, outbound calls, execution records, action records, and audit records

#### Scenario: Inspect failed execution
- **WHEN** an authorized operator views a failed execution
- **THEN** the operator sees action id, route release, step, timing, attempt, and sanitized error information but no resolved credentials or unredacted sensitive values

### Requirement: Receive authenticated external callbacks
The runtime plane SHALL provide dedicated published callback endpoints with pinned authentication or signature profiles, callback structures, inbound mappings, correlation rules, deduplication policy, and runtime signal definitions governed by `service-openapi` callback profiles.

#### Scenario: Resume a waiting workflow
- **WHEN** a callback passes authentication and schema validation and its correlation data identifies a waiting execution
- **THEN** the runtime durably records the callback, maps its payload, signals or resumes the waiting runtime execution, and returns the configured partner acknowledgement

#### Scenario: Receive duplicate partner event
- **WHEN** a callback repeats a previously accepted tenant, callback profile, and partner event identifier
- **THEN** the system returns an idempotent acknowledgement without signaling the workflow a second time

#### Scenario: Reject invalid signature
- **WHEN** a callback fails the pinned authentication, signature, timestamp, or replay-window policy
- **THEN** the system rejects it without exposing correlation or execution details and records a sanitized security event

#### Scenario: Correlation is unknown or terminal
- **WHEN** a valid callback cannot match an active waiting execution
- **THEN** the system quarantines it for authorized operator review and does not start or mutate an unrelated workflow

### Requirement: Persist callback before acknowledgement
The system SHALL durably persist and deduplicate an accepted callback before returning a configured HTTP status, fixed text, or fixed JSON acknowledgement.

#### Scenario: Runtime unavailable after callback receipt
- **WHEN** a valid callback is durably accepted while downstream runtime processing is unavailable
- **THEN** the system returns the configured acknowledgement and retries asynchronous mapping and signaling without losing the callback

### Requirement: Apply asynchronous admission limits
The system SHALL enforce bounded asynchronous admission and SHALL reject new orchestration work when the effective queue or active-execution limit is full before dispatching a state-changing Global Action to owner execution.

#### Scenario: Asynchronous capacity exhausted
- **WHEN** a client submits orchestration work after its bounded capacity is exhausted
- **THEN** the system rejects the Global Action without creating an untracked workflow and returns retry guidance allowed by policy

#### Scenario: Internal admission decision
- **WHEN** a governed runtime adapter asks `service-openapi` for an admission decision
- **THEN** `service-openapi` evaluates subscription, active release, policy version, and execution limits through an internal API or shared checker contract and fails closed when policy state is missing or stale

### Requirement: Retain sanitized execution evidence
The system SHALL retain execution metadata and sanitized error summaries for 180 days by default, SHALL NOT persist request or response bodies by default, and SHALL limit authorized redacted diagnostic-body retention to seven days.

#### Scenario: Record normal execution
- **WHEN** an invocation completes without diagnostic retention enabled
- **THEN** the system stores identifiers, versions, timing, status, and sanitized errors but no request or response body

#### Scenario: Diagnostic capture contains secrets
- **WHEN** authorized diagnostic capture processes credentials, signatures, authorization headers, or classified sensitive fields
- **THEN** those values are excluded from retained evidence regardless of diagnostic mode
