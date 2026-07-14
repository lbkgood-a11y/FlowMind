## ADDED Requirements

### Requirement: Conditions are evaluated safely and explicitly
The system SHALL evaluate process conditions in a restricted expression environment that only exposes submitted form values and approved read-only functions.

#### Scenario: Route a low-value expense
- **WHEN** an expense process has `amount` equal to 3000 and the condition is `amount > 5000`
- **THEN** the condition evaluates to false and the configured default branch is selected

#### Scenario: Route a high-value expense
- **WHEN** an expense process has `amount` equal to 8000 and the condition is `amount > 5000`
- **THEN** the condition evaluates to true and the finance approval branch is selected

#### Scenario: Reject an unsafe expression
- **WHEN** a process condition attempts class access, method invocation, file access, network access, or another prohibited operation
- **THEN** publication or execution rejects the expression and does not execute the prohibited operation

#### Scenario: Condition execution fails
- **WHEN** a published condition cannot be evaluated against the submitted data
- **THEN** the node fails with a diagnostic error and the system does not silently choose an arbitrary branch

### Requirement: Task operations are idempotent and audited
The system SHALL identify every approval, rejection, transfer, and add-sign command by an operation identifier and SHALL persist an immutable operation record containing operator, action, task linkage, comment, timestamp, and TraceId.

#### Scenario: Retry an approval command
- **WHEN** the same approval operation identifier is submitted more than once
- **THEN** the system returns the original result and emits only one Workflow state transition

### Requirement: Rejection follows declared process semantics
The system SHALL allow rejection to terminate the process or return to an eligible previously visited node, and SHALL reject unknown or unvisited targets.

#### Scenario: Reject and terminate
- **WHEN** an approver rejects a task without a return target
- **THEN** the task becomes REJECTED and the process instance becomes TERMINATED with an audit record

#### Scenario: Reject to a visited node
- **WHEN** an approver selects an eligible previously visited approval node
- **THEN** the current task is completed as REJECTED and the Workflow re-enters the selected node with a new participant snapshot

### Requirement: Transfer preserves task history
The system SHALL mark the source task as TRANSFERRED, create a linked pending task for the target user, and prevent the source task from being handled again.

#### Scenario: Transfer a pending task
- **WHEN** an authorized candidate transfers a pending approval task to an enabled user
- **THEN** the source task becomes TRANSFERRED and a linked pending task is assigned to the target user

### Requirement: MVP add-sign is parallel and mandatory
The system SHALL support parallel mandatory add-sign tasks and SHALL keep the current node active until the original required task and all added tasks are approved or a rejection rule ends the node.

#### Scenario: Add one required approver
- **WHEN** an authorized handler adds an enabled user to the current approval node
- **THEN** the Workflow registers a new pending task and does not leave the node until both required approvals complete

### Requirement: Workflow execution survives Worker restart
The system SHALL use Temporal-compatible deterministic Workflow code and idempotent Activities so that waiting processes resume after the embedded Worker restarts.

#### Scenario: Restart while waiting for approval
- **WHEN** the workflow service restarts while an instance is waiting on a pending approval signal
- **THEN** the Worker replays the Workflow history and the task can still be approved exactly once

### Requirement: Trace context reaches workflow operations
The system SHALL propagate the gateway TraceId through Workflow headers, Activities, task operations, and downstream participant queries.

#### Scenario: Trace an approval request
- **WHEN** an approval request enters through the platform gateway
- **THEN** the same trace context is available in the task operation record and all Activity logs for that operation
