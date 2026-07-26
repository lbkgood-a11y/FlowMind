## MODIFIED Requirements

### Requirement: Action definitions are registered before execution
The system SHALL execute only action types that have a registered ActionDefinition in the owning service with owner service, target type, payload schema, permission requirement, guard requirements, execution mode, audit level, and result schema.

#### Scenario: Registered action accepted
- **WHEN** a request uses action type `process.task.approve` and `service-workflow-engine` has registered its ActionDefinition
- **THEN** the owner-hosted Action Runtime loads the definition before validation, authorization, idempotency, and local execution

#### Scenario: Unregistered action rejected
- **WHEN** a request uses an action type that has no registered ActionDefinition in the resolved owner service
- **THEN** the owner-hosted Action Runtime rejects the request before executing any business logic

### Requirement: Action idempotency prevents duplicate side effects
The Action Runtime SHALL enforce idempotency for state-changing actions using tenant, action type, and idempotency key inside the owning service before executing side effects.

#### Scenario: First submission dispatches
- **WHEN** a user submits a new state-changing action with an unused idempotency key
- **THEN** the owning service records or reserves the action and executes the registered owner operation

#### Scenario: Duplicate submission reuses result
- **WHEN** the same tenant submits the same action type with the same idempotency key
- **THEN** the owning service returns the existing action status and result instead of dispatching another side effect

### Requirement: Owner services execute business state changes
The Action Runtime SHALL keep business state changes, action execution ownership, idempotency, status transition, event emission, and audit correlation inside the owning service while shared modules provide common contracts and helpers.

#### Scenario: Lowcode owner executes form action
- **WHEN** a `lowcode.form.submit` action targets a lowcode form instance
- **THEN** `service-lowcode` performs the form validation, storage, workflow launch coordination, action status update, and audit event emission through its owner-hosted runtime

#### Scenario: Workflow owner executes task signal
- **WHEN** a `process.task.approve` action targets a workflow task
- **THEN** `service-workflow-engine` performs task guard checks, operation recording, Temporal signal coordination, action status update, and audit event emission through its owner-hosted runtime

### Requirement: Action runtime records events and results
The Action Runtime SHALL expose action execution records, status transitions, result summaries, error summaries, owner execution references, and emitted action events from the owning service or a read-model projection rather than requiring a central execution facade.

#### Scenario: Query action status
- **WHEN** a caller queries an action by action id
- **THEN** the system returns actor, source, target, status, trace id, correlation id, idempotency key, result summary, and bounded error details from the owner runtime or authorized projection

#### Scenario: Subscribe action events
- **WHEN** the frontend subscribes to action events for a submitted action
- **THEN** the system streams or exposes ordered status events from the owner runtime or projection until the action reaches a terminal status

### Requirement: Frontend business operations dispatch Global Actions
The frontend SHALL invoke business-changing operations through an Action Client that resolves the owning service and SHALL handle confirmation, submission, status, errors, refresh, and retry through the normalized action lifecycle.

#### Scenario: Dispatch from lowcode runtime page
- **WHEN** a user submits a published lowcode application create action
- **THEN** the page calls the Action Client, and the Action Client dispatches the Global Action to the lowcode owner-hosted runtime instead of a central `service-action` endpoint

#### Scenario: Dispatch from task dialog
- **WHEN** a user approves, rejects, transfers, or adds a sign task
- **THEN** the task dialog dispatches the matching Global Action through the Action Client and refreshes task data after the owner-hosted action succeeds

### Requirement: Owner services synchronize ActionDefinitions dynamically
The Action Runtime SHALL support discovery of ActionDefinitions from owner services and SHALL allow optional versioned definition snapshots for diagnostics without requiring a central execution service to store definitions before runtime execution.

#### Scenario: Owner registers document action
- **WHEN** `service-scm` exposes `scm.purchaseOrder.submit`
- **THEN** the owning service publishes or exposes the ActionDefinition with owner service, target type, payload schema, result schema, permission, guard metadata, execution mode, audit level, confirmation, and version metadata

#### Scenario: Missing registered definition
- **WHEN** a caller submits an action type that has not been exposed or registered by the resolved owner service
- **THEN** the owner-hosted Action Runtime rejects the action before authorization or execution

### Requirement: Action candidates support batch availability validation
The Action Runtime SHALL provide owner-hosted batch candidate availability contracts that validate multiple candidate actions for a page or document and return frontend-renderable availability metadata.

#### Scenario: Validate document actions
- **WHEN** a document page requests availability for submit, approve, reject, cancel, and close actions
- **THEN** the Action Client routes candidates to their owning services and returns one result per candidate with action type, visible flag, enabled flag, disabled reason, validation errors, confirmation requirement, danger flag, execution mode, and normalized target metadata

#### Scenario: Candidate denied by payload schema
- **WHEN** a candidate payload is missing a required field
- **THEN** the owning service marks the candidate as not dispatchable and includes structured field errors suitable for frontend display

### Requirement: Owner services implement standard internal action execution
Every owner service that exposes Business Actions SHALL implement standard owner-hosted action validation and dispatch endpoints and return standard `common-action` results.

#### Scenario: Dispatch to owner service
- **WHEN** an Action Client dispatches `wms.inboundOrder.confirmReceive`
- **THEN** `service-wms` receives the standard action request at its owner-hosted endpoint and returns `GlobalActionResult` with status, message, data, errors, retryable flag, and owner execution reference

#### Scenario: Unsupported action
- **WHEN** an owner service receives an action type that its local registry no longer supports
- **THEN** it returns a structured failed or rejected result and does not silently ignore the request

