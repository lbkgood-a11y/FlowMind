## ADDED Requirements

### Requirement: Business objects are registered as workflow-platform catalog entries
The system SHALL provide a business object catalog inside the workflow platform for selectable business objects, statuses, forms, permissions, actions, events, and Agent follow-up actions.

#### Scenario: List published business objects
- **WHEN** a process designer opens the business object selection step
- **THEN** the system returns only PUBLISHED business objects effective for the designer's tenant, including display names and business object type codes

#### Scenario: Register expense report catalog seed
- **WHEN** the local development database is migrated
- **THEN** the system includes a PUBLISHED expense report catalog entry with statuses, permissions, form references, closure actions, events, and Agent follow-up actions needed by the MVP acceptance flow

### Requirement: Catalog entries support tenant override
The system SHALL resolve business object catalog data from GLOBAL defaults plus tenant-specific overrides without mutating the GLOBAL catalog entry.

#### Scenario: Tenant overrides display name
- **WHEN** a tenant overrides the expense report display name
- **THEN** designers in that tenant see the overridden display name while other tenants continue to see the GLOBAL display name

#### Scenario: Publish uses effective catalog
- **WHEN** a tenant publishes a process that references an overridden business object
- **THEN** the process version snapshots the effective tenant catalog values used during publication

### Requirement: Catalog entries have lifecycle states
The system SHALL manage business object catalog entries with DRAFT, PUBLISHED, and OFFLINE states.

#### Scenario: Block unpublished catalog usage
- **WHEN** a designer attempts to publish a process referencing a DRAFT business object catalog
- **THEN** publication fails with a business-language error explaining that the selected business object is not published

#### Scenario: Existing process keeps offline catalog snapshot
- **WHEN** a business object catalog is moved to OFFLINE after a process version has been published
- **THEN** existing process versions and running instances continue to use their published snapshots

### Requirement: Database catalog does not grant arbitrary execution
The system SHALL use database catalog rows only as selectable metadata and SHALL require side-effecting actions to resolve to code-registered executors.

#### Scenario: Reject URL based action
- **WHEN** a catalog action attempts to define an arbitrary URL, SQL statement, script, dynamic class name, or free Prompt as its execution mechanism
- **THEN** the system rejects or ignores that execution mechanism and requires an executorKey bound to a code-registered executor

#### Scenario: Missing executor blocks publication
- **WHEN** a process closure action references a catalog action whose executorKey has no registered executor
- **THEN** process publication fails and identifies the business action that cannot be safely executed

### Requirement: Catalog metadata is snapshotted at process publication
The system SHALL snapshot the resolved business object catalog metadata into the published process version.

#### Scenario: Status renamed after publication
- **WHEN** a business status display name is changed after process version 1 is published
- **THEN** version 1 instances continue to display the status name captured in the version 1 snapshot

#### Scenario: New catalog action added after publication
- **WHEN** a new catalog action is added after a process version is published
- **THEN** the published process version cannot use that action until a new process version is derived and published
