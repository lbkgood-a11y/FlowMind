## MODIFIED Requirements

### Requirement: Published forms synchronize authorization resources
The lowcode service SHALL publish and offline tenant-scoped authorization resources, actions, fields, and guard templates through a lowcode-owned transactional outbox and SHALL make a form runtime-visible only after `service-auth` acknowledges the exact immutable snapshot.

#### Scenario: Publish form registers resources
- **WHEN** an authorized designer publishes form key `expense` for tenant `T1`
- **THEN** the owner transaction records a pending publication event for view, create, edit, submit, approve, reject, export, design, publish, offline, field read, and field write metadata and the form becomes runtime-visible only after acknowledgement

#### Scenario: Authorization sync is idempotent
- **WHEN** the same form publication event and snapshot hash are retried
- **THEN** the registry returns the stable acknowledgement and keeps one resource revision without duplicate resources, actions, receipts, or audit side effects

#### Scenario: Authorization sync fails
- **WHEN** form publication cannot synchronize required authorization resources
- **THEN** the form remains authorization-pending, is unavailable to runtime, exposes retry diagnostics, and can be safely reconciled without manual table edits

#### Scenario: Form is offlined
- **WHEN** an authorized designer offlines a published form
- **THEN** an offline event is durably delivered and the form remains unavailable while auth marks its semantic resource inactive without deleting historical grants

