# lowcode-form-governance Specification

## Purpose
TBD - created by archiving change lowcode-production-hardening. Update Purpose after archive.
## Requirements
### Requirement: Lowcode owns form schema migrations
The lowcode service SHALL own all database migrations that create or mutate lowcode-owned `lc_*` tables.

#### Scenario: Start lowcode with owned migrations
- **WHEN** `service-lowcode` starts in a clean environment
- **THEN** Flyway creates the lowcode form definition, field definition, form instance, process binding, audit, tenant, version, and index structures without requiring `service-auth` migrations to create lowcode tables

#### Scenario: Auth migrations do not mutate lowcode tables
- **WHEN** a new auth permission or menu seed is added for lowcode
- **THEN** the auth migration only mutates auth-owned permission, menu, role, and data-policy tables

### Requirement: Form definitions are tenant-scoped and versioned
The system SHALL identify form definitions by tenant, form key, and immutable version, and SHALL allow at most one draft per tenant and form key.

#### Scenario: Create first tenant form draft
- **WHEN** an authorized designer creates form key `expense` for tenant `T1`
- **THEN** the system creates version `1` in `DRAFT` status scoped to tenant `T1`

#### Scenario: Prevent duplicate draft
- **WHEN** tenant `T1` already has a `DRAFT` for form key `expense`
- **THEN** another draft for the same tenant and form key is rejected

#### Scenario: Allow another tenant to use same form key
- **WHEN** tenant `T2` creates form key `expense`
- **THEN** the system allows it because uniqueness is scoped by tenant

### Requirement: Published form versions are immutable
Published and offline form definition versions SHALL NOT be edited in place; changes SHALL be made by deriving a new draft version.

#### Scenario: Edit published form
- **WHEN** an authorized designer attempts to update schema, UI schema, fields, name, or description of a `PUBLISHED` form version
- **THEN** the system rejects the update and instructs the caller to derive a new version

#### Scenario: Derive new draft
- **WHEN** an authorized designer derives a new version from the latest published form
- **THEN** the system creates a new `DRAFT` with copied metadata and `version = latest + 1`

### Requirement: Form definition publication validates schema
The lowcode service SHALL validate JSON Schema and UI Schema on create, update, and publish using the supported production widget registry.

#### Scenario: Publish valid form
- **WHEN** a draft uses an object JSON Schema with supported field types and registered UI widgets
- **THEN** publication succeeds and the published snapshot records schema, UI schema, version, tenant, form key, and schema hash

#### Scenario: Reject unsupported widget
- **WHEN** a draft UI Schema references an unregistered widget
- **THEN** create, update, or publish fails with a business-language schema validation error

#### Scenario: Reject invalid required field
- **WHEN** a draft JSON Schema marks a missing property as required
- **THEN** create, update, or publish fails before the draft can become published

### Requirement: Published form snapshot contract is immutable
The internal published form snapshot endpoint SHALL return only immutable published form versions and SHALL reject draft, offline, stale, or cross-tenant references.

#### Scenario: Workflow requests published snapshot
- **WHEN** `service-workflow-engine` requests a published form by ID
- **THEN** the lowcode service returns the exact published schema, UI schema, form key, version, tenant, and schema hash for that form ID

#### Scenario: Workflow requests draft snapshot
- **WHEN** `service-workflow-engine` requests a form definition that is still `DRAFT`
- **THEN** the lowcode service rejects the request

#### Scenario: Workflow requests cross-tenant form
- **WHEN** the caller context is tenant `T1` and the requested form belongs to tenant `T2`
- **THEN** the lowcode service rejects the request unless the form is explicitly platform-global and allowed by policy

### Requirement: Form definition APIs enforce service-side permissions
All public form definition lifecycle APIs SHALL enforce service-side permissions in addition to frontend button visibility.

#### Scenario: Unauthorized create
- **WHEN** a caller without `/api/v1/forms:POST` submits a create form request
- **THEN** the lowcode service rejects the request before writing metadata

#### Scenario: Unauthorized publish
- **WHEN** a caller without `/api/v1/forms/*/publish:PUT` attempts to publish a draft
- **THEN** the lowcode service rejects the request and leaves the draft unchanged

#### Scenario: Authorized query is tenant-filtered
- **WHEN** a caller with `/api/v1/forms:GET` lists form definitions
- **THEN** the response includes only forms visible in the caller's tenant context

### Requirement: Published forms synchronize authorization resources
The lowcode service SHALL synchronize tenant-scoped authorization resources, actions, fields, and guard templates with `service-auth` when a form version is published.

#### Scenario: Publish form registers resources
- **WHEN** an authorized designer publishes form key `expense` for tenant `T1`
- **THEN** the system registers lowcode form resources for view, create, edit, submit, approve, reject, export, design, publish, offline, field read, and field write actions required by the published metadata

#### Scenario: Authorization sync is idempotent
- **WHEN** the same published form version retries authorization resource synchronization
- **THEN** the registry keeps stable resource codes and updates metadata without creating duplicate resources or actions

#### Scenario: Authorization sync fails
- **WHEN** form publication cannot synchronize required authorization resources
- **THEN** the system prevents the form from becoming runtime-visible or marks publication as retryable without exposing a partially authorized form

### Requirement: Form fields carry authorization metadata
Published lowcode form field definitions SHALL carry enough metadata for field authorization, masking, and write-mode decisions.

#### Scenario: Declare sensitive field
- **WHEN** a designer marks a field as sensitive or maskable before publication
- **THEN** the published field metadata exposes the field key, label, type, sensitivity classification, and supported mask strategy to authorization synchronization

#### Scenario: Missing field authorization metadata
- **WHEN** a published form contains a field without explicit field authorization metadata
- **THEN** the system applies tenant default field policy rules and records the default in the authorization resource synchronization result

### Requirement: Form publication validates declared permission resources
The lowcode service SHALL validate that application actions, workflow actions, and form field references can be represented as authorization resources before publication succeeds.

#### Scenario: Invalid action authorization reference
- **WHEN** a form or application draft declares an action that cannot be mapped to a supported authorization action
- **THEN** publication fails with a validation error identifying the invalid action

#### Scenario: Workflow guard declaration
- **WHEN** a published form is configured to launch or participate in workflow approval
- **THEN** authorization synchronization declares the required workflow and document guard templates for approval actions

