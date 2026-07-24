# lowcode-application-runtime Specification

## Purpose
TBD - created by archiving change lowcode-production-hardening. Update Purpose after archive.
## Requirements
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
The runtime SHALL support configured workflow-launch actions by dispatching a Global Action that submits validated form data, passes process version assumptions, uses stable idempotency keys, and binds resulting form and process records through the normalized action lifecycle.

#### Scenario: Submit and launch workflow
- **WHEN** a user submits a create action configured to start workflow `expense_report`
- **THEN** the runtime dispatches a Global Action that creates the form instance, starts the workflow through `service-workflow-engine`, and binds the form instance to the process instance idempotently

#### Scenario: Workflow start fails after instance save
- **WHEN** form instance creation succeeds but workflow start fails
- **THEN** the Global Action result records the failed owner execution and the runtime leaves the instance visible with retryable pending-workflow detail rather than losing the submitted data

#### Scenario: Stale process version
- **WHEN** the configured process package version has been superseded before launch
- **THEN** the Global Action is rejected or failed with workflow version conflict details and requires the user to reload current metadata before retrying

### Requirement: Expense sample migrates to generic runtime
The existing expense-report sample SHALL be represented as a published rapid-development application using the generic runtime and Global Action dispatch while preserving current acceptance behavior.

#### Scenario: Open migrated expense app
- **WHEN** a user with expense permissions opens the migrated expense app
- **THEN** they can submit an expense report, start approval, view pending workflow state, and retry workflow launch using the generic runtime and Global Action path

#### Scenario: Compatibility route removed after action migration
- **WHEN** all expense runtime operations have migrated to Global Action dispatch
- **THEN** the old expense route is removed or redirects without retaining a separate business mutation implementation

### Requirement: Runtime application visibility uses authorization decisions
The runtime SHALL expose only tenant-visible published applications that the current user is authorized to view according to the enterprise authorization decision API.

#### Scenario: Authorized application is visible
- **WHEN** a user opens the lowcode application center with `VIEW` authorization for a published application
- **THEN** the runtime lists the application with its allowed pages and safe display metadata

#### Scenario: Unauthorized application is hidden
- **WHEN** a user lacks `VIEW` authorization for a published application
- **THEN** the runtime omits the application from navigation and application-center responses

#### Scenario: Cross-tenant application remains hidden
- **WHEN** a user requests application metadata belonging to another tenant
- **THEN** the runtime denies or hides the application regardless of action metadata

### Requirement: Runtime actions are filtered by function and guard decisions
The runtime SHALL expose page and document actions only when the central authorization decision, action-level policy evaluation, and required local guard checks pass, and each exposed action SHALL include the Global Action type and confirmation metadata needed by the frontend Action Client.

#### Scenario: Show allowed action
- **WHEN** a user opens a form instance detail and has `SUBMIT` or `APPROVE` authorization with passing guard checks
- **THEN** the runtime descriptor includes the corresponding allowed action with action type, target binding, guard requirements, confirmation metadata, and display metadata

#### Scenario: Hide denied action with reason
- **WHEN** the central decision denies an action or a local guard fails
- **THEN** the runtime excludes the action from normal UI metadata and can expose a diagnostic reason to authorized administrators

#### Scenario: Batch decision for runtime descriptor
- **WHEN** the runtime builds an application descriptor containing multiple pages, actions, and fields
- **THEN** it requests authorization decisions in batch and returns a descriptor filtered consistently by the batch result and Global Action definitions

### Requirement: Runtime descriptors include field authorization outcomes
The runtime SHALL include field read and write authorization outcomes in descriptors used to render lowcode form list, detail, create, edit, and approval views.

#### Scenario: Detail descriptor masks field
- **WHEN** a field decision marks a field as `MASKED`
- **THEN** the runtime descriptor marks the field masked and response data contains only the masked representation

#### Scenario: Edit descriptor makes field read-only
- **WHEN** a field decision marks a field write mode as `READONLY`
- **THEN** the runtime descriptor renders the field as non-editable and service-side submission rejects unauthorized changes

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

