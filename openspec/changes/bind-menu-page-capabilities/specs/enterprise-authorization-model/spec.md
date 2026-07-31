## ADDED Requirements

### Requirement: System capability catalogs bootstrap before configuration workflows
The authorization system SHALL idempotently materialize the system Page Capability Catalog for the default tenant during startup and for an authenticated tenant when a catalog-dependent API first observes no active catalog. Menu configuration and role authorization SHALL NOT be responsible for manually creating the catalog.

#### Scenario: Default tenant starts without a catalog
- **WHEN** service-auth becomes ready and the default tenant has no active system capability catalog
- **THEN** the system materializes and activates the built-in manifest before administrators use menu or role configuration

#### Scenario: Authenticated tenant queries before bootstrap
- **WHEN** an authenticated tenant queries page capabilities and no active catalog exists
- **THEN** the system performs one idempotent materialization attempt and returns the active catalog capabilities when readiness checks pass

### Requirement: Capability catalog has a dedicated inspection workbench
The system SHALL expose a separate read-only Page Capability Catalog menu and workbench showing catalog versions, lifecycle state, capabilities, readiness, dependencies, and runtime targets.

#### Scenario: Administrator opens capability catalog
- **WHEN** an authorized administrator opens the Page Capability Catalog menu
- **THEN** the workbench displays tenant-scoped catalog lifecycle information and grouped capability diagnostics without requiring a role selection or menu edit

### Requirement: Backend page capability catalog drives configurable page operations
The authorization system SHALL expose the active tenant-scoped Page Capability Catalog as the authoritative source of configurable page operations and SHALL support exact filtering by stable `pageCode`. Frontends MUST NOT create authorization operations that are absent from the owner-published catalog.

#### Scenario: Query capabilities for one page
- **WHEN** an authorized administrator queries page capabilities with `pageCode=SYSTEM.MENU`
- **THEN** the backend returns only active capabilities for that page from the selected or active tenant catalog, ordered by their declared sort order

#### Scenario: Page has no registered capabilities
- **WHEN** an authorized administrator queries a `pageCode` that is absent from the active catalog
- **THEN** the backend returns an empty list without inferring capabilities from menu button rows, route paths, or frontend labels

#### Scenario: Owner publishes a new operation
- **WHEN** an owner service publishes and activates a valid new OPERATION capability for an existing page
- **THEN** authorized frontend configuration surfaces can discover it from the backend catalog without adding a menu button permission row

### Requirement: Menus explicitly reference catalog pages
Page-bearing menu entries SHALL require an explicit stable `pageCode` reference to the Page Capability Catalog. The reference remains empty only for catalogs and links that do not render a governed business page.

#### Scenario: Save a page-bound menu
- **WHEN** an administrator creates or updates a page menu with a registered `pageCode`
- **THEN** the menu service persists and returns that `pageCode` without copying the page's capabilities into menu authorization rows

#### Scenario: Reject an unmapped page menu
- **WHEN** an administrator creates or updates a page-bearing menu without a registered `pageCode`
- **THEN** the menu API rejects the request and does not persist an unmapped page menu

### Requirement: Only ready page capabilities are newly configurable
Authorization configuration surfaces SHALL permit new selections only for Page Capabilities whose readiness is `READY`; other active capabilities SHALL remain visible for diagnosis with their backend-provided readiness message.

#### Scenario: Select a ready operation
- **WHEN** an administrator configures a role and selects a `READY` OPERATION capability
- **THEN** the system accepts the selection and resolves its declared ACCESS and READ dependencies

#### Scenario: Attempt to select an incomplete operation
- **WHEN** an administrator attempts to newly select a `PARTIAL`, `BROKEN`, or `UNMAPPED` capability
- **THEN** the system rejects or disables the selection and presents the catalog readiness reason

### Requirement: Every active page navigation is declared by an owner
Every active page-bearing navigation menu SHALL map to an explicit owner-declared page in the active catalog. Owner declarations SHALL identify the owner service and SHALL use registered resource/action targets rather than deriving authorization facts from frontend labels.

#### Scenario: Platform catalog activates
- **WHEN** the composed platform catalog is synchronized and activated
- **THEN** every active `menu` or `embedded` navigation row has a registered `pageCode` and every declared capability is `READY`

#### Scenario: Retired permission route is encountered
- **WHEN** the legacy permission-management navigation points to the retired permission API
- **THEN** the migration removes that navigation instead of registering it as a new page
