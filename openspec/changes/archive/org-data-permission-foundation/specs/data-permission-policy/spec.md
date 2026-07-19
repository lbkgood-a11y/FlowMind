## ADDED Requirements

### Requirement: Role Data Policy Management
The system SHALL allow authorized administrators to configure data permission policies for roles. Each policy MUST identify the role, resource, action, effect, combination mode, and one or more organization dimension scopes.

#### Scenario: Create role data policy
- **WHEN** an authorized administrator creates a data policy for a role, resource, action, and dimension scopes
- **THEN** the system persists the policy and its dimension scope records

#### Scenario: Update role data policy
- **WHEN** an authorized administrator updates a role data policy
- **THEN** the system replaces the policy dimension scopes atomically and preserves policy identity

#### Scenario: Delete role data policy
- **WHEN** an authorized administrator deletes a role data policy
- **THEN** the system removes the policy and all associated dimension scopes

### Requirement: Data Policy Scope Types
The system SHALL support standard data scope types: `SELF`, `OWN_ORG`, `OWN_ORG_AND_CHILDREN`, `ASSIGNED_ORGS`, and `ALL`.

#### Scenario: Assigned organizations scope
- **WHEN** a policy dimension uses `ASSIGNED_ORGS`
- **THEN** the request MUST include at least one organization unit ID for that dimension

#### Scenario: All scope
- **WHEN** a policy dimension uses `ALL`
- **THEN** the policy grants all organization units in that dimension and does not require explicit organization IDs

#### Scenario: Own organization and children scope
- **WHEN** a policy dimension uses `OWN_ORG_AND_CHILDREN`
- **THEN** the runtime resolves the user's primary organization for that dimension and includes its descendants in the effective data range

#### Scenario: Own organization scope
- **WHEN** a policy dimension uses `OWN_ORG`
- **THEN** the runtime resolves the user's primary organization for that dimension and returns that organization ID

#### Scenario: Assigned organization runtime scope
- **WHEN** a policy dimension uses `ASSIGNED_ORGS`
- **THEN** the runtime returns the explicitly configured organization IDs without expanding the organization tree

### Requirement: Multi-Dimension Data Policy Combination
The system SHALL combine organization scopes using OR within the same dimension and AND across different dimensions by default. Multiple allowed role policies SHALL be combined with OR, while explicit DENY policies MUST take precedence.

#### Scenario: Cross-dimension AND filter
- **WHEN** a role policy grants `LEGAL` organizations and `ACCOUNTING` organizations with `combineMode` set to `AND`
- **THEN** the effective data filter requires records to match both the legal organization range and the accounting organization range

#### Scenario: Multiple role union
- **WHEN** a user has multiple roles with allowed policies for the same resource and action
- **THEN** the effective data range is the union of allowed policy results unless a matching DENY policy excludes data

#### Scenario: Deny precedence
- **WHEN** a DENY policy applies to the same resource, action, and organization range as an ALLOW policy
- **THEN** the system treats the denied range as inaccessible

### Requirement: Data Policy Query Runtime
The system SHALL expose a runtime query capability that resolves a user's effective data policies for a resource and action into a standard DTO usable by business services.

#### Scenario: Resolve effective policy
- **WHEN** a business service requests effective data policy for a user, resource, and action
- **THEN** the system returns the matching role policies, dimension scopes, combination rules, and user organization context

#### Scenario: No data policy configured
- **WHEN** no data policy is configured for the user's roles and requested resource/action
- **THEN** the runtime returns a restrictive result indicating no data range unless the user has an explicit all-data policy

### Requirement: Data Scope Query Enforcement
The system SHALL provide a shared service-side data-scope execution mechanism for Java business services. Query endpoints MUST declare their business resource and action, and business services MUST apply the resolved scope to the query.

#### Scenario: Query endpoint declares data scope
- **WHEN** a Controller method exposes a protected business query
- **THEN** it declares `@RequirePermission` for function access and `@RequireDataScope` for data-range access

#### Scenario: Restrictive policy returns empty query result
- **WHEN** the resolved data scope is restrictive
- **THEN** the business query returns an empty result instead of falling back to full data access

#### Scenario: User query self scope
- **WHEN** a user has `USER:QUERY` data scope `SELF`
- **THEN** the user list query is constrained to the current user's own record

#### Scenario: Runtime permission is separated from management permission
- **WHEN** a business service resolves the current user's effective data policy
- **THEN** it uses `/api/v1/data-policies/effective:GET`, not the data-permission management page permission `/api/v1/data-policies:GET`

### Requirement: Data Permission Frontend
The system SHALL provide a system management page for role data permission configuration. The page MUST allow selecting a role, resource, action, effect, combination mode, organization dimensions, scope types, and assigned organization units.

#### Scenario: Configure role data permission
- **WHEN** an authorized administrator opens the data permission page and selects a role
- **THEN** the page loads existing role data policies and allows saving policy changes if the user has the write permission

#### Scenario: Hide unauthorized data permission actions
- **WHEN** a user lacks data permission create, update, or delete API permission codes
- **THEN** the frontend hides or disables the corresponding actions and the backend rejects unauthorized calls
