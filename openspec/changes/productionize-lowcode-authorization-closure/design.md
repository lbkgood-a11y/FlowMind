## Context

`service-lowcode` currently synchronizes form and application authorization resources synchronously before marking a draft published. Runtime descriptor generation and instance mutations call semantic authorization decisions, but descriptor generation can still fall back to legacy URL permission strings. Administrators grant semantic resources in the enterprise authorization workbench and separately grant runtime page capabilities. The pieces work independently, but publication has a cross-service consistency window and there is no single operational view proving that a generated application is publish-ready, granted, executable, revocable, and safely offline.

The design must preserve service ownership: lowcode owns form/application lifecycle and the outbox; auth owns resources, grants, decisions, page capability compilation, and grant audit. No shared tables or cross-service transactions are introduced.

## Goals / Non-Goals

**Goals:**

- Make semantic authorization the only runtime authority for published lowcode content and fail closed on missing, stale, inactive, or unavailable decisions.
- Make resource publication/offline synchronization durable, idempotent, retryable, observable, and reconcilable.
- Prevent runtime discovery or mutation until the exact published resource snapshot is acknowledged by auth.
- Allow an administrator to grant an owner-defined application bundle, including runtime page access and form actions, in one auth transaction.
- Prove allow, deny, revoke, field, data-scope, guard, retry, reconciliation, and offline behavior with cross-service acceptance tests.

**Non-Goals:**

- Replacing the enterprise authorization decision engine or role model.
- Allowing lowcode designers or administrators to invent arbitrary resources, actions, guards, or permission URLs.
- Implementing distributed XA transactions between lowcode and auth.
- Automatically granting a newly published application to any role.

## Decisions

### 1. Use a lowcode-owned transactional outbox and acknowledged publication state

Form/application lifecycle writes and an immutable authorization publication event are committed in one lowcode database transaction. The aggregate enters `AUTHORIZATION_PENDING` for publish or `OFFLINE_PENDING` for offline. A Spring-managed dispatcher claims due outbox rows with bounded retries, sends the exact snapshot to auth using an idempotency key, and records the returned resource revision/hash. Only an acknowledged publish becomes `PUBLISHED`; an acknowledged offline becomes `OFFLINE`.

This is chosen over synchronous remote calls inside a database transaction because a successful remote write cannot be rolled back with the local transaction. Temporal is not required for this short owner-local delivery loop; the outbox is the durable boundary, while a future multi-service business workflow may still use Temporal.

### 2. Auth synchronization returns an idempotent acknowledgement

The internal sync contract accepts `eventId`, `aggregateType`, `aggregateId`, `aggregateVersion`, `operation`, and `snapshotHash`. Auth stores the receipt in an auth-owned table and returns the stable resource revision. Replaying the same event and hash returns the original acknowledgement; reusing an event id with a different hash is rejected and audited.

### 3. Runtime visibility requires an acknowledged exact snapshot

Runtime queries require both the business lifecycle state and authorization acknowledgement for the current immutable version/hash. Pending, failed, superseded, or offline resources are not discoverable and all action execution fails closed. Reconciliation compares lowcode expected snapshots with auth resource lifecycle/revision and enqueues a new repair event rather than editing auth tables directly.

### 4. Remove legacy URL permission fallback after deployment compatibility gating

Runtime descriptors and action execution rely only on semantic `LOWCODE_APP` and `LOWCODE_FORM` decisions. Legacy permission strings remain readable during migration but are not consulted for published runtime authorization. Deployment uses a readiness gate that verifies all active published aggregates have acknowledged resources before enabling strict mode; strict mode is the production default.

### 5. Owner-defined authorization bundles compile to auth-owned grants

Lowcode publishes an application authorization blueprint in resource metadata: runtime page capability dependency, app VIEW, participating form resources, supported action presets, fields, and guards. The enterprise authorization API accepts a bundle request referencing a registered application resource, role, preset/custom selected actions, data scope, and field policies. Auth resolves only active registered metadata, validates dependencies, and writes the resulting page-capability selections/grants, semantic grants, policies, idempotency record, and audit entry in one auth transaction.

Presets are `APPLICANT`, `APPROVER`, `DESIGNER`, and `ADMIN`; custom selections are constrained to registered actions. A dry-run endpoint returns the compiled diff before apply.

### 6. Revocation and offline are lifecycle-aware

Grant revocation takes effect through the existing decision engine and cache/version invalidation. Offline synchronization marks generated resources inactive; decisions deny inactive resources even if historical grants remain, preserving audit history without deleting grants. Re-publishing a new immutable version reuses stable resource codes while advancing the acknowledged revision.

### 7. Operational diagnostics are read-only except explicit retry/reconcile commands

Lowcode exposes tenant-scoped publication delivery status; auth exposes receipt/resource revision and bundle audit. The workbench joins them through APIs and shows `PENDING`, `SYNCED`, `RETRYING`, `FAILED`, `DRIFTED`, or `OFFLINE`. Retry and reconcile are explicit owner operations, permission protected, idempotent, and audited.

## Risks / Trade-offs

- [Asynchronous publish changes immediate API semantics] → return lifecycle status and pollable operation metadata; UI shows “权限资源同步中” and enables runtime only after acknowledgement.
- [Strict mode can hide existing applications lacking resources] → ship diagnostics and reconciliation first, gate strict-mode activation on zero unresolved active aggregates.
- [Duplicate or reordered events] → idempotency receipts, aggregate version comparison, immutable hashes, and stale-event rejection.
- [Outbox backlog delays publication] → bounded exponential retry, metrics/alerts, manual retry, and no runtime exposure while pending.
- [Bundle complexity could overgrant] → owner-defined presets, auth-side dependency compilation, dry-run diff, deny precedence, and transactional audit.
- [Auth unavailable blocks new publication] → existing acknowledged applications continue; new or changed applications remain safely pending.

## Migration Plan

1. Deploy auth receipt/bundle APIs and inactive-resource decision enforcement while retaining current lowcode behavior.
2. Deploy lowcode outbox schema, dispatcher, lifecycle diagnostics, and dual-write publication events; reconcile all active published forms/apps until every snapshot is acknowledged.
3. Enable strict semantic runtime mode and remove legacy fallback from code after the readiness gate passes.
4. Enable bundle grant UI and migrate pilot roles using dry-run/apply; verify decision previews and audit.
5. Run cross-service production acceptance, then make strict mode non-configurable in production.

Rollback keeps acknowledged resources and grants. Lowcode can stop dispatching new events while existing published versions remain usable; it must not re-enable legacy fallback in production. Failed new publications remain pending and can be retried after forward recovery.

## Open Questions

- None. Preset contents are owner-defined and versioned in application authorization metadata; production defaults are specified in the capability contract.
