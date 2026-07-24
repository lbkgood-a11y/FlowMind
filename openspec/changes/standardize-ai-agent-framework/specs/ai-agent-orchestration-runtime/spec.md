## ADDED Requirements

### Requirement: LangGraph is the standard stateful agent runtime
The system SHALL use a supported stable LangGraph 1.x release as the orchestration runtime for AI flows that require multiple steps, branching, tool calls, persistent state, interruption, or recovery, and SHALL NOT require simple classification, schema transformation, or single-call generation to be wrapped in a graph.

#### Scenario: Stateful business assistant uses a graph
- **WHEN** a leave or expense request requires intent detection, missing-field collection, action candidate construction, and user confirmation
- **THEN** the orchestrator executes the flow as a versioned LangGraph graph

#### Scenario: Simple deterministic transformation avoids graph overhead
- **WHEN** a component only converts one validated schema into another without state, tools, branching, or recovery
- **THEN** it executes as ordinary deterministic Python code rather than a LangGraph run

### Requirement: Agent runs use trusted and versioned state
Every agent run SHALL use a schema-validated AgentState containing graph, thread, run, tenant, actor, trace, correlation, locale, intent, slot, evidence, candidate, interrupt, status, usage, and error fields, and identity fields SHALL be populated only from trusted gateway or authentication context.

#### Scenario: Model attempts to replace identity context
- **WHEN** model output or user text contains a different tenant id, actor id, role, or permission claim
- **THEN** the orchestrator ignores the claimed identity values and retains the trusted request context

#### Scenario: State schema changes incompatibly
- **WHEN** an existing thread is resumed after an incompatible graph or AgentState release
- **THEN** the orchestrator uses the thread's pinned graph version or an explicit migration and does not silently load the checkpoint into the new schema

### Requirement: Production runs have durable AI checkpoints
The orchestrator SHALL persist production LangGraph checkpoints in PostgreSQL using a namespace that includes tenant, thread, and graph version, while treating owner-service data and Global Action records as the authoritative business state.

#### Scenario: Interrupted run resumes
- **WHEN** a run pauses for a missing field or confirmation and the service restarts
- **THEN** the run resumes from its persisted checkpoint without repeating completed non-repeatable work

#### Scenario: Checkpoint conflicts with business state
- **WHEN** checkpoint data differs from the current Global Action or owner-service result
- **THEN** the orchestrator treats the business runtime result as authoritative and reconciles the conversational state

### Requirement: Agent nodes have explicit execution contracts
Every graph node SHALL declare validated inputs and outputs, timeout, retry eligibility, sensitivity classification, and observability name, and SHALL return state updates without relying on shared mutable global state.

#### Scenario: Read-only node fails transiently
- **WHEN** an idempotent RAG or read-only business query fails with a retryable transient error
- **THEN** the runtime retries it within the configured attempt and time budget and records each attempt under the same run and node trace

#### Scenario: State-changing dispatch fails
- **WHEN** a Global Action submission or status query returns an error
- **THEN** LangGraph does not blindly retry a state-changing dispatch and instead uses the Global Action idempotency key and recorded action reference to determine the result

### Requirement: Model traffic uses the governed LLM path
All model calls originating from agents SHALL pass through the platform API Gateway controls and the `ai-llm-gateway`, and Agent code SHALL NOT call an external or private model provider directly.

#### Scenario: Agent requests a completion
- **WHEN** a model node needs structured output or generated text
- **THEN** it calls the governed LLM Gateway with trace, actor, tenant, model-policy, and redaction context

#### Scenario: Direct provider client is introduced
- **WHEN** Agent code imports or configures an external model provider client for a completion call
- **THEN** architecture checks fail the change unless the code belongs to the LLM Gateway provider adapter

### Requirement: Agent tools are registered and schema constrained
The orchestrator SHALL execute only registered `READ`, `RETRIEVAL`, and `ACTION_CANDIDATE` tools whose inputs and outputs pass Pydantic or JSON Schema validation, and SHALL reject arbitrary HTTP, SQL, shell, dynamic-code, free-Prompt, and direct business-mutation tools.

#### Scenario: Registered read tool executes
- **WHEN** an Agent invokes a registered leave-balance query with a schema-valid employee reference
- **THEN** the tool performs an authorized, data-scoped read and returns a schema-valid bounded result

#### Scenario: Prompt requests arbitrary execution
- **WHEN** user or retrieved content instructs the Agent to call an arbitrary URL, execute SQL or shell code, or invoke an unregistered mutation
- **THEN** the orchestrator rejects the tool request and records a safe policy diagnostic without executing it

### Requirement: Business changes use ActionCandidate and Global Action
The Agent runtime SHALL express every requested business state change as an ActionCandidate backed by a registered ActionDefinition, SHALL validate availability before display, and SHALL submit the change only through the Global Action bridge after required confirmation.

#### Scenario: Leave request candidate is valid
- **WHEN** the user provides all fields required for a registered leave submission action
- **THEN** the Agent emits a schema-valid ActionCandidate containing target, payload, reason, confirmation metadata, trace, and correlation references

#### Scenario: Candidate action is unregistered
- **WHEN** model output names an action type without a registered ActionDefinition
- **THEN** the bridge rejects the candidate before confirmation or business execution

#### Scenario: First-phase state change requires confirmation
- **WHEN** any LUI or Agent candidate would change business state during the first production phase
- **THEN** the system presents a registered confirmation component and dispatches only after explicit user confirmation

### Requirement: Agent authorization is evaluated at every protected boundary
The orchestrator SHALL use the authenticated human actor and optional bounded agent identity for authorization, and every protected read or ActionCandidate availability check SHALL be evaluated against current RBAC and data-scope policy rather than model-provided claims or stale conversation state.

#### Scenario: User asks for inaccessible expense data
- **WHEN** a user asks the Agent to retrieve an expense record outside the user's data scope
- **THEN** the read tool returns a bounded denial and the Agent does not expose the record or infer its sensitive contents

#### Scenario: Permission changes during a paused run
- **WHEN** a run resumes after the actor's permission was revoked
- **THEN** the runtime re-evaluates authorization before the next protected read or action dispatch

### Requirement: LangGraph does not replace Java Temporal business workflows
The system SHALL use LangGraph for AI reasoning and interaction state and SHALL use Temporal Workers embedded in Spring Boot owner services for cross-service business workflows, retries, compensation, and final consistency.

#### Scenario: Agent starts a long-running approval process
- **WHEN** a confirmed Agent candidate starts an approval workflow
- **THEN** Global Action dispatches to `service-workflow-engine`, which starts the Java Temporal Workflow and returns the action and workflow references

#### Scenario: Python worker registration is attempted
- **WHEN** `ai-agent-orchestrator` attempts to register a Python Temporal Worker or the Temporal LangGraph plugin for a production business workflow
- **THEN** architecture checks reject the configuration

### Requirement: Agent output is streamed through a versioned SSE contract
The Agent API SHALL stream text and intermediate state using SSE events with event id, type, run id, thread id, monotonic sequence, timestamp, trace id, data schema version, and schema-validated data.

#### Scenario: Business assistant streams a candidate
- **WHEN** the Agent produces explanation text, evidence, missing slots, and an ActionCandidate
- **THEN** the client receives ordered `message.delta`, `evidence.ready`, `slot.missing`, and `action.candidate` events without waiting for the whole run to finish

#### Scenario: Client reconnects
- **WHEN** an SSE client reconnects with its last event id or cursor
- **THEN** the service replays subsequent events without creating a new run or redispatching a business action

#### Scenario: Client receives a future event type
- **WHEN** an older client receives an unknown event type with a compatible envelope version
- **THEN** it safely ignores the event and continues processing subsequent known events

### Requirement: Agent runs support interruption, cancellation, and bounded execution
The runtime SHALL support explicit interrupts for missing input and confirmation, cancellation propagation, maximum step and model-call limits, time budgets, and token or cost budgets.

#### Scenario: Required field is missing
- **WHEN** a leave request lacks its start date
- **THEN** the graph emits `slot.missing`, persists an interrupt, and resumes from that interrupt after receiving validated user input

#### Scenario: User cancels a run
- **WHEN** the user cancels an active run
- **THEN** the orchestrator stops further model and tool work, closes the stream with a terminal cancellation event, and does not cancel an already accepted Global Action implicitly

#### Scenario: Run exceeds its budget
- **WHEN** a graph reaches its configured step, time, token, or cost limit
- **THEN** the runtime terminates with a stable bounded error and preserves diagnostic metadata without leaking Prompt or secret content

### Requirement: Agent data is minimized and protected
Agent logs, checkpoints, traces, cache keys, events, and evaluation records SHALL exclude credentials, authorization headers, raw secrets, and unnecessary sensitive Prompt content, and SHALL apply configured redaction, encryption, reference storage, retention, and deletion controls.

#### Scenario: Sensitive expense content enters a run
- **WHEN** an expense conversation contains bank account or identity data
- **THEN** only the minimum approved redacted fields or protected references are persisted outside the owner service

#### Scenario: Error is returned to the client
- **WHEN** a provider, tool, or node raises an internal exception
- **THEN** the client receives a stable error code and bounded message without stack trace, raw Prompt, credential, or provider-secret data

### Requirement: Retrieved and generated content is untrusted
The orchestrator SHALL treat user text, retrieved documents, model output, and tool output as untrusted data that cannot alter system policy, tool registration, identity, authorization, confirmation rules, or component registry membership.

#### Scenario: Retrieved document contains tool instructions
- **WHEN** a retrieved document instructs the Agent to ignore policy and execute a privileged action
- **THEN** the content is treated only as evidence and cannot modify the allowed tools or bypass confirmation and authorization

#### Scenario: Generated UI requests an unknown component
- **WHEN** model output names an unregistered component or supplies props that fail the component schema
- **THEN** the frontend rejects the render request and records a safe diagnostic

### Requirement: Trace and audit correlation spans the complete Agent path
Every Agent run SHALL propagate gateway TraceId and correlation context through graph nodes, LLM Gateway, RAG, read tools, ActionCandidate validation, Global Action, owner service, and Java Temporal headers where applicable.

#### Scenario: Expense action completes
- **WHEN** a user confirms an expense reimbursement action generated by the Agent
- **THEN** operators can correlate the conversation run, candidate, confirmation, Global Action, owner execution, workflow, and audit record using trace and correlation references

#### Scenario: Downstream service omits trace context
- **WHEN** an Agent dependency returns without the required trace correlation
- **THEN** the runtime records an observability contract violation and does not fabricate a model-provided trace id

### Requirement: Agent releases pass evaluation and safety gates
Every production graph, Prompt, model-policy, tool-schema, or AgentState release SHALL be versioned and SHALL pass automated graph, recovery, streaming, security, authorization, and domain evaluation gates before promotion.

#### Scenario: Leave and expense golden set passes
- **WHEN** a candidate release is evaluated against the approved leave and expense datasets
- **THEN** intent, slot, evidence, ActionCandidate, refusal, and recovery metrics meet the versioned release thresholds

#### Scenario: Safety invariant fails
- **WHEN** tests detect any unregistered side effect, unconfirmed state change, unauthorized read, or cross-tenant access
- **THEN** CI blocks production promotion regardless of aggregate quality scores

### Requirement: Default-tenant operation remains namespace ready
The Agent runtime SHALL operate only with trusted tenant `default` in the current phase while including tenant scope in state, checkpoint, cache, trace, tool, retrieval, and ActionCandidate contracts for future isolation.

#### Scenario: Default tenant run is created
- **WHEN** an authenticated request enters the current single-tenant deployment
- **THEN** the runtime records `tenantId=default` from trusted gateway context across all downstream namespaces

#### Scenario: Client submits another tenant
- **WHEN** a client or model supplies a tenant value other than the trusted gateway tenant
- **THEN** the runtime rejects or ignores the supplied value and never uses it to select data, cache, checkpoint, or tools
