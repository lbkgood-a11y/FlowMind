## ADDED Requirements

### Requirement: Rapid-development applications are publishable metadata units
The system SHALL allow authorized designers to define a rapid-development application that groups forms, pages, actions, permissions, and optional workflow binding into a lifecycle-managed metadata unit.

#### Scenario: Create application draft
- **WHEN** an authorized designer creates an application with app key, display name, and at least one referenced form
- **THEN** the system creates a tenant-scoped `DRAFT` application version

#### Scenario: Publish application
- **WHEN** an application draft references only published forms, valid page metadata, valid actions, and registered permissions
- **THEN** the system publishes an immutable application version that generic runtime APIs can serve

#### Scenario: Reject invalid reference
- **WHEN** an application draft references a draft form, offline form, missing permission, or missing process binding target
- **THEN** publication fails with errors that locate the invalid application section

### Requirement: Application metadata is safe by construction
Application metadata SHALL be validated against allowlisted schemas and SHALL NOT contain arbitrary script, SQL, URL, dynamic class names, connector credentials, or free Prompt execution definitions.

#### Scenario: Reject script field
- **WHEN** a designer or API client submits page or action metadata containing script or dynamic execution fields
- **THEN** the system rejects the metadata before saving or publishing

#### Scenario: Allow declarative list columns
- **WHEN** metadata declares list columns by field key, label, format, visibility, width, and sort order
- **THEN** the system accepts the metadata if each field exists in the referenced published form snapshot

### Requirement: Generic runtime lists published applications
The runtime SHALL expose tenant-visible published applications and hide drafts, offline versions, and applications without required view permission.

#### Scenario: List available applications
- **WHEN** a user opens the rapid-development runtime center
- **THEN** the system returns only published applications visible to the user's tenant and permissions

#### Scenario: Hide offline application
- **WHEN** an application version is moved to `OFFLINE`
- **THEN** new runtime sessions no longer list or open that application while existing data remains queryable through authorized administrative APIs

### Requirement: Generic runtime renders list and detail pages
The frontend SHALL render list and detail pages from published application metadata without adding a new hard-coded Vue page per business object.

#### Scenario: Render list
- **WHEN** a user opens a published expense application through the generic runtime route
- **THEN** the list columns, filters, row actions, and status display are rendered from the published application metadata

#### Scenario: Render detail
- **WHEN** a user opens a form instance detail from a generic runtime list
- **THEN** the detail view renders fields from the published form snapshot and only exposes actions allowed by metadata and permissions

### Requirement: Generic runtime supports workflow launch binding
The runtime SHALL support configured workflow-launch actions by submitting validated form data, passing process version assumptions, and using stable idempotency keys.

#### Scenario: Submit and launch workflow
- **WHEN** a user submits a create action configured to start workflow `expense_report`
- **THEN** the runtime creates the form instance, starts the workflow through `service-workflow-engine`, and binds the form instance to the process instance idempotently

#### Scenario: Workflow start fails after instance save
- **WHEN** form instance creation succeeds but workflow start fails
- **THEN** the runtime leaves the instance visible with a retryable pending-workflow state rather than losing the submitted data

#### Scenario: Stale process version
- **WHEN** the configured process package version has been superseded before launch
- **THEN** the runtime surfaces the workflow version conflict and requires the user to reload current metadata before retrying

### Requirement: Expense sample migrates to generic runtime
The existing expense-report sample SHALL be represented as a published rapid-development application using the generic runtime while preserving current acceptance behavior.

#### Scenario: Open migrated expense app
- **WHEN** a user with expense permissions opens the migrated expense app
- **THEN** they can submit an expense report, start approval, view pending workflow state, and retry workflow launch using the generic runtime path

#### Scenario: Compatibility route
- **WHEN** an existing menu entry points to the old expense route during migration
- **THEN** the route either redirects to the generic application runtime or remains available as a temporary compatibility path until removal is explicitly scheduled
