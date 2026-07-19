## MODIFIED Requirements

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

### Requirement: Expense sample migrates to generic runtime
The existing expense-report sample SHALL be represented as a published rapid-development application using the generic runtime and Global Action dispatch while preserving current acceptance behavior.

#### Scenario: Open migrated expense app
- **WHEN** a user with expense permissions opens the migrated expense app
- **THEN** they can submit an expense report, start approval, view pending workflow state, and retry workflow launch using the generic runtime and Global Action path

#### Scenario: Compatibility route removed after action migration
- **WHEN** all expense runtime operations have migrated to Global Action dispatch
- **THEN** the old expense route is removed or redirects without retaining a separate business mutation implementation
