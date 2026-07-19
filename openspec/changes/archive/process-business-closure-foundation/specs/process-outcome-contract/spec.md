## ADDED Requirements

### Requirement: Terminal business results create ProcessOutcome
The system SHALL create a unique ProcessOutcome when a process instance reaches a terminal or blocked business result state.

#### Scenario: Approval creates approved outcome
- **WHEN** an expense report process completes with approval
- **THEN** the system creates one ProcessOutcome with outcome status APPROVED

#### Scenario: Rejection creates rejected outcome
- **WHEN** an expense report process is rejected and terminated
- **THEN** the system creates one ProcessOutcome with outcome status REJECTED or TERMINATED according to the process result mapping

### Requirement: ProcessOutcome contains process and business references
Each ProcessOutcome SHALL include processRef, businessRef, outcome, actorContext, traceId, and creation timestamp.

#### Scenario: Outcome for bound expense report
- **WHEN** a process bound to expense_report:ER001 reaches APPROVED
- **THEN** the ProcessOutcome contains the process key, process version, process instance ID, business type expense_report, business ID ER001, outcome status APPROVED, initiator, last operator, tenant, and trace context

### Requirement: ProcessOutcome creation is idempotent
The system SHALL prevent duplicate ProcessOutcome rows for the same process instance and outcome version.

#### Scenario: Duplicate terminal event
- **WHEN** the same terminal workflow transition is delivered more than once
- **THEN** the system returns or reuses the existing ProcessOutcome instead of creating a duplicate

### Requirement: ProcessOutcome is the contract for business, data, and AI consumers
Business closure execution, data event publication, and Agent follow-up SHALL consume ProcessOutcome or derived Closure events instead of relying on Workflow internal state.

#### Scenario: Data pipeline consumes outcome event
- **WHEN** a ProcessOutcome is created
- **THEN** the system makes an outcome event available for data consumers with the outcome payload and trace context

#### Scenario: Agent follow-up consumes outcome
- **WHEN** an APPROVED outcome matches a configured Agent follow-up
- **THEN** the Agent follow-up receives the standardized ProcessOutcome payload rather than ad hoc task table fields

### Requirement: Closure failure can create a visible business result
The system SHALL represent hard closure failure with a visible CLOSURE_FAILED or SUSPENDED business result rather than hiding it behind a completed process status.

#### Scenario: Hard outcome effect fails
- **WHEN** a hard OutcomeEffect fails after configured retries
- **THEN** the system marks the closure as failed and exposes a CLOSURE_FAILED or SUSPENDED result according to the published plan
