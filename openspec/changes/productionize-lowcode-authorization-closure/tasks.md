## 1. Durable lowcode authorization lifecycle

- [x] 1.1 Add lowcode-owned authorization outbox, aggregate authorization lifecycle fields, indexes, and migration-safe defaults
- [x] 1.2 Persist immutable publish/offline authorization events in the same transaction as form and application lifecycle changes
- [x] 1.3 Implement a Spring-managed outbox dispatcher with claiming, idempotent delivery, bounded retry, acknowledgement persistence, and stale-event protection
- [x] 1.4 Gate form/application runtime discovery and direct access on the acknowledged current snapshot and expose tenant-scoped lifecycle diagnostics, retry, and reconcile APIs

## 2. Auth acknowledgement and lifecycle enforcement

- [x] 2.1 Add auth-owned synchronization receipts and extend the internal resource sync contract with event identity, operation, aggregate version, snapshot hash, and acknowledgement response
- [x] 2.2 Make resource synchronization atomically idempotent, reject event conflicts/stale versions, and invalidate authorization policy versions after lifecycle changes
- [x] 2.3 Ensure authorization decisions deny inactive generated resources while preserving historical grants and audit evidence

## 3. Strict semantic runtime authorization

- [x] 3.1 Remove legacy URL-permission fallback from published application page/action descriptor authorization
- [x] 3.2 Add strict-mode readiness diagnostics proving all runtime-visible lowcode snapshots have acknowledged semantic resources
- [x] 3.3 Add regression tests proving missing, unavailable, denied, stale, and offline semantic decisions fail closed in frontend descriptors and backend mutations

## 4. Atomic application authorization bundles

- [x] 4.1 Publish versioned lowcode application authorization blueprints and APPLICANT, APPROVER, DESIGNER, and ADMIN presets from registered owner metadata
- [x] 4.2 Add auth bundle preview/apply APIs that validate active tenant-scoped resources and atomically compile page dependencies, semantic grants, data scope, field policies, idempotency, and audit
- [x] 4.3 Add enterprise authorization workbench UI for application bundle selection, dry-run diff, atomic apply, lifecycle diagnostics, retry, and reconciliation

## 5. Production verification and rollout

- [x] 5.1 Add cross-service acceptance for publish acknowledgement, applicant/approver authorization, field/data/guard enforcement, revocation, offline, retry, drift, and reconciliation
- [x] 5.2 Add metrics, structured logs, operational documentation, deployment readiness gate, rollback guidance, and validate the OpenSpec change
