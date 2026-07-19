## 1. Baseline And Dependencies

- [x] 1.1 Reconcile and verify the current `platform-gateway` workflow route, lowcode port, and `spring.temporal` configuration without overwriting unrelated working-tree changes
- [x] 1.2 Add workflow-engine dependencies for OpenFeign, restricted Apache Commons JEXL, JSON Schema validation, and their managed versions in the BOM
- [x] 1.3 Add test dependencies for Temporal `TestWorkflowEnvironment`, PostgreSQL Testcontainers, Spring Boot integration tests, and frontend component tests
- [x] 1.4 Add configurable auth/org/lowcode internal service URLs, internal service token, participant timeouts, and `workflow.participants.max-candidates=200`
- [x] 1.5 Add a configuration binding test that proves the Temporal Worker task queue equals `spring.application.name`

## 2. Process Definition Versioning

- [x] 2.1 Add a Flyway migration that replaces the single-column process key unique index with `(process_key, version)` and enforces at most one DRAFT per process key
- [x] 2.2 Add nullable form definition reference and form snapshot metadata needed to publish a reusable lowcode form into a process package
- [x] 2.3 Extend process package DTOs and entities with version-source, form reference, and snapshot metadata while preserving existing response compatibility
- [x] 2.4 Implement draft-only process package update service and `PUT /api/v1/process-packages/{id}` endpoint
- [x] 2.5 Implement new-version derivation service and `POST /api/v1/process-packages/{id}/versions` endpoint
- [x] 2.6 Enforce immutable PUBLISHED/OFFLINE content and prevent a second DRAFT for the same process key
- [x] 2.7 Implement transactional publish validation and immutable form/flow snapshot persistence
- [x] 2.8 Add repository and controller tests for create, update, publish, offline, new-version, conflicts, and existing API compatibility

## 3. Internal Participant And Form Contracts

- [x] 3.1 Add shared internal DTOs for resolved users, role resolution, department resolution, user validation, and published form snapshot responses
- [x] 3.2 Add a reusable internal service-token filter/configuration that protects `/internal/v1/**` endpoints and is not based on end-user permissions
- [x] 3.3 Add service-auth internal endpoints to resolve enabled users by role code and validate enabled users by ID
- [x] 3.4 Add service-org internal endpoint to resolve enabled users for an organization unit and dimension
- [x] 3.5 Add service-lowcode internal endpoint that returns a published form definition with Schema and UI Schema by ID
- [x] 3.6 Add integration tests proving internal endpoints reject missing/invalid service tokens and return only enabled users or published forms
- [x] 3.7 Add workflow-engine Feign clients with sub-500ms timeouts, bounded retries, TraceId propagation, and explicit error mapping

## 4. Participant Snapshot And Candidate Tasks

- [x] 4.1 Add `wf_task_candidate` and participant-resolution idempotency storage with unique constraints
- [x] 4.2 Add candidate entities, mappers, and query methods for pending-task visibility and atomic task claim
- [x] 4.3 Replace the placeholder `resolveAssignee` Activity with ROLE, USER, and DEPT resolution through internal clients
- [x] 4.4 Persist immutable node assignee snapshots and enforce the configured candidate limit
- [x] 4.5 Create one candidate-backed task for APPROVAL nodes and one assigned task per participant for COUNTERSIGN nodes
- [x] 4.6 Change pending-task queries so candidates see unclaimed tasks and assigned users see claimed tasks
- [x] 4.7 Add atomic candidate authorization and claim checks to approve, reject, transfer, and add-sign commands
- [x] 4.8 Suspend the process and mark the node FAILED when participant resolution returns no eligible users
- [x] 4.9 Add concurrency and integration tests for candidate visibility, non-candidate rejection, double claim, snapshots, empty resolution, and Activity retry idempotency

## 5. Safe Conditions And Publish Validation

- [x] 5.1 Implement a restricted JEXL condition evaluator that exposes only form values and approved read-only helpers
- [x] 5.2 Reject class access, constructors, reflection, method invocation, file/network access, oversized expressions, and unsupported syntax
- [x] 5.3 Expose condition evaluation through an idempotent Temporal Activity and return explicit matched/default/error results
- [x] 5.4 Replace the current literal-`true` routing logic with recorded Activity results and fail instead of silently selecting an arbitrary branch
- [x] 5.5 Implement flow graph validation for START/END cardinality, unique IDs, valid edges, reachability, supported node types, participants, countersign strategy, and one default condition
- [x] 5.6 Implement form Schema/UI Schema validation and reject unregistered field widgets during publication
- [x] 5.7 Add unit tests for numeric, boolean, null, default, unsafe, invalid, and fee-report branching expressions

## 6. Task State Machine And Workflow Coordination

- [x] 6.1 Add `wf_task_operation` migration, entity, mapper, operation IDs, source/target task linkage, TraceId, and immutable audit fields
- [x] 6.2 Extend Workflow signal contracts to carry operation IDs and typed approval, rejection, transfer, and add-sign command payloads
- [x] 6.3 Make approval and rejection database updates idempotent and emit exactly one Workflow transition per operation ID
- [x] 6.4 Implement rejection termination and validated return-to-visited-node behavior with a fresh participant snapshot
- [x] 6.5 Implement transfer by marking the source task TRANSFERRED and creating a linked pending task for the enabled target user
- [x] 6.6 Implement parallel mandatory add-sign by registering new required task IDs in Workflow state and waiting for all required approvals
- [x] 6.7 Correct ALL/ANY countersign vote accounting, task completion, cancellation, and rejection behavior for concurrent signals
- [x] 6.8 Propagate gateway TraceId through Workflow headers, Activities, internal Feign calls, and task operation records
- [x] 6.9 Add Temporal Workflow tests for approve, condition branch, reject, return, transfer, add-sign, countersign, duplicate signals, Replay, and Worker restart recovery

## 7. Server-Side Form Runtime

- [x] 7.1 Add a JSON Schema validation service using the published process package snapshot
- [x] 7.2 Validate form data before inserting a process instance or starting a Temporal Workflow
- [x] 7.3 Return structured field validation errors for missing, mistyped, out-of-range, and unknown data
- [x] 7.4 Add process start version checking so a stale form snapshot returns a version conflict instead of silently using different Schema data
- [x] 7.5 Support inline process forms and optional published lowcode form references during package publication
- [x] 7.6 Add integration tests proving invalid form data creates neither a database instance nor a Temporal Workflow execution

## 8. Process Frontend Closure

- [x] 8.1 Restore the `@vben/web-antd` dependency installation and fix all workflow-related X6 and TypeScript typecheck errors
- [x] 8.2 Extend the process API client and types for draft update, new-version, package version, form snapshots, reject targets, transfer, add-sign, and operation IDs
- [x] 8.3 Build a fixed dynamic form component registry for string, textarea, number, money, integer, boolean, enum/select, and date fields
- [x] 8.4 Render process start forms from JSON Schema/UI Schema and remove the raw form JSON textarea from the normal user flow
- [x] 8.5 Add client-side Schema validation, field-level errors, and stale-version conflict handling
- [x] 8.6 Add ROLE, USER, and DEPT participant configuration controls to the X6 node property panel
- [x] 8.7 Add designer validation for graph connectivity, supported node types, participants, conditions, Schema, and registered widgets before publish
- [x] 8.8 Add process package draft edit, create-new-version, publish, offline, and immutable-version UX
- [x] 8.9 Add task center actions for reject target selection, transfer, parallel add-sign, and operation result feedback
- [x] 8.10 Add an approval history timeline using node records and task operation records
- [x] 8.11 Add frontend component tests for dynamic field mapping, validation, participant configuration, and task action dialogs

## 9. Permissions, Documentation, And Integration

- [x] 9.1 Add auth permissions and menu/button bindings for process package update, new version, transfer, add-sign, reject target, and history APIs
- [x] 9.2 Ensure internal participant/form endpoints are not exposed by platform-gateway routes
- [x] 9.3 Update Docker/local configuration for workflow-engine dependencies and document required service-token and Temporal settings
- [x] 9.4 Update `openspec/specs/process-platform/design.md` to record workflow-engine package ownership, lowcode form snapshots, candidate tasks, and the MVP node scope
- [x] 9.5 Add API contract documentation for process package versioning, internal participant resolution, task operations, and dynamic form errors

## 10. End-To-End Verification

- [x] 10.1 Add fee-report fixtures with DEPT_HEAD and FINANCE users, organization assignments, and published version 1 form/flow snapshots
- [x] 10.2 Verify a 3000 amount follows department approval directly to completion
- [x] 10.3 Verify an 8000 amount follows department approval to finance approval and then completion
- [x] 10.4 Verify non-candidates cannot see or handle tasks and candidate concurrency completes exactly once
- [x] 10.5 Verify rejection, return-to-node, transfer, parallel add-sign, and empty-participant suspension scenarios
- [x] 10.6 Verify a waiting process continues after workflow-engine/Worker restart with TraceId and audit history intact
- [x] 10.7 Add a Playwright smoke flow covering login, dynamic expense form start, pending task approval, branch result, and history display
- [x] 10.8 Run targeted Maven tests, full `mvn verify`, frontend typecheck/tests/build, and `openspec validate process-runtime-mvp-closure --strict`
