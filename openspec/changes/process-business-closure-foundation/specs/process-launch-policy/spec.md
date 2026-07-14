## ADDED Requirements

### Requirement: Processes support existing-document launch
The system SHALL allow a process to launch from an existing business document by binding a businessRef supplied by page context or API input.

#### Scenario: Launch existing expense report
- **WHEN** an authorized user submits an existing expense report in an allowed business status
- **THEN** the system binds the process instance to that expense report businessRef and starts the configured process version

#### Scenario: Reject disallowed document status
- **WHEN** a user submits an existing expense report whose status is not allowed by the launch policy
- **THEN** the system rejects the launch before creating a process instance or Temporal Workflow execution

### Requirement: Processes support create-and-launch
The system SHALL allow a process to create a new business document through a registered executor before starting the process.

#### Scenario: Create expense report then launch
- **WHEN** a user submits a create-and-launch form for an expense report
- **THEN** the system invokes the registered create-document executor, receives the new businessRef, and starts the process with that businessRef

#### Scenario: Creation failure blocks launch
- **WHEN** the registered create-document executor fails
- **THEN** the system does not create a process instance and returns a diagnostic launch failure

### Requirement: Launch validates business permission and process permission
The system SHALL validate both business object permission and process launch policy before starting a process.

#### Scenario: Missing submit permission
- **WHEN** a user lacks the business object's submit permission
- **THEN** the system rejects the launch even if the process definition itself is otherwise available

#### Scenario: Missing process launch rule
- **WHEN** a user is not allowed by the process launch policy
- **THEN** the system rejects the launch even if the user has the business object submit permission

### Requirement: Launch can execute hard StartEffect
The system SHALL support StartEffect actions that run during launch, including hard effects that must succeed before the process starts.

#### Scenario: Update document to in approval
- **WHEN** an expense report process launch configures a hard StartEffect to update status to IN_APPROVAL
- **THEN** the process starts only after the status update executor succeeds

#### Scenario: Hard StartEffect fails
- **WHEN** the hard StartEffect fails after retries
- **THEN** launch fails without silently leaving a process instance that appears active

### Requirement: Launch is idempotent
The system SHALL identify launch attempts with an idempotency key so repeated submissions cannot create duplicate business documents or process instances.

#### Scenario: Retry create-and-launch request
- **WHEN** the same create-and-launch idempotency key is submitted more than once
- **THEN** the system returns the original launch result and does not create a second business document or process instance
