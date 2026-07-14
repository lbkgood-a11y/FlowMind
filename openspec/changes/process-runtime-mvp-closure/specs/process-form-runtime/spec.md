## ADDED Requirements

### Requirement: Process start forms are rendered from published Schema snapshots
The frontend SHALL render the selected published process package form from its snapshotted JSON Schema and UI Schema using a fixed component registry.

#### Scenario: Render an expense form
- **WHEN** a user selects the published expense process
- **THEN** the frontend renders amount, reason, department, and remark controls according to the published form snapshot without requiring raw JSON input

### Requirement: Dynamic form components are sandboxed
The frontend SHALL instantiate only registered form components with validated properties and SHALL NOT use `eval()`, arbitrary HTML injection, or unregistered dynamic component names.

#### Scenario: UI Schema requests an unknown widget
- **WHEN** a process package references a widget that is not in the component registry
- **THEN** publication is rejected and the frontend does not instantiate the unknown widget

### Requirement: Form data is validated on client and server
The system SHALL validate submitted process form data against the published JSON Schema in the frontend and again in `service-workflow-engine` before creating an instance.

#### Scenario: Required field is missing in the browser
- **WHEN** a user submits an expense form without a required reason
- **THEN** the frontend displays a field validation error and does not send the start request

#### Scenario: Invalid client bypasses browser validation
- **WHEN** a direct API client submits data that violates the published Schema
- **THEN** the workflow service rejects the request and does not create a process instance or start a Temporal Workflow

### Requirement: MVP field types have deterministic mappings
The form runtime SHALL support deterministic mappings for string, textarea, number, money, integer, boolean, enum/select, and date fields.

#### Scenario: Render a money field
- **WHEN** the JSON Schema defines a numeric amount and the UI Schema selects the money widget
- **THEN** the frontend renders the registered money control and submits a numeric value

### Requirement: Designer validates process and form configuration before publish
The process designer SHALL display actionable validation errors for graph, participant, condition, Schema, and widget problems before invoking the publish API.

#### Scenario: Approval node lacks participants
- **WHEN** a user attempts to publish a design containing an approval node without a participant assignment
- **THEN** the designer identifies that node and blocks publication

### Requirement: Process start uses the latest eligible published version
The system SHALL resolve the latest PUBLISHED version for a process key at start time and SHALL reject stale client version assumptions.

#### Scenario: Client selected a version that was replaced
- **WHEN** a newer version is published after the user opens the start form but before submission
- **THEN** the service returns a version conflict requiring the user to reload the form rather than silently starting with mismatched Schema data
