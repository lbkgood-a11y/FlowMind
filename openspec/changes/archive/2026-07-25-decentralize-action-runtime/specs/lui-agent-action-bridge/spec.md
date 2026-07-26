## MODIFIED Requirements

### Requirement: Agent tool calls are constrained to registered actions
Agent tools that create business side effects SHALL dispatch only registered owner-hosted Global Actions and SHALL NOT invoke arbitrary APIs, SQL, scripts, URLs, dynamic classes, or free Prompt execution definitions.

#### Scenario: Registered tool action
- **WHEN** an Agent tool call requests `process.closure.effect.retry`
- **THEN** the bridge validates that the action type is registered by the resolved owner service before creating or dispatching a Global Action

#### Scenario: Unregistered tool action
- **WHEN** an Agent tool call requests an unregistered operation
- **THEN** the bridge rejects the tool call and records a bounded diagnostic reason

### Requirement: LUI and Agent payloads are schema validated
The LUI/Agent bridge SHALL validate candidate payloads against the target owner's ActionDefinition schema before confirmation or execution.

#### Scenario: Candidate passes schema
- **WHEN** an Agent follow-up candidate includes all required payload fields
- **THEN** the bridge marks the candidate schema-valid and allows authorization evaluation through the owner-hosted validation contract

#### Scenario: Candidate fails schema
- **WHEN** a LUI candidate omits a required target id
- **THEN** the bridge rejects or asks for missing input and does not create or dispatch a Global Action

### Requirement: LUI and Agent actions use actor and source attribution
Actions dispatched from LUI or Agent sources SHALL record the human user, agent identity when present, source, reason, trace id, and correlation id in the Global Action context, and the owning service SHALL preserve that attribution in audit and timeline events.

#### Scenario: LUI action attributed to user
- **WHEN** a confirmed LUI candidate is dispatched
- **THEN** the owner-hosted Global Action records source `LUI`, the authenticated user actor, and the natural-language correlation id

#### Scenario: Agent action attributed to agent and user
- **WHEN** an Agent performs an authorized follow-up on behalf of a user or workflow
- **THEN** the owner-hosted Global Action records the agent identity, initiating actor or workflow reference, source `AGENT`, trace id, and reason summary

## ADDED Requirements

### Requirement: LUI and Agent action routing resolves owner endpoints
The LUI/Agent bridge SHALL resolve each ActionCandidate to its owning service before validation or dispatch and SHALL use a governed Action Client rather than direct arbitrary owner API calls.

#### Scenario: Leave request routed to lowcode owner
- **WHEN** an Agent proposes a leave form submit-and-launch action whose target owner is `service-lowcode`
- **THEN** the bridge validates and dispatches the candidate through the lowcode owner-hosted action endpoint

#### Scenario: Unknown owner rejected
- **WHEN** an Agent proposes a candidate without a resolvable owner service or registered action definition
- **THEN** the bridge rejects the candidate before confirmation or execution

### Requirement: LUI and Agent no longer depend on service-action availability
The LUI/Agent bridge SHALL NOT require a central `service-action` process to be running for supported owner-hosted actions.

#### Scenario: Service-action unavailable
- **WHEN** `service-action` is not deployed after migration and the Agent dispatches a supported lowcode or workflow action
- **THEN** the bridge completes validation and dispatch through the owning service and returns the normalized action result

