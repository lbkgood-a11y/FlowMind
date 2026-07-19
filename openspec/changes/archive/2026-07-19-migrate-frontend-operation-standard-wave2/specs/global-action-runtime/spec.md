## ADDED Requirements

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
- **THEN** the frontend uses the Action Client or an owner wrapper that submits an equivalent Global Action and exposes the normalized result

#### Scenario: Management CRUD stays lightweight
- **WHEN** a migrated system or operations page creates or edits platform configuration that is classified as management CRUD
- **THEN** the frontend may call the existing owner API but still uses the standard operation layout, feedback, and refresh pattern
