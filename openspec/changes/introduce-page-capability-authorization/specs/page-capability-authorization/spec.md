## ADDED Requirements

### Requirement: Page capability catalog uses business language
The system SHALL maintain a tenant-scoped, versioned page capability catalog that categorizes capabilities as page access, read, or business operation and presents implementation personnel with the same names and meanings used by the actual page.

#### Scenario: Implementation person opens a role page
- **WHEN** an implementation person selects the User Management menu while configuring a role
- **THEN** the system shows business capabilities such as enter page, view users, add user, edit user, disable user, reset password, and export without showing resource codes, action codes, endpoints, HTTP methods, or service names

#### Scenario: Page operation has no valid mapping
- **WHEN** a registered business operation has no complete and verified runtime target mapping
- **THEN** the system marks the operation unavailable for publication and gives the implementation person a plain-language instruction to contact a platform administrator

### Requirement: Access, read, and operation authorization are distinct
The system SHALL distinguish whether a role can enter a page, read business information, and execute each business operation, and SHALL evaluate each category independently at runtime.

#### Scenario: Role can enter but cannot read existing records
- **WHEN** a role is allowed to enter an application page and create a request but has no capability to read existing requests
- **THEN** the page remains accessible for creation while existing record queries are denied or empty according to the page contract

#### Scenario: Role can read but cannot mutate
- **WHEN** a role has page access and read capability but no edit, delete, or export operation capability
- **THEN** the role can view permitted records while mutation and export controls and backend actions remain unavailable

### Requirement: Capability dependencies are resolved consistently
The system SHALL store capability dependencies centrally, automatically include required capabilities, prevent invalid removals, and use the same resolved dependency set for UI summaries and runtime compilation.

#### Scenario: Edit requires access and read
- **WHEN** an implementation person selects edit user
- **THEN** the system automatically selects and identifies enter User Management and view users as required dependencies

#### Scenario: Remove a required read capability
- **WHEN** an implementation person attempts to remove view users while edit user and disable user still depend on it
- **THEN** the system identifies the dependent operations and requires their removal or cancellation of the change

#### Scenario: Manifest contains a dependency cycle
- **WHEN** page capability registration produces a cyclic dependency graph
- **THEN** the catalog rejects or marks the affected version broken and prevents role publication against it

### Requirement: Role page capability intent is persisted
The system SHALL persist the role's selected page capabilities and business policy choices as tenant-scoped desired intent independently from compiled resource/action grants.

#### Scenario: Runtime mapping changes
- **WHEN** a target resource/action mapping changes after a role release was published
- **THEN** the system retains the role's original page capability intent and reports drift without silently changing its active compiled permissions

### Requirement: Data scope supports page defaults and explicit operation overrides
The system SHALL let implementation personnel define a page-level default data scope and SHALL allow a supported business operation to override it explicitly without exposing storage dimensions or policy codes.

#### Scenario: Read and approval scopes differ
- **WHEN** a role may view company-wide expense claims but approve only claims from its department and descendants
- **THEN** the summary and compiled policies preserve the company-wide read scope and the narrower approval scope separately

#### Scenario: Operation does not support scope configuration
- **WHEN** an implementation person attempts to configure a data scope for an operation whose owner manifest does not declare executable scope support
- **THEN** validation blocks the unsupported configuration instead of representing it as effective

### Requirement: Authorization changes follow a controlled release lifecycle
The system SHALL separate draft editing, validation, publication, active release, failed publication, and rollback so draft changes never affect runtime authorization before successful publication.

#### Scenario: Save an incomplete draft
- **WHEN** an implementation person saves role configuration with an unresolved non-production mapping
- **THEN** the draft is retained for later work while the currently active authorization release remains unchanged

#### Scenario: Publish stale validation
- **WHEN** the draft, catalog, tenant context, or publisher authority changes after validation
- **THEN** publication rejects the stale validation token and requires revalidation

#### Scenario: Compilation fails during publication
- **WHEN** compiled grants or policy projections cannot be persisted completely
- **THEN** publication fails atomically and the previous active release remains effective

### Requirement: Validation explains effective authorization in business language
The system SHALL generate a review summary, compiled difference, dependency explanation, affected-user analysis, and actual-user or current-role simulation using menu and business-operation terminology.

#### Scenario: Review a finance approver role
- **WHEN** an implementation person validates a finance approver draft
- **THEN** the system states which pages are accessible, what can be viewed, which operations can be performed, each effective data scope, field restriction, and business constraint without requiring technical permission knowledge

#### Scenario: Simulate an operation denial
- **WHEN** current-role simulation denies self-approval
- **THEN** the result identifies the business operation, applicable data, failed business constraint, and final denial in business language

### Requirement: Mapping drift requires impact review
The system SHALL detect capability catalog or target mapping changes that affect active role releases and SHALL require impact review and a new publication before changing active projections.

#### Scenario: Mapping adds a new runtime target
- **WHEN** a page operation mapping gains an additional resource/action target
- **THEN** the system lists affected roles and users and does not grant the new target until an authorized administrator publishes a reviewed release

### Requirement: Runtime enforcement uses the published projection
The system SHALL enforce the active release consistently across menu visibility, route access, page action availability, backend resource actions, data queries, field reads and writes, and owner-service business guards.

#### Scenario: Caller bypasses the frontend
- **WHEN** a user without a published delete operation grant calls the delete endpoint directly
- **THEN** the owner service denies the action regardless of menu or button visibility

#### Scenario: Published read scope filters data
- **WHEN** a role's active release limits user viewing to its department and descendants
- **THEN** the owner service applies the compiled identifiers to its query and fails closed if it cannot compile the scope safely

### Requirement: Authorization releases are auditable and reversible
The system SHALL record business intent changes, automatic dependency changes, validation evidence, release differences, publication results, affected users, compiled projections, actor, tenant, trace, and rollback events.

#### Scenario: Auditor reviews a role expansion
- **WHEN** an auditor opens a release that added export and expanded read scope
- **THEN** the audit view names the page, business operations, old and new scope, publisher, affected users, and release versions with optional administrator-only technical evidence

#### Scenario: Roll back a release
- **WHEN** an authorized administrator rolls a role back to its preceding release
- **THEN** the system atomically restores that immutable release's compiled projections and records the rollback without recompiling against current mappings

### Requirement: Legacy migration does not widen access
The system SHALL analyze existing grants and policies into exact, partial, or unmapped capability intent and SHALL require review when reconstruction is not exact.

#### Scenario: Existing grants map exactly
- **WHEN** all active grants and policies for a role resolve exactly to registered page capabilities
- **THEN** the system creates a migration draft whose compiled comparison is equivalent to the existing effective authorization

#### Scenario: Existing grant has ambiguous reverse mappings
- **WHEN** an existing resource/action grant maps to multiple page capabilities or no page capability
- **THEN** the migration marks the draft for manual review and keeps the existing active grants unchanged

