## 1. Service Foundation

- [x] 1.1 Add `service-openapi` to the root and services Maven reactor with aligned BOM, Spring Boot, validation, MyBatis-Plus, Flyway, PostgreSQL, Redis, Nacos, Actuator, and Temporal dependencies
- [x] 1.2 Create the Spring Boot application, environment configuration, health/readiness endpoints, structured logging, and local test profile
- [x] 1.3 Define service permissions and add gateway/Nacos configuration for management and disabled-by-default runtime routes
- [x] 1.4 Add ArchUnit and baseline unit/integration test setup, including TrioBase common architecture rules

## 2. Persistence and Domain Model

- [x] 2.1 Create Flyway migrations for structure identity, structure version, field metadata, and provenance tables with tenant-aware constraints
- [x] 2.2 Create migrations for field mapping sets/rules, value-map sets/entries, versions, and stored contract tests
- [x] 2.3 Create migrations for connector endpoints, route definitions, orchestration definitions, release snapshots, and active release pointers
- [x] 2.4 Create migrations for idempotency records, execution summaries, step attempts, audit metadata, and required indexes/retention fields
- [x] 2.5 Implement entities, enums, repositories/mappers, optimistic locking, tenant filters, and audit-field population for the new schema

## 3. Structure Registry

- [x] 3.1 Implement canonical/external structure creation, lookup, listing, ownership, namespace uniqueness, and tenant isolation
- [x] 3.2 Implement draft version creation/editing plus publish, deprecate, and archive state transitions with immutable published content
- [x] 3.3 Implement tenant extension structures pinned to a parent canonical version with additive-field and inherited-governance validation
- [x] 3.4 Implement JSON structure validation and normalized field metadata extraction for nested objects, arrays, constraints, and sensitivity classification
- [x] 3.5 Implement structural compatibility comparison, semantic-change declarations, and blocking/approval rules for breaking or security-sensitive changes
- [x] 3.6 Implement OpenAPI 3.x import for component, request, and response schemas with provenance and reference validation
- [x] 3.7 Implement OpenAPI 3.x export for published contracts with sanitized `x-triobase-*` lifecycle extensions
- [x] 3.8 Add management controllers, DTO validation, permission checks, audit events, and tests for the complete structure lifecycle

## 4. Field and Value Mapping

- [x] 4.1 Implement directional mapping-set lifecycle pinned to canonical and external structure versions
- [x] 4.2 Implement JSON path/pointer validation and required target-field coverage analysis
- [x] 4.3 Implement the allow-listed transformation operation registry and initial copy, constant, default, type, concatenate, date, collection, and value-map operations
- [x] 4.4 Add safeguards rejecting script/expression operations, dynamic classes, credentials, and authorization-header constants
- [x] 4.5 Implement versioned value-map sets with forward/reverse lookup, case policy, default, and fail/pass-through/default policies
- [x] 4.6 Implement mapping compilation into an immutable execution plan and cache serialization
- [x] 4.7 Implement transformation preview with source/target validation, applied-rule trace, warnings, and sensitive-value redaction
- [x] 4.8 Implement stored contract test cases and publication-time test execution
- [x] 4.9 Add mapping/value-map management APIs and unit/property/integration tests for nested fields, arrays, conversion failures, coverage, and redaction

## 5. Connector and Secret Boundary

- [ ] 5.1 Implement connector endpoint lifecycle with approved URL, method, timeout, operation classification, and network-policy validation
- [ ] 5.2 Define credential-provider and outbound-client abstractions supporting no-auth, API key, basic auth, OAuth2 client credentials, and signing profiles by secret reference
- [ ] 5.3 Implement the production Vault credential-provider adapter plus a configurable local-development provider without exposing resolved secrets through APIs or logs
- [ ] 5.4 Implement SSRF protections, allowed schemes/hosts, redirect policy, DNS/IP validation, controlled egress hooks, and related security tests
- [ ] 5.5 Implement outbound trace propagation, sanitized metrics/logging, response size limits, and request/response sensitive-field redaction

## 6. Routes and Releases

- [ ] 6.1 Implement tenant/environment route definitions with stable route key, priority, effective time, constrained predicates, and enabled state
- [ ] 6.2 Implement deterministic route compilation/resolution and publication-time ambiguity detection
- [ ] 6.3 Implement release validation that pins structures, mappings, value maps, connector, route, orchestration, secrets, and required passing tests
- [ ] 6.4 Implement immutable release snapshots, atomic activation, deprecation, rollback, cache invalidation, and audited release history
- [ ] 6.5 Implement Redis compiled-release caching with PostgreSQL fallback and tenant/environment/route/release cache keys
- [ ] 6.6 Add route/release management APIs and concurrency, ambiguity, rollback, and cache-failure integration tests

## 7. Application Access, Flow Control, and Security

- [ ] 7.1 Create migrations and domain models for API products/versions/route members, applications, environment clients, owners, contacts, approvals, credential bindings, product subscriptions, scopes, and policy versions
- [ ] 7.2 Implement API product create, edit, validate, publish, deprecate, archive, documentation, ownership, audience, risk, terms, private/tenant/platform-public visibility, default scopes, and default policy lifecycles
- [ ] 7.3 Implement immutable semantic API product versions that pin published route releases/contracts and classify patch, compatible minor, and breaking major changes
- [ ] 7.4 Implement application/client create, approve, activate, suspend, reactivate, expire, and revoke lifecycles with tenant isolation and audit events
- [ ] 7.5 Implement credential binding, one-time secret delivery where required, overlapping rotation, expiry, retirement, and immediate revocation through `CredentialProvider`
- [ ] 7.6 Implement API product subscription request, distinct asset-owner/platform-administrator production approval, activation, suspension, expiry, revocation, and explicit version-upgrade workflows per environment
- [ ] 7.7 Implement route exclusions and stricter subscription overrides for operation, scope, quota, effective period, source network, structure version, and field restrictions without permission broadening
- [ ] 7.8 Implement hierarchical traffic policy resolution for tenant, client, product, route, and operation rate, burst, daily quota, body size, concurrency, workflow, and callback limits
- [ ] 7.9 Implement immutable signed policy snapshots, gateway/runtime distribution, applied-version reporting, cache invalidation, revocation fail-closed behavior, and drift monitoring
- [ ] 7.10 Extend `platform-gateway` integration for client authentication, IP/network rules, product/route admission, request-size checks, rate limits, and standard 429 responses
- [ ] 7.11 Implement runtime product subscription/scope checks, workflow concurrency and consumption budgets, security violation counters, and policy-driven automatic suspension
- [ ] 7.12 Add management APIs and tests for product compatibility, pinned upgrades, dual approval, least privilege, environment isolation, overlapping rotation, revocation, policy precedence, rate limits, concurrency, drift, and suspension/reactivation

## 8. Synchronous Runtime

- [ ] 8.1 Implement gateway-facing invocation by route key with application identity, authentication context, tenant resolution, request schema validation, and published release resolution
- [ ] 8.2 Enforce synchronous eligibility for one read-only endpoint with timeout below 500 ms and reject state-changing, multi-step, or long-running routes
- [ ] 8.3 Implement request transformation, single outbound invocation, response transformation, response validation, and normalized error mapping
- [ ] 8.4 Add rate-limit integration, execution summary persistence, TraceId propagation, audit linkage, and sanitized runtime logs
- [ ] 8.5 Add end-to-end tests for successful read invocation, validation failure, timeout, partner error, route miss, tenant isolation, application denial, quota exhaustion, and runtime URL override rejection

## 9. Temporal Orchestration Runtime

- [ ] 9.1 Define versioned orchestration JSON schema for invoke, transform, branch, parallel, wait/timer, and compensation references
- [ ] 9.2 Implement graph validation for references, reachability, supported cycles, terminal paths, compensation targets, and deterministic configuration
- [ ] 9.3 Implement the generic deterministic Temporal workflow interpreter without network, database, clock, random, thread, or future I/O violations
- [ ] 9.4 Implement idempotent Activities for release loading, transformation, connector invocation, execution persistence, callback/wait support, and compensation
- [ ] 9.5 Embed and configure the Temporal worker with task queue tied to `spring.application.name`, approved retry presets, timeouts, and graceful lifecycle
- [ ] 9.6 Implement JSON-only workflow/activity contracts and Temporal header propagation for TraceId, tenant, application client, caller, route release, and idempotency key
- [ ] 9.7 Implement start/status/result/cancel APIs with stable workflow IDs and duplicate idempotency-key attachment behavior
- [ ] 9.8 Implement retry classification, attempt evidence, deterministic reverse compensation, and partial-failure status handling
- [ ] 9.9 Add Temporal replay/determinism tests and end-to-end tests for branch, parallel, retry, timer, duplicate request, compensation, cancellation, concurrency limit, and worker restart

## 10. External Callback Runtime

- [ ] 10.1 Implement versioned callback profiles with opaque callback keys, pinned application/security policy, request schema, mapping, correlation, replay window, acknowledgement, and workflow signal configuration
- [ ] 10.2 Implement the gateway-facing callback endpoint with application identification, request-size/rate limits, raw-body signature verification, timestamp/nonce replay protection, schema validation, durable-before-acknowledgement behavior, configurable acknowledgements, and sensitive-data redaction
- [ ] 10.3 Implement durable callback inbox persistence and idempotent deduplication by tenant, application client, callback profile, and partner event identifier before workflow signaling
- [ ] 10.4 Implement correlation to active workflow executions and Temporal signal delivery with mapped JSON payloads and trace context
- [ ] 10.5 Implement quarantine and operator resolution for valid unmatched, ambiguous, late, or terminal callbacks
- [ ] 10.6 Add end-to-end callback tests for success, duplicate delivery, invalid signature, expired timestamp, unknown correlation, late callback, rate limit, suspended application, worker outage, and signal retry

## 11. Operations and Governance

- [ ] 11.1 Implement searchable execution and step-attempt APIs with permissions, tenant isolation, application filters, pagination, and retention fields
- [ ] 11.2 Add metrics and alerts for application denials, policy lag, quotas, route latency/error rate, mapping failures, partner failures, callback quarantine, workflow backlog, retries, compensation, cache behavior, and release rollback
- [ ] 11.3 Add audit coverage tests for application/credential/subscription lifecycle, policy publication, route lifecycle, rollback, denied access, runtime invocation, callback receipt, and operational actions
- [ ] 11.4 Implement default 180-day execution metadata retention, no-body default storage, authorized redacted diagnostic capture limited to seven days, and deletion/redaction jobs that never retain credentials or sensitive values
- [ ] 11.5 Implement the internal operations UI for structures, mappings, routes, orchestration, callbacks, products, applications, subscriptions, approvals, policies, executions, and callback quarantine
- [ ] 11.6 Document management/runtime OpenAPI contracts, application onboarding, lifecycle state machines, mapping DSL, orchestration DSL, callback profiles, flow/security policy, and operator runbooks

## 12. Delivery and Acceptance

- [ ] 12.1 Add Docker/local-compose configuration and reproducible migrations for PostgreSQL, Redis, Nacos, and Temporal dependencies
- [ ] 12.2 Add CI checks for compilation, unit/integration/ArchUnit tests, coverage, migration validation, OpenAPI validation, and security scanning
- [ ] 12.3 Run gateway masking/auth/application-policy/rate-limit and end-to-end trace propagation verification from ingress through outbound call, callback, and Temporal Activity
- [ ] 12.4 Pilot one managed consuming application against one read-only integration and one state-changing callback-based multi-step integration using canonical contracts and stored contract tests
- [ ] 12.5 Execute load, quota, concurrency, policy drift, credential revocation, failure injection, secret leakage, SSRF, callback replay, workflow replay compatibility, rollback, and disaster-recovery acceptance tests before enabling public routes
