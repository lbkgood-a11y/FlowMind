## ADDED Requirements

### Requirement: Process packages declare business closure policy
The system SHALL allow a process package to declare a business closure policy that binds businessRef, start effects, outcome effects, failure effects, and Agent follow-up effects.

#### Scenario: Configure expense report closure
- **WHEN** a designer configures an expense report process
- **THEN** the process package includes a closure policy that binds the expense report business object and maps APPROVED and REJECTED outcomes to selected business actions

### Requirement: BusinessRef is selected from catalog and form or context sources
The designer SHALL let users choose businessRef ID sources from validated form fields, page context, API input, process context, or fixed values permitted by policy.

#### Scenario: Select form field as business ID
- **WHEN** a designer selects "报销单编号" as the business ID source
- **THEN** the system stores the corresponding internal source in closurePolicy without requiring the designer to type a JSON path

#### Scenario: Missing field blocks publish
- **WHEN** the selected business ID field no longer exists in the published form snapshot
- **THEN** process publication fails with a business-language validation error

### Requirement: Outcome mappings use selectable business statuses and actions
The designer SHALL configure outcome mappings by selecting business statuses, catalog actions, events, notifications, and Agent follow-up actions.

#### Scenario: Approved maps to approved status
- **WHEN** a designer maps APPROVED to "将报销单状态改为 已通过"
- **THEN** the system generates the required effect configuration using catalog status and action codes internally

#### Scenario: Reject arbitrary technical action
- **WHEN** a designer or API client attempts to add an outcome effect using arbitrary URL, SQL, script, dynamic class, or unregistered connector data
- **THEN** publication rejects the effect

### Requirement: Closure policy compiles into immutable ClosurePlan
The system SHALL compile closurePolicy into a ClosurePlan snapshot during process publication.

#### Scenario: Runtime executes snapshot plan
- **WHEN** a process instance reaches APPROVED
- **THEN** the runtime selects effects from the published ClosurePlan snapshot rather than re-reading mutable designer draft data

#### Scenario: Catalog action changes after publish
- **WHEN** a catalog action parameter schema changes after process version 1 is published
- **THEN** version 1 continues to execute the parameter schema captured in its ClosurePlan snapshot

### Requirement: Closure policy supports hard and soft modes
Each closure effect SHALL declare whether it is HARD or ASYNC/SOFT according to the published plan and allowed effect type.

#### Scenario: Hard status update
- **WHEN** an approved outcome has a HARD business status update effect
- **THEN** the process closure does not report business completion until the status update succeeds

#### Scenario: Soft notification failure
- **WHEN** an approved outcome has a soft notification effect and notification delivery fails
- **THEN** the approval outcome remains visible while closure status shows the failed notification effect
