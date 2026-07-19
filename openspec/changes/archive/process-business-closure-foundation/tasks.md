## 1. Catalog Schema And Seed Data

- [x] 1.1 Add database migrations for business object catalog core tables with tenant scope, lifecycle status, version, and audit fields
- [x] 1.2 Add catalog child tables for statuses, forms, permissions, actions, events, Agent follow-up actions, and default templates
- [x] 1.3 Add uniqueness constraints for effective business object type, status codes, action codes, event codes, and Agent action codes per tenant/version
- [x] 1.4 Add migrations for ProcessOutcome, ClosureRecord, ClosureEffect, and closure outbox records
- [x] 1.5 Seed a GLOBAL PUBLISHED expense_report catalog with DRAFT, IN_APPROVAL, APPROVED, REJECTED, and required permissions/actions/events/Agent actions
- [x] 1.6 Add repository integration tests for catalog lifecycle, tenant override resolution, uniqueness, and OFFLINE behavior

## 2. Catalog Domain And APIs

- [x] 2.1 Implement catalog entities, mappers/repositories, and descriptor DTOs in service-workflow-engine
- [x] 2.2 Implement effective catalog resolution using GLOBAL defaults plus tenant override
- [x] 2.3 Implement catalog lifecycle validation for DRAFT, PUBLISHED, and OFFLINE records
- [x] 2.4 Add read APIs for published business objects, object detail, statuses, forms, permissions, actions, events, and Agent actions
- [x] 2.5 Ensure catalog APIs return business display names and hide executor internals from normal designer responses
- [x] 2.6 Add API tests for tenant-scoped catalog reads and unpublished/offline filtering

## 3. Executor Registries

- [x] 3.1 Add BusinessActionExecutor, ClosureEffectExecutor, and AgentFollowUpExecutor registry interfaces
- [x] 3.2 Implement startup diagnostics for catalog executorKeys that have no registered code executor
- [x] 3.3 Add publish-time hard failure when a selected action or Agent action lacks a registered executor
- [x] 3.4 Implement expense_report update-status executor with timeout, TraceId propagation, idempotency key, and explicit error mapping
- [x] 3.5 Implement expense_report create-document executor for create-and-launch fixtures
- [x] 3.6 Add tests proving URL, SQL, script, dynamic class, and free Prompt execution definitions are rejected

## 4. Process Package Model And Publication

- [x] 4.1 Extend process package definition DTOs with business object binding, launch policy, permission policy, closure policy, and Agent follow-up policy
- [x] 4.2 Add snapshot fields or snapshot storage for BusinessBindingSnapshot, LaunchPlan, PermissionPlan, ClosurePlan, and AgentFollowUpPlan
- [x] 4.3 Implement publish validation for business object status, selected forms, businessRef source, status mappings, action schemas, permission mappings, and executorKeys
- [x] 4.4 Compile closurePolicy into immutable ClosurePlan during process publication
- [x] 4.5 Preserve compatibility for existing pure workflow packages without business object binding
- [x] 4.6 Add publication tests proving catalog changes after publication do not affect published process versions

## 5. Launch Policy Runtime

- [x] 5.1 Implement existing-document launch with businessRef supplied by page context or API input
- [x] 5.2 Implement create-and-launch through the registered create-document executor
- [x] 5.3 Validate business object submit permission, process launch policy, and allowed document status before starting a process
- [x] 5.4 Execute hard StartEffect such as IN_APPROVAL status update before process startup
- [x] 5.5 Add launch idempotency so repeated create-and-launch requests cannot create duplicate business documents or process instances
- [x] 5.6 Add integration tests for allowed launch, disallowed status, missing permission, create failure, StartEffect failure, and duplicate launch retries

## 6. Permission Binding Runtime

- [x] 6.1 Bind business object action codes to existing RBAC permission codes during effective catalog resolution
- [x] 6.2 Enforce combined business view permission and process visibility rules on instance detail and closure detail APIs
- [x] 6.3 Preserve task candidate and assignee authorization as mandatory for task handling
- [x] 6.4 Enforce retry-closure permission on closure retry and manual handling APIs
- [x] 6.5 Add tests for submit, view, approve, retry closure, and unauthorized access edge cases

## 7. Outcome And Closure Runtime

- [x] 7.1 Implement ProcessOutcome creation when a process reaches APPROVED, REJECTED, TERMINATED, SUSPENDED, or CLOSURE_FAILED result states
- [x] 7.2 Enforce unique ProcessOutcome per process instance and outcome version
- [x] 7.3 Create ClosureRecord and ClosureEffect records from the published ClosurePlan
- [x] 7.4 Add outcome and closure events for data consumers without exposing Workflow internal tables
- [x] 7.5 Propagate TraceId, initiator, last operator, tenant, businessRef, and processRef into Outcome and Closure records
- [x] 7.6 Add tests for approved, rejected, suspended, duplicate outcome, and trace propagation scenarios

## 8. Closure Effect Execution

- [x] 8.1 Implement durable closure outbox and background dispatcher for soft effects
- [x] 8.2 Execute hard OutcomeEffects synchronously through Activities or controlled closure runtime paths without violating Temporal determinism
- [x] 8.3 Implement idempotency key generation and pass-through for every closure effect
- [x] 8.4 Implement retry policy, attempt counters, next retry time, last error, failure category, and result recording
- [x] 8.5 Recalculate ClosureRecord status from child effect statuses
- [x] 8.6 Add authorized retry API for failed closure effects
- [x] 8.7 Add audited manual mark-as-handled API only if enabled by policy
- [x] 8.8 Add tests for soft failure/retry success, hard failure, duplicate delivery, missing executor, manual handling audit, and partial failure display state

## 9. Agent Follow-Up Runtime

- [x] 9.1 Render Agent follow-up actions from catalog metadata and parameter schemas
- [x] 9.2 Validate Agent follow-up permissions and parameter schemas during publication
- [x] 9.3 Execute Agent follow-up as closure effects through registered AgentFollowUpExecutor implementations
- [x] 9.4 Reject free Prompt text and arbitrary tool-call JSON in designer and publication APIs
- [x] 9.5 Persist Agent result summaries, failures, traceId, and retry metadata for instance detail display
- [x] 9.6 Add tests for configured Agent follow-up success, parameter validation failure, authorization failure, and retry after soft failure

## 10. Designer Workbench

- [x] 10.1 Replace standalone JSON-first process creation with a business-object-first designer workbench flow
- [x] 10.2 Add business object selection step backed by catalog APIs
- [x] 10.3 Add launch mode configuration for existing-document launch and create-and-launch
- [x] 10.4 Add form selection and businessRef source picker using published form schema fields and context sources
- [x] 10.5 Add condition builder for field/operator/value branch configuration while preserving safe expression output
- [x] 10.6 Add participant selectors that display business names for roles, departments, and users instead of requiring raw codes in the normal flow
- [x] 10.7 Add closure policy configuration using business statuses, actions, events, notifications, and Agent follow-up selectors
- [x] 10.8 Add read-only technical preview for generated JSON, codes, executorKeys, and plans
- [x] 10.9 Add designer component tests for selection-first configuration and blocked technical editing

## 11. Designer Visualization And Validation

- [x] 11.1 Keep and adapt the X6 process graph visualization for node, edge, participant, and condition configuration
- [x] 11.2 Add document status graph visualization for allowed launch statuses, in-approval state, approved/rejected states, and hard failure state
- [x] 11.3 Add closure action chain visualization grouped by outcome
- [x] 11.4 Add permission matrix visualization for launch, view, task handling, closure retry, and Agent follow-up
- [x] 11.5 Add completion checks for business object binding, form binding, launch policy, permission mappings, outcome effects, Agent effects, and required effect parameters
- [x] 11.6 Add business-language validation errors that locate issues to the relevant wizard step, graph node, form field, permission, or effect
- [ ] 11.7 Add frontend tests for all four visualizations and validation error targeting

## 12. Instance Detail And Operations UI

- [x] 12.1 Add API responses for closure status, ProcessOutcome, ClosureRecord, ClosureEffect details, and retry availability
- [x] 12.2 Show approval result separately from business closure status on process instance detail
- [x] 12.3 Show successful, failed, skipped, retrying, and manually handled effects with business action names
- [x] 12.4 Show failure reason, attempt count, last error, traceId, result summary, and retry action when authorized
- [ ] 12.5 Add UI tests for successful closure, partial failure, hard failure, Agent follow-up result, and unauthorized retry hidden state

## 13. Expense Report End-To-End Acceptance

- [x] 13.1 Add fixtures for existing expense report launch with DRAFT and REJECTED allowed statuses
- [ ] 13.2 Verify existing expense report launch updates status to IN_APPROVAL and starts the process
- [ ] 13.3 Verify create-and-launch creates an expense report, binds businessRef, updates status, and starts the process
- [ ] 13.4 Verify APPROVED outcome updates expense report status to APPROVED, emits approved event, notifies applicant, and triggers Agent follow-up
- [ ] 13.5 Verify REJECTED outcome updates expense report status to REJECTED and notifies applicant
- [ ] 13.6 Verify soft event or notification failure leaves approval result visible and allows authorized retry
- [ ] 13.7 Verify hard status update failure marks closure as failed and does not show business completion
- [ ] 13.8 Verify data consumers can read standardized outcome or closure events
- [ ] 13.9 Add Playwright smoke covering designer configuration, visual previews, launch, approval, closure status, and retry view

## 14. Documentation And Validation

- [x] 14.1 Document business object catalog table semantics and database registration examples
- [x] 14.2 Document executor registry contracts and required idempotency behavior
- [x] 14.3 Document business-object-first designer configuration flow with screenshots or diagrams
- [x] 14.4 Update process platform design notes with business closure foundation decisions
- [ ] 14.5 Run targeted backend tests, frontend component tests, Playwright smoke, full Maven verification if feasible, frontend typecheck/build, and strict OpenSpec validation
