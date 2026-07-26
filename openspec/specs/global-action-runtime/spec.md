# global-action-runtime Specification

## Purpose
TBD - created by archiving change global-action-runtime-refactor. Update Purpose after archive.
## Requirements
### Requirement: Business-changing operations use Global Action envelope
The system SHALL require every operation that mutates business state, triggers workflow, invokes an external side effect, requires retry or compensation, or requires business audit to be submitted as a Global Action envelope.

#### Scenario: Submit GUI business action
- **WHEN** a user submits an approval operation from a GUI page
- **THEN** the frontend submits a Global Action containing action type, GUI source, actor, target, payload, trace context, and idempotency key

#### Scenario: Keep local UI events local
- **WHEN** a user opens a drawer, switches a tab, refreshes a table, or edits a local input value before submission
- **THEN** the frontend handles the event locally and does not create a Global Action

### Requirement: Action definitions are registered before execution
The system SHALL execute only action types that have a registered ActionDefinition in the owning service with owner service, target type, payload schema, permission requirement, guard requirements, execution mode, audit level, and result schema.

#### Scenario: Registered action accepted
- **WHEN** a request uses action type `process.task.approve` and `service-workflow-engine` has registered its ActionDefinition
- **THEN** the owner-hosted Action Runtime loads the definition before validation, authorization, idempotency, and local execution

#### Scenario: Unregistered action rejected
- **WHEN** a request uses an action type that has no registered ActionDefinition in the resolved owner service
- **THEN** the owner-hosted Action Runtime rejects the request before executing any business logic

### Requirement: Action payloads are schema validated
The Action Runtime SHALL validate every action payload against the registered payload schema before authorization and execution.

#### Scenario: Valid payload
- **WHEN** a `process.task.transfer` action includes a valid target assignee payload
- **THEN** the Action Runtime continues to authorization and records the validation status

#### Scenario: Invalid payload
- **WHEN** an action payload is missing a required field or includes unsupported parameters
- **THEN** the Action Runtime rejects the action with structured field errors and does not dispatch to the owner service

### Requirement: Action lifecycle is normalized
The Action Runtime SHALL expose action status using the normalized lifecycle `CREATED`, `VALIDATING`, `REJECTED`, `AUTHORIZED`, `ACCEPTED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `COMPENSATING`, and `COMPENSATED`.

#### Scenario: Synchronous success
- **WHEN** an owner service completes a short action during the request
- **THEN** the Action Runtime returns `SUCCEEDED` with domain-specific result data

#### Scenario: Asynchronous acceptance
- **WHEN** an action starts a Temporal workflow or long-running owner execution
- **THEN** the Action Runtime returns `ACCEPTED` or `RUNNING` with an action id and execution reference

#### Scenario: Domain status preserved as data
- **WHEN** a lowcode form workflow operation leaves a form instance in `PENDING_WORKFLOW`
- **THEN** the Global Action status uses the normalized lifecycle and places the form workflow status in result data

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

### Requirement: Action payload and audit data are redacted
The Action Runtime SHALL redact configured sensitive payload paths and SHALL NOT persist secrets, credentials, raw sensitive fields, or raw Prompt content in action records, events, or audit summaries.

#### Scenario: Sensitive payload redacted
- **WHEN** an action payload contains a field declared sensitive by the ActionDefinition
- **THEN** the persisted action summary stores a redacted value and the owner service receives the original payload only for execution

#### Scenario: Secret value blocked from audit
- **WHEN** an integration action includes an authorization header or credential reference
- **THEN** the action audit stores only the reference or redacted marker and never stores the resolved secret

### Requirement: Frontend business operations dispatch Global Actions
The frontend SHALL invoke business-changing operations through an Action Client that resolves the owning service and SHALL handle confirmation, submission, status, errors, refresh, and retry through the normalized action lifecycle.

#### Scenario: Dispatch from lowcode runtime page
- **WHEN** a user submits a published lowcode application create action
- **THEN** the page calls the Action Client, and the Action Client dispatches the Global Action to the lowcode owner-hosted runtime instead of a central `service-action` endpoint

#### Scenario: Dispatch from task dialog
- **WHEN** a user approves, rejects, transfers, or adds a sign task
- **THEN** the task dialog dispatches the matching Global Action through the Action Client and refreshes task data after the owner-hosted action succeeds

### Requirement: Legacy business mutation paths are removed after migration
After all operation families are migrated, the system SHALL remove redundant local action DTOs, direct frontend business API wrappers, duplicate status utilities, and compatibility endpoints that bypass Global Action.

#### Scenario: Compatibility adapter during migration
- **WHEN** a legacy endpoint remains during the migration window
- **THEN** it submits an equivalent Global Action internally and records the action id in the response or audit context

#### Scenario: Bypass rejected after removal
- **WHEN** code attempts to add a new business-changing frontend call or backend endpoint that does not dispatch a Global Action
- **THEN** automated tests or architecture checks fail the change

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

### Requirement: Lifecycle actions use Global Action while draft edits remain domain APIs
The system SHALL route lifecycle and side-effect operations through Global Action while allowing query, local UI, and draft-edit operations to remain in owner domain APIs.

#### Scenario: Submit document
- **WHEN** a user submits a purchase order for approval
- **THEN** the frontend dispatches a Global Action and does not call the SCM submit endpoint directly

#### Scenario: Save draft document
- **WHEN** a user saves editable draft fields without changing the document lifecycle state
- **THEN** the frontend calls the SCM draft-save API or management operation wrapper and does not create a Global Action

### Requirement: Owner services implement standard owner-hosted action execution
Every owner service that exposes Business Actions SHALL implement standard owner-hosted action definition, validation, candidate dispatch, and direct dispatch endpoints and return standard `common-action` results.

#### Scenario: Dispatch to owner service
- **WHEN** an Action Client dispatches `wms.inboundOrder.confirmReceive`
- **THEN** `service-wms` receives the standard action request at its owner-hosted endpoint and returns `GlobalActionResult` with status, message, data, errors, retryable flag, and owner execution reference

#### Scenario: OpenAPI owner exposes management action endpoints
- **WHEN** an Action Client needs OpenAPI action definitions, candidate validation, candidate dispatch, or direct dispatch
- **THEN** `service-openapi` exposes the standard owner-hosted contracts under `/api/v1/openapi/management/actions/**`

#### Scenario: Unsupported action
- **WHEN** an owner service receives an action type that its local registry no longer supports
- **THEN** it returns a structured failed or rejected result and does not silently ignore the request

### Requirement: Action results include frontend refresh metadata
Global Action results SHALL include or expose metadata that allows the frontend to refresh affected page scopes consistently after action completion.

#### Scenario: Refresh document after success
- **WHEN** a submit action succeeds
- **THEN** the result identifies affected refresh scopes such as document, list, timeline, actions, workflow, attachments, or related tables

#### Scenario: Refresh running workflow action
- **WHEN** an action starts a long-running workflow and returns accepted or running
- **THEN** the result includes action id and owner execution reference so the frontend can subscribe to action events and refresh scopes when terminal status is reached

### Requirement: Action target metadata carries document identity
Global Actions for business documents SHALL use stable target metadata including target type, target id, owner service, tenant id, version when available, and bounded attributes required for audit and timeline correlation.

#### Scenario: Action targets a document
- **WHEN** a user approves an SCM purchase order
- **THEN** the action target identifies the purchase order object type, document id, owner service, tenant id, and version so audit, authorization, and timeline queries can correlate the operation

### Requirement: Frontend migrated lifecycle operations remain Action-routed
The system SHALL ensure second-wave migrated frontend pages continue routing lifecycle and side-effect operations through the Action Client while non-lifecycle query, management, and draft operations remain lightweight.

#### Scenario: Process task operation stays Action-routed
- **WHEN** a migrated process task page performs approve, reject, transfer, add-sign, retry, or workflow signal operations
- **THEN** the frontend dispatches the matching Global Action through the Action Client and refreshes the returned scopes

#### Scenario: Lowcode lifecycle operation stays Action-routed
- **WHEN** a migrated lowcode runtime page performs submit, workflow retry, or another lifecycle transition
- **THEN** the frontend uses the Action Client and does not call a legacy lowcode lifecycle mutation wrapper directly

#### Scenario: OpenAPI runtime action stays Action-routed
- **WHEN** a migrated OpenAPI page starts orchestration, cancels execution, signals callback, or invokes a state-changing runtime operation
- **THEN** the frontend uses the Action Client or an owner wrapper that submits an equivalent owner-hosted OpenAPI Global Action and exposes the normalized result

#### Scenario: OpenAPI runtime adapter unavailable
- **WHEN** an OpenAPI state-changing action is requested before a governed runtime adapter is enabled for the route and environment
- **THEN** `service-openapi` fails closed with a structured rejected or retryable result instead of bypassing the owner-hosted Action Runtime or invoking an arbitrary connector URL

#### Scenario: Management CRUD stays lightweight
- **WHEN** a migrated system or operations page creates or edits platform configuration that is classified as management CRUD
- **THEN** the frontend may call the existing owner API but still uses the standard operation layout, feedback, and refresh pattern
