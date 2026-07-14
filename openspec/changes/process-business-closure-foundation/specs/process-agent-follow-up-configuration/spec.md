## ADDED Requirements

### Requirement: Agent follow-up actions are catalog registered
The system SHALL expose Agent follow-up actions through the business object catalog with display name, action code, executorKey, parameter schema, and required permission.

#### Scenario: List expense agent actions
- **WHEN** a designer configures APPROVED follow-up for expense report
- **THEN** the designer can select registered actions such as risk check or payment preparation summary from the expense report catalog

### Requirement: Agent follow-up forbids free Prompt configuration
The designer SHALL NOT allow free-form Prompt text or arbitrary tool-call JSON as the execution definition for Agent follow-up.

#### Scenario: Prompt editing blocked
- **WHEN** a designer tries to define a custom Prompt for an Agent follow-up action
- **THEN** the designer blocks the custom Prompt and requires selecting a pre-registered Agent action with validated parameters

### Requirement: Agent follow-up parameters use schemas and selectors
The designer SHALL render Agent follow-up parameters from registered parameter schemas and selectable form/context fields.

#### Scenario: Configure risk check amount source
- **WHEN** an expense risk check requires an amount field
- **THEN** the designer lets the user select "报销金额" from form fields and validates the selected field type

### Requirement: Agent follow-up requires authorization
The system SHALL verify the configured business permission before publishing or executing an Agent follow-up action.

#### Scenario: Missing agent permission mapping
- **WHEN** a selected Agent follow-up action has no valid permission mapping in the business object catalog
- **THEN** process publication fails with a business-language validation error

#### Scenario: Unauthorized manual trigger
- **WHEN** a user without Agent follow-up permission attempts to manually retry or trigger an Agent follow-up effect
- **THEN** the system rejects the operation

### Requirement: Agent follow-up execution is visible as closure effect
The system SHALL execute Agent follow-up as a closure effect and persist status, result, error, traceId, and retry metadata.

#### Scenario: Agent follow-up succeeds
- **WHEN** an APPROVED expense report triggers a registered payment summary Agent follow-up
- **THEN** the effect records SUCCEEDED status and stores the Agent result summary for instance detail display

#### Scenario: Agent follow-up fails softly
- **WHEN** an Agent follow-up effect fails
- **THEN** the approval result remains visible, the effect records FAILED status, and authorized users can retry according to policy
