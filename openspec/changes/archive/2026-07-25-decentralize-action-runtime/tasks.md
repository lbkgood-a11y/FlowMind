## 1. Shared Runtime Foundation

- [x] 1.1 Add owner-hosted action runtime contracts and helpers to common modules without moving business logic into `common-action`
- [x] 1.2 Move reusable payload validation, status transition, redaction, idempotency key, and result normalization utilities out of `service-action`
- [x] 1.3 Add Spring runtime support for owner-hosted validation, dispatch, audit event emission, security context propagation, and exception mapping
- [x] 1.4 Add unit tests for shared validation, status transitions, duplicate idempotency handling, redaction, and owner runtime result mapping

## 2. Owner Action Definitions

- [x] 2.1 Move lowcode action definitions from `service-action` into `service-lowcode`
- [x] 2.2 Move workflow and closure action definitions from `service-action` into `service-workflow-engine`
- [x] 2.3 Move OpenAPI action definitions from `service-action` into `service-openapi`
- [x] 2.4 Add owner definition discovery tests for lowcode, workflow, and OpenAPI action types

## 3. Owner-Hosted Dispatch APIs

- [x] 3.1 Add lowcode owner-hosted action candidate validation and dispatch endpoints using `GlobalActionRequest` or `ActionCandidate`
- [x] 3.2 Add workflow owner-hosted action candidate validation and dispatch endpoints using local workflow services and Temporal-safe boundaries
- [x] 3.3 Add OpenAPI owner-hosted action candidate validation and dispatch endpoints using local orchestration services
- [x] 3.4 Keep existing `/internal/v1/actions/*` adapters only as temporary compatibility paths until callers are migrated
- [x] 3.5 Add controller and integration tests covering validation rejection, guard rejection, successful dispatch, duplicate idempotency, and bounded failure results

## 4. Service Caller Migration

- [x] 4.1 Replace `service-openapi` loopback calls through `OpenApiGlobalActionClient` with direct local business service execution
- [x] 4.2 Replace `service-workflow-engine` loopback calls through `WorkflowGlobalActionClient` with direct local workflow/closure service execution
- [x] 4.3 Remove `triobase.integrations.action` dependencies from owner service code paths after direct execution is verified
- [x] 4.4 Add regression tests proving OpenAPI orchestration/callback/cancel and workflow closure actions no longer require `service-action`

## 5. Frontend And AI Action Client Migration

- [x] 5.1 Refactor `apps/web-antd` Action Client to resolve owner-hosted validate, batch-validate, dispatch, status, and event endpoints
- [x] 5.2 Refactor `AiAssistantPanel` and related composables to preserve confirmation flow while dispatching to owner-hosted action routes
- [x] 5.3 Refactor `ai-agent-orchestrator` ActionClient to resolve owners and stop calling central `/actions/candidates/*`
- [x] 5.4 Add frontend and Python tests for lowcode leave-form Agent dispatch, workflow candidate rejection, unknown owner rejection, and normalized result handling

## 6. Audit And Timeline Migration

- [x] 6.1 Add owner-emitted action audit event persistence or outbox records for lowcode, workflow, and OpenAPI dispatch results
- [x] 6.2 Move new document timeline writes away from `service-action` and keep historical `act_*` reads available during migration
- [x] 6.3 Add audit/timeline tests proving owner-hosted action events are queryable with action id, target, trace id, and correlation id

## 7. Decommission service-action

- [x] 7.1 Remove frontend, AI, and backend call sites for `/api/v1/actions` and `service-action`
- [x] 7.2 Remove gateway `service-action` route, Docker service entry, integration properties, and `allowed-callers` references
- [x] 7.3 Remove `service-action` from Maven modules and delete the module after all replacement tests pass
- [x] 7.4 Run repository-wide searches proving no runtime dependency on `service-action`, `/internal/v1/actions/execute`, or `ActionOwnerDispatchRequest` remains outside intentional compatibility docs

## 8. Verification

- [x] 8.1 Run targeted Maven tests for `common-action`, lowcode, workflow-engine, OpenAPI, auth audit, and gateway config
- [x] 8.2 Run frontend typecheck, lint, unit tests, and targeted AI assistant dispatch tests
- [x] 8.3 Run Python tests or compile checks for `ai-agent-orchestrator`
- [x] 8.4 Validate the OpenSpec change and update all completed task checkboxes
