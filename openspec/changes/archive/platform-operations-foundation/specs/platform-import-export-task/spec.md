## ADDED Requirements

### Requirement: Async import export task lifecycle
The system SHALL manage asynchronous import and export tasks with status, progress, parameters, and result metadata.

#### Scenario: Create export task
- **WHEN** an authorized user creates an export task with business type and query parameters
- **THEN** the system creates a pending task and returns the task identifier

#### Scenario: Complete task
- **WHEN** a task processor completes a task successfully
- **THEN** the system marks the task as success, sets progress to 100, and records result file metadata when present

#### Scenario: Fail task
- **WHEN** a task processor fails a task
- **THEN** the system marks the task as failed and records a failure reason visible to authorized users

### Requirement: Import result details
The system SHALL retain import success count, failure count, and failure detail file when an import task finishes.

#### Scenario: Import with partial failures
- **WHEN** an import task finishes with invalid rows
- **THEN** the system records success count, failure count, and a downloadable failure detail file

### Requirement: Task query and cancellation
The system SHALL allow authorized users to query their own tasks and authorized administrators to query all tasks.

#### Scenario: Query own tasks
- **WHEN** an authorized user queries import export tasks without global management permission
- **THEN** the system returns only tasks created by that user

#### Scenario: Cancel pending task
- **WHEN** an authorized user cancels their pending or running task
- **THEN** the system marks the task as cancelled if the processor has not completed it

### Requirement: Import export authorization
The system MUST enforce separate permissions for import, export, cancel, and result download operations.

#### Scenario: Missing export permission
- **WHEN** a user without export permission attempts to create an export task
- **THEN** the system rejects the operation and the frontend does not render the export button
