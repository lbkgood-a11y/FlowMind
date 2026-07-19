## ADDED Requirements

### Requirement: Closure effects execute through registered executors
The system SHALL execute business status updates, document creation, domain events, notifications, and Agent follow-up through registered executors referenced by the published ClosurePlan.

#### Scenario: Execute registered status update
- **WHEN** an APPROVED outcome triggers the expense report update-status effect
- **THEN** the closure executor invokes the code-registered executor for the effect executorKey

#### Scenario: Missing executor at runtime
- **WHEN** a published effect executor cannot be resolved at runtime
- **THEN** the effect fails with a diagnostic error and no arbitrary fallback execution occurs

### Requirement: Closure effects are idempotent
The system SHALL execute each effect with a stable idempotency key and SHALL not repeat side effects for duplicate delivery or retry.

#### Scenario: Retry status update
- **WHEN** the same update-status effect is retried after a timeout
- **THEN** the business status executor receives the same idempotency key and returns the original or safely repeated result without a duplicate state transition

### Requirement: Soft closure uses outbox and retry
The system SHALL persist soft closure effects to an outbox or equivalent durable queue and retry them according to configured retry policy.

#### Scenario: Soft event temporarily fails
- **WHEN** a domain event effect fails due to a temporary broker outage
- **THEN** the effect is marked FAILED or RETRYING with attempt count and next retry time, and the process approval result remains visible

#### Scenario: Retry succeeds
- **WHEN** a failed soft effect is retried successfully
- **THEN** the effect status becomes SUCCEEDED and the parent ClosureRecord is recalculated

### Requirement: Hard closure failure is visible and blocks business completion
The system SHALL not hide hard closure failure behind a normal completed business closure state.

#### Scenario: Hard payment preparation fails
- **WHEN** a hard OutcomeEffect such as create payment request fails after retries
- **THEN** the ClosureRecord status becomes FAILED or CLOSURE_FAILED and the instance detail shows that business closure is not complete

### Requirement: Closure execution records diagnostic details
The system SHALL persist effect request, result, failure category, last error, attempt count, operator context, traceId, and timestamps.

#### Scenario: Inspect failed effect
- **WHEN** an administrator opens a failed closure effect
- **THEN** the system displays the business action name, current status, attempts, last error, traceId, and retry availability

### Requirement: Manual handling is audited
If manual mark-as-handled is supported, the system SHALL record a dedicated immutable audit operation.

#### Scenario: Mark failed effect handled
- **WHEN** an authorized administrator manually marks a failed effect as handled
- **THEN** the system records operator, reason, timestamp, traceId, original failure, and resulting closure status
