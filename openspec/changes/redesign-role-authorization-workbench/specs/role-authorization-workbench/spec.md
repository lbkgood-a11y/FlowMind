## ADDED Requirements

### Requirement: Guided role authorization sequence
The system SHALL present role authorization as the ordered stages Function & Menu, Data Scope, Field Access, Business Constraints, and Validation, and SHALL show completion and warning status for every stage.

#### Scenario: Configure a saved role
- **WHEN** an implementer opens authorization for a saved role
- **THEN** the workbench loads all five stages and summarizes the current effective configuration without requiring the implementer to inspect persistence records

### Requirement: Function grants explain derived menus
The workbench SHALL configure function grants as the writable source and SHALL render the resulting menu tree with mapping evidence for every visible or problematic node.

#### Scenario: Grant produces a menu
- **WHEN** an implementer selects a resource action mapped by a menu permission code
- **THEN** the menu preview immediately identifies the directly granted menu and ancestor menus added for navigation

#### Scenario: Menu has no authorization mapping
- **WHEN** an active business menu has no valid resource/action mapping
- **THEN** the workbench displays an unmapped warning and does not imply that selecting the menu grants access

### Requirement: Data scope is summarized as an effective rule
The workbench SHALL configure data scope by resource, action, scope type, organization dimension, and identifiers, and SHALL render a natural-language effective summary and policy evidence.

#### Scenario: Assigned organization scope
- **WHEN** an implementer selects assigned organizations for a resource action
- **THEN** the workbench names the dimension and organizations and explains whether descendants are included

#### Scenario: Multiple matching policies
- **WHEN** multiple policies can affect the same resource action
- **THEN** the workbench shows the effective merge or precedence result and identifies contributing policies

### Requirement: Field access shows runtime enforcement coverage
The workbench SHALL show read-hide, read-mask, and write-deny enforcement coverage for each resource and MUST NOT present unsupported behavior as effective.

#### Scenario: Resource supports masking only
- **WHEN** a resource declares read masking but not write denial enforcement
- **THEN** the workbench permits masking configuration, disables write-deny configuration, and explains the missing owner-service capability

### Requirement: Role page separates business constraints from template administration
The role workbench SHALL display guard requirements implied by selected actions and SHALL direct guard-template creation and global enablement to the authorization center.

#### Scenario: Granted action requires a guard
- **WHEN** a selected action declares a no-self-approval guard
- **THEN** the Business Constraints stage shows the guard, owner service, status, and description without offering role-local template creation

### Requirement: Validation supports actual users and role simulation
The workbench SHALL support searchable actual-user validation and current-role simulation and SHALL clearly identify which mode produced a result.

#### Scenario: Select an actual user
- **WHEN** an implementer searches by username and selects a user
- **THEN** the workbench submits the user ID, displays the user's roles and organization context, and warns if the edited role is not assigned

#### Scenario: Simulate the current role
- **WHEN** an implementer selects current-role simulation with optional organization context
- **THEN** the workbench evaluates the edited role without persisting a user-role assignment and labels the result as simulated

### Requirement: Decision explanation is layered
The workbench SHALL explain function, menu, data scope, field access, guard requirements, final outcome, reasons, and policy versions in one validation result.

#### Scenario: Allowed with restrictions
- **WHEN** a decision allows an action with organization scope, masked fields, and a required guard
- **THEN** the result shows each restriction separately and does not reduce the outcome to a single allowed tag
