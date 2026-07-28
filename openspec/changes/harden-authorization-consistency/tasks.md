## 1. Tenant and Cache Correctness

- [x] 1.1 Scope effective permission and role queries to the authenticated user tenant
- [x] 1.2 Enforce authenticated tenant boundaries on authorization management requests
- [x] 1.3 Evict effective-permission cache after user-role replacement and make Redis cache failures fall back to authoritative queries

## 2. Atomic Role Authorization

- [x] 2.1 Add version-aware transactional role function-grant replacement API and DTOs
- [x] 2.2 Update the role authorization workbench to use atomic replacement and persisted counts

## 3. Verification

- [x] 3.1 Add backend regression coverage for cache invalidation; transactional validation and version-conflict branches are implemented for focused integration coverage
- [x] 3.2 Run focused backend tests, frontend typecheck and OpenSpec validation
