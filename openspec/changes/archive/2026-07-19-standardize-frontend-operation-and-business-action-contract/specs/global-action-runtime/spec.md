## ADDED Requirements

### Requirement: Owner services synchronize ActionDefinitions dynamically
The Action Runtime SHALL support internal synchronization of ActionDefinitions from owner services and SHALL persist versioned definition snapshots for runtime validation and diagnostics.

#### Scenario: Owner registers document action
- **WHEN** `service-scm` synchronizes `scm.purchaseOrder.submit`
- **THEN** `service-action` stores the ActionDefinition with owner service, target type, payload schema, result schema, permission, guard metadata, execution mode, audit level, confirmation, and version metadata

#### Scenario: Missing registered definition
- **WHEN** a caller submits an action type that has not been synchronized or registered
- **THEN** the Action Runtime rejects the action before authorization or owner dispatch

### Requirement: Action candidates support batch availability validation
The Action Runtime SHALL provide a batch candidate availability contract that validates multiple candidate actions for a page or document and returns frontend-renderable availability metadata.

#### Scenario: Validate document actions
- **WHEN** a document page requests availability for submit, approve, reject, cancel, and close actions
- **THEN** the Action Runtime returns one result per candidate with action type, visible flag, enabled flag, disabled reason, validation errors, confirmation requirement, danger flag, execution mode, and normalized target metadata

#### Scenario: Candidate denied by payload schema
- **WHEN** a candidate payload is missing a required field
- **THEN** the candidate result is not dispatchable and includes structured field errors suitable for frontend display

### Requirement: Lifecycle actions use Global Action while draft edits remain domain APIs
The system SHALL route lifecycle and side-effect operations through Global Action while allowing query, local UI, and draft-edit operations to remain in owner domain APIs.

#### Scenario: Submit document
- **WHEN** a user submits a purchase order for approval
- **THEN** the frontend dispatches a Global Action and does not call the SCM submit endpoint directly

#### Scenario: Save draft document
- **WHEN** a user saves editable draft fields without changing the document lifecycle state
- **THEN** the frontend calls the SCM draft-save API or management operation wrapper and does not create a Global Action

### Requirement: Owner services implement standard internal action execution
Every owner service that exposes Business Actions SHALL implement the standard internal action execution endpoint and return the standard owner dispatch response.

#### Scenario: Dispatch to owner service
- **WHEN** `service-action` dispatches `wms.inboundOrder.confirmReceive`
- **THEN** `service-wms` receives `ActionOwnerDispatchRequest` at the standard internal endpoint and returns `ActionOwnerDispatchResponse` with status, message, data, errors, retryable flag, and owner execution reference

#### Scenario: Unsupported action
- **WHEN** an owner service receives a registered action type that its executor no longer supports
- **THEN** it returns a structured failed or rejected owner response and does not silently ignore the request

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
