## MODIFIED Requirements

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

### Requirement: Apply asynchronous admission limits
The system SHALL enforce bounded asynchronous admission and SHALL reject new orchestration work when the effective queue or active-workflow limit is full before dispatching a state-changing Global Action to owner execution.

#### Scenario: Asynchronous capacity exhausted
- **WHEN** a client submits orchestration work after its bounded capacity is exhausted
- **THEN** the system rejects the Global Action without creating an untracked workflow and returns retry guidance allowed by policy
