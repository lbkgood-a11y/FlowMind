## ADDED Requirements

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
