## ADDED Requirements

### Requirement: Application management workbench completes the lifecycle
The frontend SHALL provide a non-404 application management workbench for listing, creating, editing, versioning, designing, publishing, and offlining rapid-development applications.

#### Scenario: Create leave application
- **WHEN** a designer opens Rapid Development / Application Management and creates an application from a published leave form
- **THEN** a DRAFT application version is created and can be configured without direct API use

#### Scenario: Publish configured application
- **WHEN** pages, actions, form references, relations, and permission references are valid
- **THEN** the workbench publishes the immutable version and shows it in the runtime application center for authorized users

### Requirement: Application runtime supports related form pages
Published application descriptors SHALL include the immutable relation graph and page metadata required to render master, child, and grandchild create, detail, and list experiences.

#### Scenario: Render nested create page
- **WHEN** the user starts a new instance of an application with a three-level relation graph
- **THEN** the runtime renders registered field components for the master and nested tabular editors for children and grandchildren without dynamic code execution

### Requirement: Existing single-form applications remain compatible
The runtime SHALL treat applications without relation metadata as single-form applications and preserve existing list, create, detail, workflow, and authorization behavior.

#### Scenario: Open existing expense application
- **WHEN** an existing published expense application has no relation rows
- **THEN** it continues to render and execute through the generic runtime with no migration required
