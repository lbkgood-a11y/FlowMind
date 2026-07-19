## ADDED Requirements

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
The runtime SHALL expose page and document actions only when the central authorization decision allows the action and required local guard checks pass.

#### Scenario: Show allowed action
- **WHEN** a user opens a form instance detail and has `SUBMIT` or `APPROVE` authorization with passing guard checks
- **THEN** the runtime descriptor includes the corresponding action in the allowed actions list

#### Scenario: Hide denied action with reason
- **WHEN** the central decision denies an action or a local guard fails
- **THEN** the runtime excludes the action from normal UI metadata and can expose a diagnostic reason to authorized administrators

#### Scenario: Batch decision for runtime descriptor
- **WHEN** the runtime builds an application descriptor containing multiple pages, actions, and fields
- **THEN** it requests authorization decisions in batch and returns a descriptor filtered consistently by the batch result

### Requirement: Runtime descriptors include field authorization outcomes
The runtime SHALL include field read and write authorization outcomes in descriptors used to render lowcode form list, detail, create, edit, and approval views.

#### Scenario: Detail descriptor masks field
- **WHEN** a field decision marks a field as `MASKED`
- **THEN** the runtime descriptor marks the field masked and response data contains only the masked representation

#### Scenario: Edit descriptor makes field read-only
- **WHEN** a field decision marks a field write mode as `READONLY`
- **THEN** the runtime descriptor renders the field as non-editable and service-side submission rejects unauthorized changes
