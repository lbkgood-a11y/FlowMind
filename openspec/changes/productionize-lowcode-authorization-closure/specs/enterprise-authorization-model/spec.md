## MODIFIED Requirements

### Requirement: Authorization decisions evaluate grants fail-closed
The system SHALL expose internal single and batch authorization decision APIs that evaluate tenant boundary, subject roles, direct grants, registered active resources, action grants, deny precedence, data scope, field rules, required guard templates, and Global Action context. Published lowcode runtime callers MUST NOT widen a denied, missing, stale, unavailable, or unknown semantic decision through URL permission fallback.

#### Scenario: Allow granted action
- **WHEN** a user has an active role grant for resource `LOWCODE_FORM:EXPENSE` and action `APPROVE`
- **THEN** the decision API returns an allowed function decision with the matched grant, resource, action, tenant, subject, Global Action context, and policy versions

#### Scenario: Deny unknown resource
- **WHEN** a caller requests a decision for an unregistered resource or unregistered Global Action target
- **THEN** the decision API returns denied with a fail-closed reason and does not infer permission from request paths or legacy URL permissions

#### Scenario: Deny inactive lowcode resource
- **WHEN** a caller requests a decision for a pending, failed, drifted, or offline lowcode resource
- **THEN** the decision API denies it even when historical allow grants exist

#### Scenario: Deny takes precedence
- **WHEN** one matching grant allows an action and another active matching grant denies the same action
- **THEN** the decision API returns denied and includes the deny grant in the explanation

#### Scenario: Batch action decision
- **WHEN** a runtime page requests decisions for multiple document actions, fields, and Global Action definitions
- **THEN** the batch decision API returns one stable decision entry per requested resource/action without requiring multiple network round trips

## ADDED Requirements

### Requirement: Authorization resource lifecycle synchronization is acknowledged and idempotent
The authorization service SHALL store an auth-owned receipt for each lowcode resource lifecycle event and SHALL acknowledge the resource revision and snapshot hash only after the resource, actions, fields, guards, and lifecycle state are committed.

#### Scenario: First publication event is applied
- **WHEN** auth receives a valid current lowcode publication event
- **THEN** it atomically synchronizes the registered metadata, records the receipt, advances policy/resource versions, and returns an acknowledgement

#### Scenario: Event id is reused with another hash
- **WHEN** a caller reuses an existing event id with a different snapshot hash or aggregate identity
- **THEN** auth rejects the event, preserves the original receipt, and records a conflict diagnostic

#### Scenario: Stale aggregate event arrives
- **WHEN** an older aggregate version arrives after a newer acknowledged version
- **THEN** auth rejects the stale transition without reactivating or overwriting the newer resource state

### Requirement: Lowcode authorization bundles compile atomically
The authorization service SHALL preview and atomically apply an owner-defined lowcode application authorization bundle to a role using active registered resources and SHALL record an idempotency result and audit diff.

#### Scenario: Bundle dry run
- **WHEN** an administrator previews a registered application preset or constrained custom selection
- **THEN** auth returns the page capability selections, semantic grants, data policies, field policies, additions, removals, dependencies, and validation findings without writing state

#### Scenario: Bundle apply succeeds
- **WHEN** the administrator applies the validated preview with an idempotency key
- **THEN** auth writes all compiled authorization facts and audit metadata in one transaction and returns the resulting authorization version

#### Scenario: Bundle apply partially fails
- **WHEN** any resource, action, field, tenant, dependency, or policy validation fails
- **THEN** auth rolls back the whole bundle and leaves the role's prior authorization unchanged

