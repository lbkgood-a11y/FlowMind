## 1. Shared Action Contract

- [x] 1.1 Add `common-action` module under `trio-base-common` and wire it into the root Maven build
- [x] 1.2 Define `GlobalActionRequest`, `GlobalActionResult`, `ActionActor`, `ActionTarget`, `ActionContext`, `ActionError`, and `ActionEventPayload`
- [x] 1.3 Define action enums for source, actor type, execution mode, audit level, status, terminal status, and error category
- [x] 1.4 Define `ActionDefinition`, `ActionGuardRequirement`, `ActionConfirmation`, `ActionRetryPolicy`, and `ActionSensitivePath` contracts
- [x] 1.5 Add action naming validation for namespaced action types such as `process.task.approve`
- [x] 1.6 Add action id, correlation id, request id, trace id, and idempotency helper utilities
- [x] 1.7 Add redaction utilities for sensitive payload paths and bounded error/result summaries
- [x] 1.8 Add unit tests for DTO serialization, enum values, naming validation, idempotency key normalization, and redaction

## 2. Service Action Module And Persistence

- [x] 2.1 Create `trio-base-services/service-action` Spring Boot module and add dependency management entries
- [x] 2.2 Add database migrations for `act_action_execution`, `act_action_event`, `act_action_definition_snapshot`, and `act_action_dispatch`
- [x] 2.3 Add indexes for action id, tenant id, action type, actor, source, target, status, trace id, correlation id, and idempotency key
- [x] 2.4 Implement MyBatis entities and mappers for action execution, events, definition snapshots, and dispatch records
- [x] 2.5 Implement `ActionExecutionRepository` with idempotent create and duplicate lookup by tenant, action type, and idempotency key
- [x] 2.6 Implement `ActionEventRepository` with ordered event append and query by action id
- [x] 2.7 Add service-action security configuration, audit filter integration, and common security context propagation
- [x] 2.8 Add service-action controller exception mapping to return normalized `ActionError` responses
- [x] 2.9 Add migration integration tests proving indexes, uniqueness, and rollback-compatible schema behavior

## 3. Action Registry And Runtime Pipeline

- [x] 3.1 Implement `ActionDefinitionRegistry` with registration, lookup, duplicate detection, and owner-service metadata
- [x] 3.2 Implement JSON Schema or Jackson validation for action payloads using registered payload schemas
- [x] 3.3 Implement `ActionPolicyChecker` that calls enterprise authorization decision APIs with action actor, target, tenant, and guard context
- [x] 3.4 Implement `ActionIdempotencyGuard` that returns existing action results for duplicate submissions
- [x] 3.5 Implement `ActionStatusService` for normalized lifecycle transitions and terminal-state enforcement
- [x] 3.6 Implement `ActionAuditRecorder` that writes redacted action summaries and links existing request audit trace context
- [x] 3.7 Implement `ActionDispatchService` for owner-service dispatch records and retryable dispatch failures
- [x] 3.8 Implement bounded result and error summary persistence with sensitive payload redaction
- [x] 3.9 Add pipeline tests for validation rejection, authorization rejection, duplicate idempotency reuse, successful dispatch, failed dispatch, and redaction

## 4. Action Facade APIs And Event Streaming

- [x] 4.1 Add `POST /api/v1/actions` to submit a Global Action and return normalized action status
- [x] 4.2 Add `GET /api/v1/actions/{actionId}` to return action execution detail
- [x] 4.3 Add `GET /api/v1/actions/{actionId}/events` to return ordered action events
- [x] 4.4 Add `GET /api/v1/actions/{actionId}/stream` as SSE for action lifecycle events
- [x] 4.5 Add action query API for authorized audit/admin views with filters by actor, source, target, status, trace id, correlation id, and idempotency key
- [x] 4.6 Add gateway routes for `service-action`
- [x] 4.7 Add API tests for submit, status query, event query, SSE event formatting, authorization failures, and public endpoint exclusions

## 5. Owner-Service Action Adapter Contract

- [x] 5.1 Add owner adapter request and response DTOs for service-action to dispatch to owner services
- [x] 5.2 Add common `ActionOwnerExecutor` and `ActionOwnerGuard` interfaces for owner services
- [x] 5.3 Add owner adapter controller base support for validation, guard result, execution result, and terminal status mapping
- [x] 5.4 Add service-to-service security headers and TraceId propagation for action dispatch
- [x] 5.5 Add dispatch retry behavior for transient owner-service failures without duplicate owner execution
- [x] 5.6 Add contract tests with a fake owner service proving guard failure, success, failure, and idempotent duplicate behavior

## 6. Lowcode Runtime Migration

- [x] 6.1 Register lowcode action definitions for `lowcode.form.create`, `lowcode.form.save`, `lowcode.form.submit`, and `lowcode.workflow.retry`
- [x] 6.2 Add `service-lowcode` owner executors for form create/save/submit using existing schema validation and field authorization rules
- [x] 6.3 Migrate `ApplicationRuntimeService.runAction` to submit or execute through Global Action semantics
- [x] 6.4 Replace `RuntimeActionRequest` usage with `GlobalActionRequest` mapping and mark the old DTO for deletion
- [x] 6.5 Replace `RuntimeActionResponse` usage with `GlobalActionResult` mapping and mark the old DTO for deletion
- [x] 6.6 Correlate lowcode form instance creation and update audit metadata with action id, trace id, source, and actor
- [x] 6.7 Update workflow launch binding from lowcode runtime to return normalized action status while preserving pending-workflow detail
- [x] 6.8 Add compatibility adapter tests proving old lowcode action endpoint dispatches an equivalent Global Action during migration
- [x] 6.9 Add lowcode runtime integration tests for valid submit, invalid schema, denied authorization, workflow start success, workflow start failure, retry, and duplicate idempotency

## 7. Workflow And Closure Migration

- [x] 7.1 Register process action definitions for `process.instance.start`, `process.task.approve`, `process.task.reject`, `process.task.transfer`, and `process.task.addSign`
- [x] 7.2 Register closure action definitions for `process.closure.effect.retry` and `process.closure.effect.markHandled`
- [x] 7.3 Add owner executors in `service-workflow-engine` for process start using existing `ProcessInstanceService`
- [x] 7.4 Add owner executors for task approve, reject, transfer, and add-sign using existing task guard and Temporal signal coordination
- [x] 7.5 Map existing task operation id semantics to Global Action idempotency and remove duplicate operation id generation after migration
- [x] 7.6 Add owner executors for closure retry and manual handled operations using existing closure effect services
- [x] 7.7 Propagate action id, trace id, actor, and correlation id into `TaskOperation`, `ProcessOutcome`, `ProcessClosure`, and `ClosureEffect` records
- [x] 7.8 Add compatibility adapter tests proving legacy task and closure endpoints dispatch equivalent Global Actions during migration
- [x] 7.9 Add workflow integration tests for approve, reject, transfer, add-sign, process start, closure retry, manual handled, duplicate idempotency, guard denial, and Temporal trace propagation

## 8. OpenAPI Integration Migration

- [x] 8.1 Register integration action definitions for `integration.orchestration.start`, `integration.invocation.stateChanging`, and `integration.callback.signal` where applicable
- [x] 8.2 Keep read-only synchronous invocation outside Global Action unless it creates a business audit or side effect
- [x] 8.3 Add owner executors in `service-openapi` for state-changing orchestration starts and retryable execution operations
- [x] 8.4 Correlate integration execution records, step attempts, outbox records, and sanitized audit events with action id and trace id
- [x] 8.5 Enforce admission limits before action owner execution dispatch for asynchronous orchestration actions
- [x] 8.6 Add integration tests for orchestration action start, duplicate idempotency, capacity rejection, sanitized failure evidence, and Temporal Activity trace propagation

## 9. Authorization And Audit Integration

- [x] 9.1 Extend authorization decision requests to carry action type, source, target, payload metadata, and action correlation
- [x] 9.2 Update `service-auth` decision logging to record action id, action type, source, target, and bounded action context
- [x] 9.3 Update owner-service guard composition to return structured guard results to the Action Runtime
- [x] 9.4 Extend platform audit log persistence or projection to expose action id, action type, action source, target, status, correlation id, and idempotency key
- [x] 9.5 Update audit log query APIs and frontend audit page filters for action fields
- [x] 9.6 Add authorization tests for unknown action target fail-closed, granted action allowed, explicit deny, guard failure, and sensitive payload exclusion
- [x] 9.7 Add audit tests for action-linked success, action-linked failure, HTTP-only operation detail, and redacted payload detail

## 10. Frontend Action Client Migration

- [x] 10.1 Add frontend action types and `action-client.ts` for submit, status query, event query, and SSE subscription
- [x] 10.2 Add `useActionDispatch` composable for confirmation, submit, normalized status, loading state, structured errors, and refresh hooks
- [x] 10.3 Add action status utilities and remove duplicated local operation status mapping where migrated
- [x] 10.4 Migrate lowcode runtime app submission and workflow retry to `dispatchAction`
- [x] 10.5 Migrate process instance start page to `dispatchAction`
- [x] 10.6 Migrate task approve, reject, transfer, and add-sign dialog to `dispatchAction`
- [x] 10.7 Migrate closure effect retry and manual handled controls to `dispatchAction`
- [x] 10.8 Migrate OpenAPI workbench or lifecycle state-changing operations to `dispatchAction`
- [x] 10.9 Add frontend tests for action client, dispatch composable, lowcode submit, task dialog actions, closure retry, structured errors, and SSE event handling

## 11. LUI And Agent Action Bridge

- [x] 11.1 Define `ActionCandidate` frontend and backend contracts for LUI and Agent proposed actions
- [x] 11.2 Add candidate validation against registered action definitions and payload schemas
- [x] 11.3 Add confirmation metadata handling for sensitive and critical actions
- [x] 11.4 Add Agent tool adapter that rejects unregistered action types and dispatches only validated Global Actions
- [x] 11.5 Add LUI intent adapter that produces candidates without mutating DOM or business data directly
- [x] 11.6 Add registered confirmation/result components with schema-validated props
- [x] 11.7 Add tests for candidate validation, confirmation required, unregistered tool rejection, agent attribution, LUI attribution, and dynamic component injection rejection

## 12. Compatibility Removal And Redundancy Cleanup

- [x] 12.1 Remove `RuntimeActionRequest` after all lowcode callers use Global Action
- [x] 12.2 Remove `RuntimeActionResponse` after all lowcode callers use Global Action
- [x] 12.3 Remove legacy frontend wrappers for `runRuntimeApplicationAction`, `startProcessInstance`, task mutation APIs, and closure mutation APIs after migrated call sites are gone
- [x] 12.4 Remove compatibility backend endpoints or convert them to non-public internal adapters according to the migration window decision
- [x] 12.5 Remove duplicate local action status mapping utilities that are replaced by global action status helpers
- [x] 12.6 Remove duplicate permission, idempotency, and audit branches made redundant by the Action Runtime pipeline
- [x] 12.7 Run repository-wide search for direct business-changing API calls that bypass `dispatchAction`
- [x] 12.8 Run repository-wide search for backend mutation endpoints that bypass Global Action or owner adapter registration

## 13. Governance, Documentation, And Verification

- [x] 13.1 Add ArchUnit rules preventing new business-changing controller methods from bypassing Global Action or owner action adapters
- [x] 13.2 Add frontend lint or test guard preventing new business mutation wrappers outside the Action Client
- [x] 13.3 Update developer documentation with Global Action naming, envelope, lifecycle, idempotency, audit, and frontend dispatch rules
- [x] 13.4 Update API documentation for action submit, status query, event query, SSE stream, and admin query endpoints
- [x] 13.5 Add end-to-end tests covering lowcode submit, process start, task approval, closure retry, integration orchestration, LUI candidate confirmation, and audit lookup by action id
- [x] 13.6 Run Maven tests for common, action, lowcode, workflow, auth, ops, and openapi modules
- [x] 13.7 Run frontend typecheck, unit tests, and targeted Playwright coverage for migrated pages
- [x] 13.8 Validate the OpenSpec change and mark tasks complete only after redundant code removal is verified
