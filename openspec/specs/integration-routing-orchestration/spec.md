# integration-routing-orchestration Specification

## Purpose
Define governed connector registration, deterministic routing, immutable releases, synchronous invocation, Temporal orchestration, callbacks, admission control, and observable runtime evidence.
## Requirements
### Requirement: Register governed connector endpoints
The system SHALL register versioned connector endpoints with approved base URL, operation path, HTTP method, timeout, authentication profile reference, network policy, and read-only/state-changing classification.

#### Scenario: Register endpoint with secret reference
- **WHEN** an authorized manager registers an endpoint with a valid credential reference
- **THEN** the system stores only the reference and never stores or returns the resolved secret

#### Scenario: Supply runtime URL
- **WHEN** an invocation attempts to override the registered target URL
- **THEN** the system rejects the override and does not perform a network call

### Requirement: Define and publish deterministic routes
The system SHALL route a stable route key by tenant, environment, enabled state, effective time, priority, and constrained predicates to one immutable integration release.

#### Scenario: Resolve one matching route
- **WHEN** an invocation matches a single highest-priority published route
- **THEN** the system resolves that route's pinned release snapshot

#### Scenario: Detect ambiguous publication
- **WHEN** two candidate routes could match the same context with equal priority
- **THEN** the system blocks publication until ambiguity is removed

### Requirement: Publish immutable integration releases
The system SHALL publish an immutable snapshot that pins all structure, mapping, value-map, connector, route, and orchestration versions required for runtime execution.

#### Scenario: Invoke after draft changes
- **WHEN** a draft dependency changes after a release was published
- **THEN** existing runtime invocations continue using the published pinned version

#### Scenario: Roll back active release
- **WHEN** an authorized operator rolls a route back to a prior compatible published release
- **THEN** the active release pointer changes atomically and the action is audited

### Requirement: Restrict synchronous invocation
The system SHALL permit synchronous direct invocation only for a single operation declared read-only with a timeout below 500 milliseconds and no state-changing integration semantics.

#### Scenario: Invoke eligible read operation
- **WHEN** an authenticated caller invokes an eligible published read route
- **THEN** the system validates and maps the request, calls the registered endpoint once, maps and validates the response, and returns within the configured timeout behavior

#### Scenario: Attempt synchronous state change
- **WHEN** a caller synchronously invokes a route classified as state-changing, multi-step, or long-running
- **THEN** the system rejects synchronous mode and requires orchestration execution

### Requirement: Execute durable orchestration through Temporal
The system SHALL execute state-changing, retryable, long-running, callback-based, or multi-step integrations as Temporal workflows whose worker is embedded in `service-openapi`, and orchestration starts or cancellation requests triggered by GUI, LUI, Agent, scheduler, event, workflow, or API mutation sources SHALL be submitted as Global Actions.

#### Scenario: Start orchestration
- **WHEN** an authenticated caller starts a published orchestration route with a valid canonical payload through a Global Action
- **THEN** the system starts a workflow with a stable workflow identifier, returns an action execution reference, and propagates trace, tenant, actor, and action context

#### Scenario: Cancel orchestration
- **WHEN** an authenticated caller requests cancellation for a non-terminal orchestration execution
- **THEN** the system dispatches `integration.orchestration.cancel`, signals the owning workflow through `service-openapi`, and records action id, trace id, actor, target execution, reason, and idempotency key

#### Scenario: Perform network I/O
- **WHEN** an orchestration step invokes an external endpoint
- **THEN** the network call occurs in an Activity and not in Workflow code

### Requirement: Support declarative orchestration steps
The system SHALL support validated invoke, transform, conditional branch, parallel group, wait/timer, and compensation references without arbitrary executable code.

#### Scenario: Validate orchestration graph
- **WHEN** a draft definition contains an unreachable step, missing reference, invalid cycle, or unsupported operation
- **THEN** publication is blocked with graph validation errors

#### Scenario: Run compensation
- **WHEN** a completed state-changing step declares compensation and a later step fails under the configured policy
- **THEN** the workflow invokes eligible compensations in deterministic reverse order and records each result

### Requirement: Enforce idempotency and retry policies
The system SHALL require idempotency handling for state-changing Activities and SHALL apply explicit timeout and retry policies from approved presets; Global Action idempotency SHALL prevent duplicate orchestration dispatch before owner execution starts.

#### Scenario: Repeat invocation key
- **WHEN** the same tenant, action type, route release, and idempotency key are submitted again
- **THEN** the system returns or attaches to the existing action and integration execution rather than creating a duplicate side effect

#### Scenario: Retry transient failure
- **WHEN** an Activity receives a configured retryable transport failure
- **THEN** Temporal retries according to the pinned policy and records each sanitized attempt with trace and action correlation

### Requirement: Secure and observe runtime execution
The system SHALL expose runtime routes through `platform-gateway`, enforce authorization and rate limits, propagate TraceId, redact secrets and sensitive payload fields, retain searchable execution status and sanitized step evidence, and correlate state-changing executions with Global Action records.

#### Scenario: Trace an invocation
- **WHEN** a request enters through the gateway with a trace context and creates a Global Action
- **THEN** the same trace and action context is available in route resolution, workflow headers, activities, outbound calls, execution records, action records, and audit records

#### Scenario: Inspect failed execution
- **WHEN** an authorized operator views a failed execution
- **THEN** the operator sees action id, route release, step, timing, attempt, and sanitized error information but no resolved credentials or unredacted sensitive values

### Requirement: Receive authenticated external callbacks
The system SHALL provide dedicated published callback endpoints with pinned authentication or signature profiles, callback structures, inbound mappings, correlation rules, deduplication policy, and workflow signal definitions.

#### Scenario: Resume a waiting workflow
- **WHEN** a callback passes authentication and schema validation and its correlation data identifies a waiting execution
- **THEN** the system durably records the callback, maps its payload, signals the workflow, and returns the configured partner acknowledgement

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

#### Scenario: Worker unavailable after callback receipt
- **WHEN** a valid callback is durably accepted while the Temporal worker is unavailable
- **THEN** the system returns the configured acknowledgement and retries asynchronous mapping and signaling without losing the callback

### Requirement: Apply asynchronous admission limits
The system SHALL enforce bounded asynchronous admission and SHALL reject new orchestration work when the effective queue or active-workflow limit is full before dispatching a state-changing Global Action to owner execution.

#### Scenario: Asynchronous capacity exhausted
- **WHEN** a client submits orchestration work after its bounded capacity is exhausted
- **THEN** the system rejects the Global Action without creating an untracked workflow and returns retry guidance allowed by policy

### Requirement: Retain sanitized execution evidence
The system SHALL retain execution metadata and sanitized error summaries for 180 days by default, SHALL NOT persist request or response bodies by default, and SHALL limit authorized redacted diagnostic-body retention to seven days.

#### Scenario: Record normal execution
- **WHEN** an invocation completes without diagnostic retention enabled
- **THEN** the system stores identifiers, versions, timing, status, and sanitized errors but no request or response body

#### Scenario: Diagnostic capture contains secrets
- **WHEN** authorized diagnostic capture processes credentials, signatures, authorization headers, or classified sensitive fields
- **THEN** those values are excluded from retained evidence regardless of diagnostic mode

