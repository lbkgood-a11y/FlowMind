## ADDED Requirements

### Requirement: Authorization operations require an authoritative tenant
The system SHALL require an authenticated or explicitly job-scoped tenant for every catalog, draft, release, projection, compatibility, and cutover operation and MUST NOT convert missing tenant context into tenant `default`.

#### Scenario: Interactive request lacks tenant
- **WHEN** an authorization request has neither authenticated tenant context nor platform-global authority with an explicit target tenant
- **THEN** the system denies the request before reading grants or catalog data

#### Scenario: Background manifest synchronization
- **WHEN** a background job synchronizes system page manifests
- **THEN** it materializes the tenant-neutral manifest separately for each explicitly enumerated active tenant

### Requirement: Compatibility assessment is set-based and tenant isolated
The compatibility dashboard SHALL compute catalog readiness, migration coverage, decision equivalence, drift, publication failures, rollback, and cutover blockers using a bounded number of tenant-scoped database queries independent of role count.

#### Scenario: Large tenant assessment
- **WHEN** a tenant with thousands of roles requests compatibility assessment
- **THEN** the service performs no evidence or grant query inside a per-role loop and returns aggregate counters plus bounded role details

#### Scenario: Same role codes in two tenants
- **WHEN** tenants `T1` and `T2` have overlapping role or capability codes
- **THEN** each dashboard contains only releases, evidence, grants, reviews, drift, and audit data belonging to its requested tenant

### Requirement: Production acceptance uses real PostgreSQL persistence
The release pipeline SHALL provide a required production-acceptance profile that applies the real Flyway migrations to PostgreSQL and fails rather than skips when the database is unavailable.

#### Scenario: Acceptance database unavailable
- **WHEN** the production-acceptance profile cannot connect to its configured PostgreSQL database
- **THEN** the build fails and no production acceptance result is reported

#### Scenario: Multi-tenant cutover acceptance
- **WHEN** the acceptance suite publishes isolated role releases for two tenants and evaluates cutover
- **THEN** it verifies immutable evidence, exact runtime equivalence, zero unintended expansion, tenant isolation, and server-side cutover gating

### Requirement: Cutover evidence cannot be bypassed or reversed through normal management APIs
The system SHALL repeat compatibility assessment on every page-capability cutover request and SHALL reject normal API attempts to reopen legacy writes after a successful cutover.

#### Scenario: UI state is bypassed
- **WHEN** a caller directly invokes the management-mode API while compatibility blockers exist
- **THEN** the server rejects cutover with the current business blockers

#### Scenario: Downgrade after cutover
- **WHEN** a tenant in `PAGE_CAPABILITY` requests `MIGRATION` or `LEGACY`
- **THEN** the system rejects the request and leaves the tenant in `PAGE_CAPABILITY`

### Requirement: Production diagnostics expose unresolved ownership and scale evidence
The administrator diagnostics SHALL expose unresolved lowcode ownership counts, compatibility query statistics, and tenant-scoped acceptance status without exposing sensitive record payloads.

#### Scenario: Ownership reconciliation incomplete
- **WHEN** lowcode instances remain without verified organization ownership
- **THEN** the dashboard identifies the unresolved count as an organization-scope readiness blocker

