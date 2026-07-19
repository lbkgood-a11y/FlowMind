## ADDED Requirements

### Requirement: Authorization decisions support action availability semantics
Authorization decisions used for frontend action availability SHALL provide visible, enabled, disabled reason, evidence, policy versions, and guard requirement metadata suitable for rendering consistent business action controls.

#### Scenario: Action denied but visible
- **WHEN** a user can view a document but lacks permission to approve it
- **THEN** the availability result can mark the approve action visible but disabled with a backend-provided disabled reason

#### Scenario: Action hidden
- **WHEN** an action is not applicable to the user's role or page context
- **THEN** the availability result marks the action not visible so the frontend omits it from primary, row, and more-action surfaces

### Requirement: Central authorization composes with owner-service state guards
The system SHALL compose central authorization decisions with owner-service guard results for state-sensitive action availability and execution.

#### Scenario: Permission allowed but state guard denied
- **WHEN** central authorization allows `SUBMIT` but the owner service detects the document is already submitted
- **THEN** the final candidate availability is disabled or rejected with the owner guard reason and the document state is not changed

#### Scenario: Permission and guard allowed
- **WHEN** central authorization allows `APPROVE` and the owner service confirms the task or document is actionable by the current actor
- **THEN** the final action availability is enabled and dispatchable

### Requirement: Business permissions use semantic resource and action codes
Business document permissions SHALL use semantic resource and action codes rather than relying only on URL permission strings.

#### Scenario: Purchase order action permission
- **WHEN** SCM registers purchase order permissions
- **THEN** it uses stable semantic codes for actions such as view, create, edit, submit, approve, reject, cancel, close, and export

#### Scenario: Legacy management endpoint
- **WHEN** a system management page uses existing URL permissions
- **THEN** the existing permission behavior remains valid during migration unless the operation is reclassified as a business document action

### Requirement: Field decisions drive frontend rendering and backend enforcement
Field authorization decisions SHALL drive frontend visible/editable/required/masked field rendering while owner services remain the enforcement source for reads and writes.

#### Scenario: Field readonly
- **WHEN** a field decision marks a document field readonly for the current actor and status
- **THEN** the frontend renders it readonly and the owner service rejects unauthorized write attempts even if a caller bypasses the frontend

#### Scenario: Field hidden
- **WHEN** a field decision marks a sensitive field hidden
- **THEN** the owner service omits it from responses and the frontend does not render the field

### Requirement: Batch decisions support page action rendering
The authorization system SHALL support batch evaluation for page action rendering so a frontend page can request decisions for multiple actions and fields without repeated round trips.

#### Scenario: Document page action batch
- **WHEN** a document page loads actions and fields for a purchase order
- **THEN** the backend can evaluate all relevant resource/action and field decisions in one batch and return stable entries for the frontend to render
