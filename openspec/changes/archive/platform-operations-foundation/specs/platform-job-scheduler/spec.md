## ADDED Requirements

### Requirement: Job definition management
The system SHALL allow authorized administrators to create, edit, enable, disable, and delete scheduled job definitions.

#### Scenario: Create job definition
- **WHEN** an authorized administrator creates a job with code, name, handler, cron expression, and parameters
- **THEN** the system persists the job definition in disabled state unless explicitly enabled

#### Scenario: Disable job
- **WHEN** an authorized administrator disables an enabled job
- **THEN** the system prevents future scheduled executions while preserving execution history

### Requirement: Manual job trigger
The system SHALL allow authorized administrators to manually trigger enabled or disabled jobs.

#### Scenario: Trigger job manually
- **WHEN** an authorized administrator manually triggers a job
- **THEN** the system creates an execution record and runs the job handler once

### Requirement: Job execution logging
The system SHALL record execution logs for scheduled and manual job runs.

#### Scenario: Successful execution
- **WHEN** a job run completes successfully
- **THEN** the system records start time, end time, duration, status, trigger type, and result summary

#### Scenario: Failed execution
- **WHEN** a job run throws an error
- **THEN** the system records failure status and error message without deleting the job definition

### Requirement: Job scheduler authorization
The system MUST enforce menu, API, and button permissions for job management and manual trigger operations.

#### Scenario: Missing trigger permission
- **WHEN** a user without job trigger permission attempts to trigger a job
- **THEN** the system rejects the operation and the frontend does not render the trigger button
