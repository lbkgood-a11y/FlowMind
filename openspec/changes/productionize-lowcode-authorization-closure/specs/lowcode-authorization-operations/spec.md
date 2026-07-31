## ADDED Requirements

### Requirement: Lowcode authorization publication is durable and observable
The lowcode service SHALL commit an immutable authorization publication event with every form or application publish/offline lifecycle transition and SHALL deliver it through an idempotent retryable outbox.

#### Scenario: Publish commits pending authorization event
- **WHEN** a designer publishes a valid lowcode form or application
- **THEN** the owner transaction stores the immutable version, snapshot hash, pending lifecycle state, and one outbox event without exposing the aggregate to runtime

#### Scenario: Delivery retries after auth outage
- **WHEN** authorization synchronization fails transiently
- **THEN** the outbox records the failure, schedules a bounded retry, exposes operational status, and keeps the aggregate unavailable to runtime

#### Scenario: Duplicate event is delivered
- **WHEN** the dispatcher repeats an already acknowledged event with the same snapshot hash
- **THEN** auth returns the original acknowledgement without duplicating resources, actions, receipts, or audit side effects

### Requirement: Runtime requires exact authorization acknowledgement
The lowcode runtime SHALL expose and execute only immutable application and form versions whose current authorization snapshot is acknowledged and active.

#### Scenario: Pending application is queried
- **WHEN** an application's authorization publication remains pending or failed
- **THEN** the application is absent from runtime discovery and direct runtime access fails closed

#### Scenario: Acknowledged application is queried
- **WHEN** auth acknowledges the exact current application and participating form snapshot hashes
- **THEN** lowcode promotes the lifecycle state and runtime evaluates semantic decisions for discovery and execution

#### Scenario: Stale acknowledgement is present
- **WHEN** the stored acknowledgement belongs to an older aggregate version or different snapshot hash
- **THEN** runtime rejects it and diagnostics report drift

### Requirement: Lowcode application authorization bundles are owner-defined
The system SHALL expose versioned authorization blueprints and presets for published lowcode applications and SHALL allow auth to compile only registered active resources, actions, fields, guards, and runtime page dependencies.

#### Scenario: Applicant preset is previewed
- **WHEN** an administrator previews the `APPLICANT` preset for a leave application
- **THEN** the diff contains runtime page access, application VIEW, and owner-declared form VIEW, CREATE, EDIT, DELETE, and SUBMIT selections without unregistered actions

#### Scenario: Approver preset is applied
- **WHEN** an administrator applies the `APPROVER` preset to a role
- **THEN** auth atomically stores application VIEW, form VIEW, APPROVE and REJECT grants plus selected data/field policies and one bundle audit record

#### Scenario: Bundle references inactive resource
- **WHEN** a bundle references an offline, pending, missing, or foreign-tenant resource
- **THEN** auth rejects the entire bundle without partial grants

### Requirement: Operators can diagnose and reconcile authorization lifecycle
Authorized operators SHALL be able to inspect publication delivery, auth receipt, resource revision, bundle grant, and drift state and SHALL be able to request idempotent retry or reconciliation without editing owner/auth tables directly.

#### Scenario: Missing auth resource is reconciled
- **WHEN** diagnostics find an acknowledged published lowcode aggregate whose auth resource is missing or has a mismatched revision
- **THEN** lowcode records a repair event for the exact expected snapshot and reports progress through the same outbox lifecycle

#### Scenario: Unauthorized retry is attempted
- **WHEN** a caller without lowcode lifecycle repair permission requests retry or reconciliation
- **THEN** the request is rejected and no outbox state changes

### Requirement: Production acceptance proves the complete generated-form authorization loop
The release pipeline SHALL run cross-service acceptance covering publish, acknowledgement, bundle authorization, runtime decisions, owner enforcement, revocation, offline, retry, and reconciliation.

#### Scenario: Leave application happy path and denial path
- **WHEN** a leave form/application is published, an applicant bundle and approver bundle are applied, and representative users execute the workflow
- **THEN** the applicant can create and submit only permitted data, the eligible approver can approve or reject within scope, self-approval and unauthorized fields are denied, and ungranted users cannot discover or execute the application

#### Scenario: Revoke and offline are verified
- **WHEN** application VIEW is revoked and the application is subsequently offlined
- **THEN** discovery and execution deny immediately after each transition while historical grants and audits remain inspectable

