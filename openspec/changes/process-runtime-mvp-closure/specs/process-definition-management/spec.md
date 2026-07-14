## ADDED Requirements

### Requirement: Draft process definitions are editable
The system SHALL allow authorized users to create and update a process package while it is in DRAFT status, and SHALL reject content updates to PUBLISHED or OFFLINE versions.

#### Scenario: Update a draft
- **WHEN** an authorized user updates the name, flow definition, or form reference of a DRAFT process package
- **THEN** the system persists the update without changing the package version

#### Scenario: Reject mutation of a published version
- **WHEN** a user attempts to update the content of a PUBLISHED process package
- **THEN** the system rejects the request and leaves the published definition unchanged

### Requirement: Published process versions are immutable
The system SHALL identify process definitions by `processKey` and `version`, SHALL allow multiple published versions for one process key, and SHALL keep a published version immutable.

#### Scenario: Publish the first version
- **WHEN** a valid version 1 DRAFT is published
- **THEN** the system marks version 1 as PUBLISHED and preserves its flow and form snapshots

#### Scenario: Derive a new version
- **WHEN** an authorized user requests a new version from the latest published process package
- **THEN** the system creates a new DRAFT with version incremented by one and leaves the source version unchanged

#### Scenario: Prevent multiple drafts
- **WHEN** a DRAFT already exists for a process key and another draft creation is requested
- **THEN** the system rejects the request with a conflict response

### Requirement: Process instances remain bound to their start version
The system SHALL bind each process instance to the exact published process package identifier and version selected at start time.

#### Scenario: Publish a newer version during execution
- **WHEN** version 2 is published while an instance created from version 1 is still running
- **THEN** the running instance continues with the version 1 definition

### Requirement: Publishing validates and snapshots the complete package
The system SHALL validate the flow graph, participant configuration, condition syntax, form Schema, and UI Schema before publishing, and SHALL snapshot the resolved form definition into the published package.

#### Scenario: Publish a valid package
- **WHEN** a DRAFT contains one START, at least one END, reachable supported nodes, valid participants, valid conditions, and a supported form Schema
- **THEN** the system publishes the package atomically with immutable flow and form snapshots

#### Scenario: Reject an invalid package
- **WHEN** a DRAFT contains an unreachable node, unsupported node type, invalid participant, invalid condition, or unsupported form widget
- **THEN** the system rejects publication with field-level validation details and keeps the package in DRAFT status

### Requirement: Existing process package APIs remain compatible
The system SHALL preserve existing create, list, detail, publish, offline, and delete API behavior while adding draft update and new-version operations.

#### Scenario: Existing client lists packages
- **WHEN** an existing client calls the process package list endpoint without version filters
- **THEN** the system returns compatible paged records including version and status fields
