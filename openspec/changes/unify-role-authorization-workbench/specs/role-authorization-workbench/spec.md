## ADDED Requirements

### Requirement: Role-centered workbench navigation
The frontend SHALL expose one “角色与授权” workbench with a role tree on the left and nine configuration tabs on the right, and SHALL preserve selected role and tab in the URL.

#### Scenario: Administrator selects a role and tab
- **WHEN** an administrator selects a role and opens an available configuration tab
- **THEN** the workbench updates `roleId` and `tab` in the URL and loads only that tab's data for the selected role

#### Scenario: Workbench is refreshed
- **WHEN** a valid workbench URL containing `roleId` and `tab` is refreshed
- **THEN** the same role and tab are restored

#### Scenario: Administrator switches an internal configuration tab
- **WHEN** an administrator switches between authorization configuration tabs
- **THEN** the content changes inside the current workbench and no new top-level application tab or route navigation is created

### Requirement: Nine authorization configuration tabs
The workbench SHALL provide Basic Information, Page Functions, Business Functions, Data Permissions, Field Rules, Lowcode Applications, Guards, Decision Preview, and Diagnostics tabs using the existing owner APIs.

#### Scenario: Role has complete authorization configuration
- **WHEN** an authorized administrator navigates between tabs for a selected role
- **THEN** each tab exposes the complete existing capability without requiring navigation to another menu page

### Requirement: Permission-aware workbench
The workbench MUST hide or disable tabs and mutations for which the current administrator lacks permission and MUST NOT widen backend authorization.

#### Scenario: Administrator cannot query data policies
- **WHEN** the current administrator lacks data-policy query permission
- **THEN** the Data Permissions tab is absent and no data-policy request is issued

### Requirement: Legacy route compatibility
The frontend SHALL redirect the former role management, data permission, enterprise authorization, and authorization alias routes to the canonical workbench with the corresponding tab context.

#### Scenario: Legacy data permission bookmark is opened
- **WHEN** a user opens `/system/data-permission`
- **THEN** the browser reaches the canonical workbench with the Data Permissions tab active

### Requirement: Adaptive tabular content
Every tabular workbench view SHALL fill available width and height, scroll internally when content exceeds the viewport, and keep an explicit pagination component visible at the bottom.

#### Scenario: Table exceeds available viewport
- **WHEN** rows or columns exceed the workbench viewport
- **THEN** table content scrolls inside its panel while the role tree, tab bar, and pagination remain visible

### Requirement: Responsive role master panel
The workbench SHALL show a fixed-width role master panel on desktop and a collapsible or stacked role selector on narrow screens.

#### Scenario: Workbench is opened on a narrow viewport
- **WHEN** the viewport crosses the configured responsive breakpoint
- **THEN** the role selector no longer reduces the tab content below its usable width
