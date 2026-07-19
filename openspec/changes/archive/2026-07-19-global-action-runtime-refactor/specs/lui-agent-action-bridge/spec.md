## ADDED Requirements

### Requirement: LUI intents resolve to action candidates
The LUI layer SHALL convert natural-language business-changing intents into ActionCandidate objects and SHALL NOT mutate GUI state, business data, workflow state, or external systems directly.

#### Scenario: Natural language proposes form update
- **WHEN** a user says "set the approval amount to 5000 and submit"
- **THEN** the LUI layer produces one or more ActionCandidates with registered action types, targets, payloads, and confirmation metadata

#### Scenario: LUI cannot directly mutate DOM
- **WHEN** a natural-language instruction changes visible business data
- **THEN** the UI updates only after the corresponding Global Action updates store/query state and does not use direct DOM mutation

### Requirement: Agent tool calls are constrained to registered actions
Agent tools that create business side effects SHALL dispatch only registered Global Actions and SHALL NOT invoke arbitrary APIs, SQL, scripts, URLs, dynamic classes, or free Prompt execution definitions.

#### Scenario: Registered tool action
- **WHEN** an Agent tool call requests `process.closure.effect.retry`
- **THEN** the bridge validates that the action type is registered before creating a Global Action

#### Scenario: Unregistered tool action
- **WHEN** an Agent tool call requests an unregistered operation
- **THEN** the bridge rejects the tool call and records a bounded diagnostic reason

### Requirement: LUI and Agent payloads are schema validated
The LUI/Agent bridge SHALL validate candidate payloads against the target ActionDefinition schema before confirmation or execution.

#### Scenario: Candidate passes schema
- **WHEN** an Agent follow-up candidate includes all required payload fields
- **THEN** the bridge marks the candidate schema-valid and allows authorization evaluation

#### Scenario: Candidate fails schema
- **WHEN** a LUI candidate omits a required target id
- **THEN** the bridge rejects or asks for missing input and does not create a Global Action

### Requirement: High-risk actions require confirmation
The LUI/Agent bridge SHALL require explicit user confirmation before dispatching any action whose definition requires confirmation or whose audit level is sensitive or critical.

#### Scenario: Confirm critical action
- **WHEN** a LUI candidate would approve, reject, submit, refund, retry a hard closure effect, or invoke an external state-changing integration
- **THEN** the UI presents a confirmation using registered action metadata and dispatches only after the user confirms

#### Scenario: Silent execution blocked
- **WHEN** an Agent attempts to execute a critical registered action without confirmation authorization
- **THEN** the bridge rejects the action before execution

### Requirement: LUI and Agent actions use actor and source attribution
Actions dispatched from LUI or Agent sources SHALL record the human user, agent identity when present, source, reason, trace id, and correlation id in the Global Action context.

#### Scenario: LUI action attributed to user
- **WHEN** a confirmed LUI candidate is dispatched
- **THEN** the resulting Global Action records source `LUI`, the authenticated user actor, and the natural-language correlation id

#### Scenario: Agent action attributed to agent and user
- **WHEN** an Agent performs an authorized follow-up on behalf of a user or workflow
- **THEN** the resulting Global Action records the agent identity, initiating actor or workflow reference, source `AGENT`, trace id, and reason summary

### Requirement: Generated UI uses registered components
When LUI or Agent output renders confirmation forms, approval controls, action previews, or result cards, the frontend SHALL instantiate only registered components with schema-validated props.

#### Scenario: Render registered confirmation component
- **WHEN** a LUI candidate needs user confirmation
- **THEN** the frontend renders the registered confirmation component and validates props before display

#### Scenario: Reject dynamic component injection
- **WHEN** LUI or Agent output attempts to inject an unregistered component, raw HTML handler, script, or dynamic tag
- **THEN** the frontend rejects the render request and records a safe diagnostic
