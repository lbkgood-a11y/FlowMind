## ADDED Requirements

### Requirement: Business permissions bind to existing RBAC codes
The business object catalog SHALL map business actions to existing RBAC permission codes.

#### Scenario: Resolve submit permission
- **WHEN** the expense report catalog maps "提交报销" to an RBAC permission code
- **THEN** launch authorization uses that existing RBAC permission code instead of a separate workflow-only permission system

### Requirement: Designers configure permissions through business actions
The process designer SHALL show permission options as business action names and SHALL NOT require configuration users to type permission codes.

#### Scenario: Configure launch permission
- **WHEN** a designer configures who can launch an expense report process
- **THEN** the designer selects business actions such as "提交报销" from the catalog, and the system stores the corresponding permission code internally

#### Scenario: Read-only technical preview
- **WHEN** a designer opens the technical preview
- **THEN** the system may display the resolved permission code as read-only information but does not allow editing it as the primary configuration path

### Requirement: View permission combines business and process rules
The system SHALL enforce process visibility by combining process visibility rules with business object view permissions.

#### Scenario: Applicant views own process
- **WHEN** the applicant has business object view permission and matches the process visibility rule
- **THEN** the applicant can view the process instance, form snapshot, closure status, and history

#### Scenario: Business view permission missing
- **WHEN** a user matches a process visibility rule but lacks the business object's view permission
- **THEN** the system denies access to the process instance details

### Requirement: Task handling still requires candidate authorization
The system SHALL keep candidate or assignee authorization as a separate requirement in addition to business object permissions.

#### Scenario: Non-candidate has approve permission
- **WHEN** a user has a business approval permission but is not a snapshotted candidate or assignee for the current task
- **THEN** the system denies task handling and does not change Workflow state

### Requirement: Closure retry requires business closure permission
The system SHALL require an explicit business object permission to retry failed closure effects or perform audited manual handling.

#### Scenario: Authorized retry
- **WHEN** a user with the business object's retry-closure permission retries a failed closure effect
- **THEN** the system records the retry operation and attempts the effect with the original idempotency key

#### Scenario: Unauthorized retry
- **WHEN** a user without retry-closure permission attempts to retry a failed closure effect
- **THEN** the system rejects the operation and leaves the effect unchanged
