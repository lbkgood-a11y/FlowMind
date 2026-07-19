## ADDED Requirements

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
