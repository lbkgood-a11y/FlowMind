## ADDED Requirements

### Requirement: Paginated authorization resource discovery
The system SHALL provide a standalone authorization resource catalog that lists registered resources with Owner service, resource type, lifecycle, enforcement state, and last synchronization time using server-side pagination.

#### Scenario: Administrator opens the catalog
- **WHEN** an authorized administrator opens the authorization resource catalog
- **THEN** the system loads the first resource page and keeps an explicit pagination component visible at the bottom

#### Scenario: Administrator filters resources
- **WHEN** an administrator filters by keyword, Owner service, resource type, or lifecycle state
- **THEN** the system resets to the first page and returns only matching resources

### Requirement: Complete resource detail inspection
The catalog SHALL allow an administrator to inspect a selected resource's actions, fields, guards, enforcement flags, business object identity, metadata, and synchronization state without navigating to a role.

#### Scenario: Administrator opens resource detail
- **WHEN** an administrator selects a resource row
- **THEN** a detail drawer displays the resource and its registered child metadata with explicit empty states for absent optional data

### Requirement: Owner-governed registration boundary
The resource catalog MUST remain read-only and MUST direct resource definition changes through the owning service's governed synchronization mechanism.

#### Scenario: Administrator inspects a resource
- **WHEN** an administrator views any resource in the catalog
- **THEN** the page exposes no create, edit, or delete mutation that could bypass the Owner service

### Requirement: Registration freshness visibility
The catalog SHALL expose stale registration diagnostics and allow the administrator to refresh the displayed registry state.

#### Scenario: Resource synchronization is stale
- **WHEN** a registered resource exceeds the configured freshness threshold
- **THEN** the catalog marks it as stale and exposes its last synchronization time and Owner service

### Requirement: Adaptive catalog layout
The catalog SHALL fill available width and height, scroll table content internally, support horizontal overflow, and preserve filters and bottom pagination across supported viewport sizes.

#### Scenario: Resource columns exceed the viewport
- **WHEN** the catalog columns exceed the available width or rows exceed the available height
- **THEN** the table scrolls internally while filters and pagination remain usable

### Requirement: Permission-governed navigation
The system SHALL publish the authorization resource catalog through the backend-driven system menu and page-capability projection and SHALL require authorization registry read permission.

#### Scenario: Authorized administrator loads menus
- **WHEN** an administrator has catalog access and authorization registry read permission
- **THEN** the system menu contains “权限治理 → 资源注册中心” and the page can query registry data

### Requirement: Distinct governance navigation and cross-reference
The system SHALL name the two catalogs “页面能力目录” and “资源注册中心”, SHALL group them under “权限治理”, and SHALL expose bidirectional navigation between a page capability and its runtime authorization resources.

#### Scenario: Administrator follows a page capability target
- **WHEN** an administrator selects a runtime target in the page capability catalog
- **THEN** the system opens the resource registration center filtered to that target resource

#### Scenario: Administrator inspects resource references
- **WHEN** an administrator opens a registered resource detail
- **THEN** the system lists page capabilities that reference that resource and allows navigation back to the page capability catalog
