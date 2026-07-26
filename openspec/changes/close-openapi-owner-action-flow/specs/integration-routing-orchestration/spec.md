## ADDED Requirements

### Requirement: Invoke owner-hosted actions from OpenAPI orchestration
The system SHALL support a governed `OWNER_ACTION` orchestration step that dispatches a standard `GlobalActionRequest` to an allow-listed owner service action runtime without treating the owner service as an external connector.

#### Scenario: Dispatch lowcode leave request action
- **WHEN** a published OpenAPI route orchestration receives a canonical leave request payload and executes an `OWNER_ACTION` step targeting `service-lowcode`, action type `lowcode.form.submit`, target type `LOWCODE_FORM`, target id `leave`, and payload containing `appKey=leave`, `actionCode=submitAndLaunch`, and form `data`
- **THEN** the runtime dispatches the action through the owner-hosted action endpoint, propagates tenant, trace, actor, correlation, and idempotency context, and stores the normalized lowcode Global Action result in the execution payload

#### Scenario: Reject unsafe owner action configuration
- **WHEN** an orchestration definition contains an `OWNER_ACTION` step with an unsupported owner service, missing action type, missing target type, invalid JSON pointer, or URL/credential/authorization fields
- **THEN** publication validation fails before the route can be released

#### Scenario: Preserve owner service responsibility
- **WHEN** an `OWNER_ACTION` step is executed
- **THEN** OpenAPI runtime records orchestration attempt evidence while the owner service performs action schema validation, guard evaluation, idempotency enforcement, audit emission, and business state mutation

#### Scenario: Lifecycle readiness accepts owner action implementation
- **WHEN** a route is implemented by a published `OWNER_ACTION` orchestration and an active release without an external connector
- **THEN** OpenAPI lifecycle readiness treats the implementation stage as complete because the route is bound to an owner-hosted action runtime

#### Scenario: Owner action failure follows orchestration policy
- **WHEN** the owner service returns a rejected or failed Global Action result for an `OWNER_ACTION` step
- **THEN** the orchestration marks the step attempt failed with sanitized owner evidence and applies the step failure policy, including compensation when configured
