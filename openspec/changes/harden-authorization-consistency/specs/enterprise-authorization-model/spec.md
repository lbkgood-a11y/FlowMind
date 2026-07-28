## MODIFIED Requirements

### Requirement: Tenant context is authoritative for authorization
The system SHALL evaluate runtime and management authorization against the authenticated user's real tenant context, SHALL include tenant predicates in effective grant queries, and MUST NOT trust a caller-supplied tenant identifier unless the caller has platform-global administrative authority.

#### Scenario: Token validates with tenant
- **WHEN** the gateway validates a token for a tenant user
- **THEN** the propagated security context includes the user's effective tenant id used by authorization, menu projection, and data-scope decisions

#### Scenario: Cross-tenant decision denied
- **WHEN** a user from tenant `T1` requests access to tenant `T2` resources without platform-global authority
- **THEN** the decision is denied before grants, field rules, or domain guards can widen access

#### Scenario: Cross-tenant management parameter rejected
- **WHEN** a tenant administrator supplies a different tenant id to a grant, resource, policy, or role authorization management operation
- **THEN** the system rejects the request and does not read or mutate the other tenant's authorization data

#### Scenario: Platform administrator targets tenant
- **WHEN** a platform administrator explicitly manages authorization for tenant `T2`
- **THEN** the operation is scoped to `T2` and all referenced roles, resources, actions, and policies are validated inside `T2`
