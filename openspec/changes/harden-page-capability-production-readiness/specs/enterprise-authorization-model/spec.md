## MODIFIED Requirements

### Requirement: Tenant context is authoritative for authorization
The system SHALL evaluate all interactive and background authorization work against an authenticated or explicitly job-scoped tenant, MUST NOT rely on a hardcoded default tenant for production decisions or management projections, and SHALL preserve tenant identity in catalog, draft, release, evidence, simulation, compatibility, and cutover operations.

#### Scenario: Token validates with tenant
- **WHEN** the gateway validates a token for a tenant user
- **THEN** the propagated security context includes the user's effective tenant id used by authorization and data-scope decisions

#### Scenario: Cross-tenant decision denied
- **WHEN** a user from tenant `T1` requests access to tenant `T2` resources without platform-global authority
- **THEN** the decision is denied before grants, field rules, or domain guards can widen access

#### Scenario: Missing tenant fails closed
- **WHEN** a tenant-scoped role projection, simulation, or compatibility request has no authenticated tenant and no explicit platform-global job tenant
- **THEN** the system rejects the operation instead of reading tenant `default`

#### Scenario: Tenant-scoped manifest materialization
- **WHEN** a system manifest is synchronized for multiple active tenants
- **THEN** each tenant receives an isolated catalog version and no tenant's activation changes another tenant's active role releases

