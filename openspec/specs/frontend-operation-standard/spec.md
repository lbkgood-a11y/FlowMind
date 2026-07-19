# frontend-operation-standard Specification

## Purpose
TBD - created by archiving change standardize-frontend-operation-and-business-action-contract. Update Purpose after archive.
## Requirements
### Requirement: Vben shell remains stable while TrioBase standardizes business pages
The frontend SHALL keep the existing Vben shell architecture for layout, menu, router, access, request, theme, and base UI behavior while introducing TrioBase business-layer page and operation standards above it.

#### Scenario: Shell behavior remains unchanged
- **WHEN** the frontend operation standard is adopted
- **THEN** existing Vben shell navigation, route guards, menu rendering, request setup, theme behavior, and base layout behavior remain compatible with current `web-antd` usage

#### Scenario: Business page uses TrioBase standards
- **WHEN** a business module adds or migrates a page
- **THEN** the page uses TrioBase standard page, table, action, form, document, and business components instead of hand-assembling operation flow from raw UI components

### Requirement: Frontend modules expose bounded contracts
The frontend SHALL organize business modules so each module owns its routes, APIs, permissions, action metadata, pages, local components, composables, and types behind a bounded public module contract.

#### Scenario: Module exposes public entrypoint
- **WHEN** a module such as SCM or WMS is added
- **THEN** other modules import only the module public entrypoint or shared business components and do not deep-import private page or composable internals

#### Scenario: Shared dependencies remain downward
- **WHEN** a shared page or table component is implemented
- **THEN** it does not depend on any business module internals

### Requirement: Page patterns are standardized
The frontend SHALL provide standard page patterns for single-table pages, master-detail pages, multi-table pages, and document detail pages.

#### Scenario: Single-table page
- **WHEN** a list management page is migrated
- **THEN** it renders using a standard query area, toolbar, data table, row action area, loading state, empty state, and edit/detail surface

#### Scenario: Master-detail page
- **WHEN** a page shows a parent object and dependent child records
- **THEN** child query state is driven by the selected parent and child operations refresh only the affected child region unless a broader refresh scope is returned

#### Scenario: Document detail page
- **WHEN** a user opens an information-dense business document
- **THEN** the page renders a standard document header, status area, primary actions, summary form, detail sections, editable grid region, side context when configured, footer summary, and timeline/action feedback surfaces

### Requirement: Business action UI follows a normalized lifecycle
The frontend SHALL render business actions using a shared lifecycle for visibility, enabled state, confirmation, dispatch, loading, result feedback, error feedback, and refresh behavior.

#### Scenario: Render backend-provided action availability
- **WHEN** a document page loads action availability from the backend
- **THEN** the frontend displays only visible actions, disables actions that are not enabled, and shows the backend-provided disabled reason

#### Scenario: Dispatch action consistently
- **WHEN** a user executes a visible enabled business action
- **THEN** the frontend performs the configured confirmation if required, dispatches through the Action Client, shows loading on the action surface, handles the normalized Action result, and refreshes the scopes returned by the backend

#### Scenario: Dangerous action
- **WHEN** an action availability result marks an action as dangerous
- **THEN** the frontend renders it with the standard dangerous-action treatment and requires explicit confirmation before dispatch

### Requirement: Query and draft-edit operations stay lightweight
The frontend SHALL keep local UI events, query operations, and draft editing flows outside Global Action while preserving consistent layout and feedback.

#### Scenario: Refresh table
- **WHEN** a user filters, sorts, paginates, or refreshes a table
- **THEN** the page performs a query operation and does not create a Global Action

#### Scenario: Save draft
- **WHEN** a user saves a draft document without lifecycle transition
- **THEN** the page calls the owner domain API or management operation wrapper and does not use Global Action unless the backend marks that operation as a lifecycle action

### Requirement: Compact ERP density is standardized
The frontend SHALL use compact ERP density tokens for business operation pages.

#### Scenario: Compact business page
- **WHEN** a page opts into the TrioBase compact business layout
- **THEN** it uses standard content gutters, region gaps, control height, table row height, radius, query layout, toolbar spacing, and table frame behavior

#### Scenario: Text and controls remain usable
- **WHEN** a compact page renders buttons, filters, table cells, tabs, drawers, or action menus on supported desktop and mobile widths
- **THEN** labels remain readable, controls do not overlap, and dynamic content does not resize fixed-format toolbars or tables unexpectedly

### Requirement: Existing pages migrate in governed waves
Existing pages SHALL migrate incrementally by page family while guard tests prevent migrated lifecycle actions from bypassing the unified Action Client.

#### Scenario: Pilot page migrated
- **WHEN** a pilot page is migrated to the new operation standard
- **THEN** its layout, action surfaces, confirmation behavior, loading behavior, result feedback, refresh logic, and tests follow the standard

#### Scenario: Bypass blocked after migration
- **WHEN** a migrated page adds a new lifecycle action by calling a domain mutation endpoint directly
- **THEN** frontend governance tests fail the change unless the operation is explicitly classified as non-lifecycle

### Requirement: Second-wave existing pages adopt standard operation primitives
The frontend SHALL migrate the second wave of existing high-traffic pages to TrioBase shared operation primitives while preserving the existing Vben shell and lightweight domain query behavior.

#### Scenario: System management page migrated
- **WHEN** a system management page such as user, organization, department, menu, dictionary, config, session, audit, authz, or role is migrated
- **THEN** it renders with the standard business page scaffold, compact query area, toolbar, table or master-detail frame, consistent loading/empty states, and standardized drawer or detail surface

#### Scenario: Process runtime page migrated
- **WHEN** a process task, package, instance, or designer runtime page is migrated
- **THEN** query and detail loading stay in process APIs while lifecycle operations use the shared Action UI and backend refresh scopes when available

#### Scenario: Lowcode runtime page migrated
- **WHEN** a lowcode form list, runtime center, runtime app, or compatibility page is migrated
- **THEN** metadata/query/draft operations remain lightweight and lifecycle transitions such as submit or workflow retry use the shared Action flow

#### Scenario: OpenAPI and operations page migrated
- **WHEN** an OpenAPI lifecycle/operations page or platform operations page is migrated
- **THEN** management and query operations keep their owner APIs while runtime state-changing operations use the shared Action flow and refreshed page regions

### Requirement: Migrated pages are recorded and protected
The frontend SHALL keep an explicit migrated-page inventory and source-level tests so future changes do not reintroduce page-local lifecycle action bypasses or non-standard operation surfaces.

#### Scenario: Migrated page inventory updated
- **WHEN** a page is migrated in the second wave
- **THEN** the migration documentation records its page family, operation category, shared primitives used, and any intentional exception

#### Scenario: Migrated page uses shared primitives
- **WHEN** a migrated page is included in the governed page list
- **THEN** frontend tests verify that it imports standard TrioBase operation primitives or is explicitly exempted with a documented reason

#### Scenario: Lifecycle bypass blocked
- **WHEN** a migrated page adds a new lifecycle action that calls a domain mutation endpoint directly
- **THEN** frontend governance tests fail unless the operation is explicitly classified as a management or draft operation

