## ADDED Requirements

### Requirement: Designer uses business-object-first workflow
The process designer SHALL require selecting a published business object before configuring forms, permissions, launch policy, closure policy, and Agent follow-up.

#### Scenario: Create expense report process
- **WHEN** a designer starts creating a new process
- **THEN** the designer first selects a business object such as "报销单" and then receives catalog-driven options for the remaining configuration steps

### Requirement: Designer prefers selection over free-form technical input
The designer SHALL use templates, selectors, parameter forms, field pickers, and condition builders instead of requiring users to type technical structures.

#### Scenario: Configure business ID source
- **WHEN** a designer configures the business ID source
- **THEN** the designer selects from available form fields or context sources rather than typing `form.expenseReportId`

#### Scenario: Configure condition branch
- **WHEN** a designer creates a high-value expense branch
- **THEN** the designer can configure "报销金额 大于 5000" through a condition builder rather than writing an expression string

### Requirement: Designer hides editable technical structures
The designer SHALL NOT require users to edit JSON, connector identifiers, executor keys, URL, SQL, scripts, permission codes, or Prompt text to complete business closure configuration.

#### Scenario: Read technical preview
- **WHEN** a designer opens the technical preview
- **THEN** the system displays generated codes and JSON as read-only diagnostic information

#### Scenario: Attempt to edit generated JSON
- **WHEN** a designer attempts to change the generated closure JSON through the normal configuration flow
- **THEN** the designer blocks editing and directs the user back to business selectors

### Requirement: Designer visualizes process, document state, closure actions, and permissions
The designer SHALL provide four MVP visualizations: process graph, document status graph, closure action chain, and permission matrix.

#### Scenario: Show document status graph
- **WHEN** an expense process configures launch status and approved/rejected outcome statuses
- **THEN** the designer displays the business document state path from allowed launch statuses to IN_APPROVAL and final statuses

#### Scenario: Show closure action chain
- **WHEN** APPROVED maps to status update, event, notification, and Agent follow-up
- **THEN** the designer displays the chain of actions in business language under the APPROVED result

#### Scenario: Show permission matrix
- **WHEN** permissions are configured for launch, view, task handling, closure retry, and Agent follow-up
- **THEN** the designer displays a matrix using business action display names and actor groups

### Requirement: Designer provides completion checks and actionable validation
The designer SHALL show completion status and validation errors in business language and locate errors to steps, nodes, fields, permissions, or effects.

#### Scenario: Missing approved status
- **WHEN** APPROVED outcome has an enabled status update effect without a target business status
- **THEN** the designer reports that "审批通过 -> 更新业务状态" is missing a target status and links to that configuration item

#### Scenario: Missing permission mapping
- **WHEN** launch policy references a business action without an RBAC permission mapping
- **THEN** the designer reports the missing permission mapping in the permission matrix

### Requirement: Instance detail visualizes closure runtime state
The runtime UI SHALL show approval result separately from business closure status and effect execution details.

#### Scenario: Partial closure failure
- **WHEN** approval is APPROVED but one soft effect fails
- **THEN** instance detail shows approval result APPROVED, closure status PARTIAL_FAILED or FAILED, successful effects, failed effects, failure reason, and retry action when authorized
