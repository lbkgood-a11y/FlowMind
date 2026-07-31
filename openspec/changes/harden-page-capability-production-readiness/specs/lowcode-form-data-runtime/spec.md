## MODIFIED Requirements

### Requirement: Form instance queries compile data scope safely
The lowcode service SHALL persist verified organization ownership and provenance for form instances, SHALL compile authorization data-scope decisions into tenant-safe list and detail predicates using that ownership, and SHALL fail closed when ownership or scope evidence cannot be safely compiled.

#### Scenario: Self scope filters submitted instances
- **WHEN** the decision returns `SELF` query scope for a form
- **THEN** list and detail operations return only instances submitted or owned by the current user in the current tenant

#### Scenario: Organization scope filters instances
- **WHEN** the decision returns organization-scoped access with resolved organization ids
- **THEN** list and detail operations return only matching tenant instances whose verified `owner_org_id` is contained in those resolved organizations

#### Scenario: New submission captures organization owner
- **WHEN** an authenticated user with a primary organization submits a form instance
- **THEN** the lowcode service stores the primary organization from governed subject evidence and ignores any client-supplied ownership value

#### Scenario: Existing ownership is reconciled
- **WHEN** an existing instance has unresolved ownership and the organization owner service resolves the submitter's primary organization
- **THEN** an idempotent reconciliation job records the verified organization and provenance without changing business form data

#### Scenario: Unsupported scope fails closed
- **WHEN** the decision returns a scope that lowcode cannot compile for form instance storage
- **THEN** the lowcode service returns no records or an authorization error rather than widening access

#### Scenario: Missing ownership fails closed
- **WHEN** organization-scoped access evaluates an instance whose organization ownership is unresolved
- **THEN** the instance is excluded from lists and denied on detail access
