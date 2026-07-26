## ADDED Requirements

### Requirement: Owner services self-host Action Runtime
Every owner service that exposes business-changing Global Actions SHALL host its own Action Runtime for definition lookup, payload validation, authorization context evaluation, owner guard checks, idempotency, audit emission, execution, and result normalization.

#### Scenario: Lowcode owner dispatches local form action
- **WHEN** a lowcode form submit candidate targets owner service `service-lowcode`
- **THEN** `service-lowcode` validates and dispatches the action inside its own runtime without routing through `service-action`

#### Scenario: Workflow owner starts process locally
- **WHEN** a process instance start action targets owner service `service-workflow-engine`
- **THEN** `service-workflow-engine` performs action validation and starts or signals the workflow through its local service and Temporal integration

### Requirement: Owner-hosted endpoints use standard Action contracts
Owner services SHALL expose standard HTTP contracts for action definitions, candidate validation, candidate dispatch, and optional action status/event lookup using `common-action` request and response models.

#### Scenario: Candidate validation endpoint
- **WHEN** a client posts an `ActionCandidate` to an owner-hosted validation endpoint
- **THEN** the owner returns `ActionCandidateValidationResult` with definition, schema, authorization, guard, confirmation, visibility, enabled, dispatchable, and refresh metadata

#### Scenario: Candidate dispatch endpoint
- **WHEN** a confirmed candidate is dispatched to the owning service
- **THEN** the owner returns `GlobalActionResult` with normalized status, action id, owner execution reference, target metadata, refresh scopes, bounded errors, and redacted summaries

### Requirement: Shared action starter contains no business execution logic
The shared Spring action runtime SHALL provide reusable annotations, interceptors, validators, status transition helpers, redaction helpers, and audit event contracts, but SHALL NOT contain owner-specific business actions or Temporal workflow logic.

#### Scenario: Service adds a new domain action
- **WHEN** a new service adds an action type such as `wms.inboundOrder.confirmReceive`
- **THEN** the executable business logic is implemented in the owner service while the shared runtime only supplies common validation, idempotency, status, and audit support

### Requirement: Owner runtime enforces idempotency before side effects
Owner-hosted dispatch SHALL enforce action idempotency by tenant, action type, and idempotency key before executing a side effect, while preserving owner-specific business idempotency checks.

#### Scenario: Duplicate owner dispatch
- **WHEN** the same tenant dispatches the same action type with the same idempotency key to the owner service
- **THEN** the owner returns the existing action result instead of repeating the business side effect

### Requirement: Owner runtime emits action audit events
Owner-hosted dispatch SHALL emit or persist bounded action audit events containing action id, action type, source, actor, target, normalized status, idempotency key, trace id, correlation id, owner execution reference, result summary, and redacted payload summary.

#### Scenario: Successful owner action audit
- **WHEN** an owner-hosted action completes successfully
- **THEN** the owner emits an action audit event that can be consumed by platform audit and document timeline projections

#### Scenario: Failed owner action audit
- **WHEN** an owner-hosted action fails validation, authorization, guard, dispatch, or execution
- **THEN** the owner emits a bounded failure event without storing raw secrets, raw sensitive values, or raw Prompt content

### Requirement: Central service-action is not an execution dependency
The system SHALL NOT require a running `service-action` process to validate, dispatch, or complete owner-hosted Global Actions after migration.

#### Scenario: Service-action removed from runtime
- **WHEN** lowcode, workflow, OpenAPI, frontend, and AI Action Clients dispatch supported actions after migration
- **THEN** the actions complete through owner-hosted runtimes while no gateway route or service-to-service call targets `service-action`

