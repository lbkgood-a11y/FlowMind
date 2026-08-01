## 1. Shared field-governance standard

- [x] 1.1 Add typed Owner enforcement manifest, capability, readiness, and adapter contracts to common modules
- [x] 1.2 Extend shared helpers for typed read filtering and submitted-field write validation with fail-closed semantics
- [x] 1.3 Document the mandatory integration sequence for list, detail, export, mutation, workflow, and AI/tool boundaries

## 2. Registry gates and diagnostics

- [x] 2.1 Replace the hard-coded verified adapter allowlist with Owner enforcement declarations and persisted capability flags
- [x] 2.2 Reject field policies whose resource, field, Owner, or required read/write enforcement is not ready
- [x] 2.3 Add field-governance readiness diagnostics to authorization resource responses and the resource registration center

## 3. Existing Owner adoption

- [x] 3.1 Integrate USER list/detail read masking and hiding plus create/update write validation in service-auth
- [x] 3.2 Integrate ORG_UNIT list/tree/detail reads and create/update write validation in service-org
- [x] 3.3 Align lowcode forms and custom document runtime with the shared declaration contract and retain their existing enforcement
- [x] 3.4 Correct existing resource Owner/enforcement metadata and ensure every current field-bearing resource reports READY

## 4. Production gates and verification

- [x] 4.1 Add contract tests for USER phone masking/hiding and write denial, including ADMIN role policies
- [x] 4.2 Add contract tests for ORG_UNIT, lowcode, custom documents, and rejection of unready field policy configuration
- [x] 4.3 Add an architecture/readiness test that fails for any field-bearing resource without verified enforcement coverage
- [x] 4.4 Run affected builds and tests, validate OpenSpec, apply migrations, restart services, and verify runtime decisions
