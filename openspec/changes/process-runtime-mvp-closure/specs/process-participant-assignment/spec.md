## ADDED Requirements

### Requirement: Participants are resolved in Temporal Activities
The system SHALL resolve ROLE, USER, and DEPT assignments through Activity implementations and SHALL NOT perform authentication or organization service I/O inside Workflow code.

#### Scenario: Resolve a role assignment
- **WHEN** an approval node with a ROLE assignment is entered
- **THEN** an Activity queries the authentication service and returns the enabled users assigned to that role

#### Scenario: Resolve a department assignment
- **WHEN** an approval node with a DEPT assignment is entered
- **THEN** an Activity queries the organization service and returns the enabled users belonging to that organization unit

#### Scenario: Resolve a direct user assignment
- **WHEN** an approval node with a USER assignment is entered
- **THEN** an Activity verifies that the configured user exists and is enabled

### Requirement: Node participant snapshots are immutable
The system SHALL persist the resolved participant snapshot when a node is entered, and subsequent role or organization changes SHALL NOT modify tasks already created for that node.

#### Scenario: User leaves a role after task creation
- **WHEN** a role member is removed after an approval task has been created
- **THEN** the existing task candidate snapshot remains unchanged

### Requirement: Approval tasks use candidate authorization
The system SHALL expose a pending approval task only to its snapshotted candidates and SHALL atomically assign the task to the first candidate who performs a valid handling action.

#### Scenario: Candidate views pending task
- **WHEN** a snapshotted candidate requests their pending task list
- **THEN** the unclaimed approval task is included

#### Scenario: Non-candidate attempts approval
- **WHEN** an authenticated user who is not a task candidate attempts to approve or reject the task
- **THEN** the system rejects the operation without changing task or Workflow state

#### Scenario: Two candidates approve concurrently
- **WHEN** two candidates attempt to approve the same unclaimed task concurrently
- **THEN** exactly one candidate claims and completes the task and the other receives an already-claimed result

### Requirement: Countersign tasks are assigned per participant
The system SHALL create one independently assigned task for every countersign participant and SHALL preserve the complete participant set for Workflow vote evaluation.

#### Scenario: Create ALL countersign tasks
- **WHEN** a COUNTERSIGN node configured with ALL resolves three participants
- **THEN** the system creates three assigned pending tasks and requires all three approvals unless a rejection rule terminates the node

### Requirement: Empty participant resolution does not create orphan tasks
The system SHALL NOT create an unowned task when participant resolution returns no enabled users.

#### Scenario: Role has no enabled users
- **WHEN** a ROLE assignment resolves to an empty user set after configured retries
- **THEN** the node is recorded as FAILED, the process instance becomes SUSPENDED, and a diagnostic reason is persisted
