## 1. Model Inventory and Classification

- [ ] 1.1 Inventory all persisted tables and entities with owner service, model category, tenant scope, mutability, data-scope modes, lifecycle, Global Action, and projection flags
- [ ] 1.2 Define the machine-readable model classification manifest and time-bounded waiver format
- [ ] 1.3 Decide and document the reserved global scope representation and prohibit null/blank-as-global semantics

## 2. Shared Contracts and Tooling

- [ ] 2.1 Add composable Java contracts or annotations for tenant, versioned, owned, organization-scoped, action-correlated, immutable, and projection models
- [ ] 2.2 Add reusable Flyway templates for mandatory columns, indexes, tenant-scoped unique keys, optimistic versions, and audit fields
- [ ] 2.3 Add PostgreSQL schema contract tests for required columns, types, nullability, constraints, indexes, and reserved scope values
- [ ] 2.4 Add ArchUnit checks that compare governed entity mappings with their declared model classification
- [ ] 2.5 Integrate report and blocking modes into Harness CI with explicit expiring waivers

## 3. Authorization Core Migration

- [ ] 3.1 Add non-null tenant identity and tenant-scoped role-code uniqueness to roles, then backfill existing rows
- [ ] 3.2 Add tenant identity and same-tenant relation validation to user-role and other permission-subject relation tables
- [ ] 3.3 Align resource, action, field, grant, data policy, field policy, and guard policy tables with deterministic keys, status, versions, and audit fields
- [ ] 3.4 Add database and service validation that rejects cross-tenant subject, role, resource, action, grant, and policy references
- [ ] 3.5 Migrate authorization audit and decision records to the mandatory evidence set without storing sensitive raw values

## 4. Owner-Service Model Migration

- [ ] 4.1 Add registered typed owner and organization columns to SELF/ORG-protected business facts and backfill existing data
- [ ] 4.2 Add action, trace, idempotency, and workflow correlation fields to owner-hosted state-changing models where applicable
- [ ] 4.3 Add source event, replay cursor, projection version, tenant, and freshness fields to governed read models
- [ ] 4.4 Update data-scope metadata registration so missing declared owner or organization columns fail closed

## 5. Verification and Rollout

- [ ] 5.1 Add migration tests for clean database creation and upgrade from the previous stable schema
- [ ] 5.2 Add tenant collision, cross-tenant relation, concurrent version, data-scope compilation, audit redaction, and projection replay tests
- [ ] 5.3 Run model contract checks in report mode, resolve or waive every violation, then enable blocking mode for authorization core tables
- [ ] 5.4 Run backend tests, architecture tests, Flyway validation, OpenSpec strict validation, and affected frontend typechecks
