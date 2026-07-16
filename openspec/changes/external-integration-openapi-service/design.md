## Context

External integrations currently risk being implemented as service-specific adapters with duplicated payload models, mapping code, credentials, retries, and routing logic. The new service must centralize reusable integration metadata while respecting TrioBase's boundaries: public traffic enters through Spring Cloud Gateway, state-changing or long-running chains use Temporal, workers live inside their Spring Boot host, activities are idempotent, and trace context is propagated end to end.

The service has two distinct planes:

- The management plane registers structures, mappings, connector endpoints, routes, orchestration definitions, tests, and releases.
- The runtime plane resolves immutable published snapshots, transforms payloads, invokes approved endpoints or starts Temporal workflows, and records sanitized execution evidence.

External consumers are represented as managed applications rather than raw credentials. `service-openapi` owns application identity, route subscriptions, scopes, quotas, security policies, approvals, and lifecycle metadata. `platform-gateway` remains the ingress enforcement point for authentication, coarse authorization, IP policy, request size, and rate limiting; `service-openapi` publishes signed/versioned policy snapshots to it and performs integration-specific checks such as route subscription, idempotency, orchestration concurrency, and payload-field policy.

## Goals / Non-Goals

**Goals:**

- Establish versioned canonical structures as the stable contracts used by TrioBase business capabilities.
- Register external structures and map them to/from canonical structures without arbitrary code execution.
- Publish immutable, auditable integration releases and safely roll routes back.
- Support governed synchronous endpoint invocation and durable multi-step orchestration.
- Keep tenant, environment, authentication, secret, trace, retry, and idempotency concerns explicit.
- Import and export OpenAPI 3.x schemas while retaining TrioBase-specific lifecycle metadata.

**Non-Goals:**

- Replacing `platform-gateway` as the public API gateway, authentication boundary, rate limiter, or sensitive-data filter.
- Becoming a general-purpose ESB, unrestricted scripting platform, or replacement for domain business services.
- Supporting arbitrary Java, JavaScript, SpEL, OGNL, or template expression execution in mapping and routing rules.
- Providing full BPMN semantics or a visual orchestration designer in the first backend release.
- Storing plaintext partner credentials, signing keys, or tokens.

## Decisions

### 1. Create `service-openapi` as a Spring Boot service

The capability belongs in `trio-base-services` because it is a governed enterprise integration service and must embed a Temporal worker. Splitting metadata management and runtime execution into separate deployables is deferred until independent scaling is justified.

Alternative considered: implement mappings in `service-lowcode`. Rejected because integration contracts have independent lifecycle, security, runtime, and compatibility requirements and should remain usable outside low-code forms.

### 2. Separate canonical structures from external structures

A canonical structure represents the stable TrioBase-facing contract. Each partner/system-specific structure is registered separately and connected through directional mapping sets. Business services depend on the canonical contract, not partner payloads.

Each structure has a stable identity and immutable numbered versions. Only drafts are editable. Published versions can be deprecated and archived but not mutated.

### 3. Use constrained declarative mapping

Mapping rules use JSON Pointer-style source/target paths, explicit operations, and a registry of allow-listed converters. Initial operations are copy, rename/path move, default, type conversion, constant, concatenate, date formatting, collection projection, and value-map lookup. Rules are compiled and validated at publication time.

No runtime `eval`, dynamic class loading, SpEL, or arbitrary scripts are allowed. Unsupported transformations require a versioned converter plugin implemented and reviewed in code.

### 3a. Allow additive tenant extensions of canonical structures

Published platform or domain canonical structures may be selected as a parent by a tenant-owned extension structure. The extension inherits the pinned parent version and may add tenant-namespaced optional fields and stricter presentation metadata, but cannot remove inherited fields, change their type or meaning, weaken validation, or redefine sensitivity. A breaking parent upgrade requires an explicit new tenant draft and compatibility validation; extensions never float automatically to the latest parent.

### 4. Model value mappings as reusable versioned assets

Value-map sets contain canonical/external pairs and define direction, case sensitivity, default behavior, and unmapped-value policy (`FAIL`, `PASS_THROUGH`, or `USE_DEFAULT`). Published mapping versions are immutable and referenced by release snapshot.

Where an existing platform dictionary is semantically appropriate, the integration asset may reference a published dictionary snapshot; partner-specific values remain local to `service-openapi` to avoid polluting global dictionaries.

### 5. Publish immutable integration release snapshots

A release snapshot pins structure versions, mapping versions, endpoint configuration version, route definition version, orchestration definition version, and secret reference names. Runtime resolution always ends at one immutable snapshot. Rollback changes the route's active release pointer atomically instead of modifying historical metadata.

Publication runs structural validation, compatibility checks, mapping coverage checks, route conflict detection, secret-reference existence checks, and stored contract tests.

### 6. Keep routing declarative and deterministic

Routes are addressed by a stable `routeKey` and scoped by tenant and environment. Candidate selection uses enabled state, priority, effective time, and constrained predicates over authenticated context, headers, and canonical payload fields. Ambiguous routes with equal priority are rejected during publication.

Target URLs are selected only from registered connector endpoints. Runtime-supplied arbitrary URLs are forbidden to reduce SSRF risk.

### 7. Divide synchronous invocation from Temporal orchestration

Synchronous direct invocation is allowed only for a single endpoint operation declared read-only, with a configured timeout below 500 ms and no cross-service state change. It uses the runtime mapping pipeline but does not permit retry chains that extend request latency.

All state-changing calls, calls expected to exceed one second, retryable delivery, callbacks, waits, compensation, or multiple steps use a Temporal workflow embedded in `service-openapi`. Activities perform all network I/O and implement business idempotency using an invocation/idempotency key.

### 8. Define a small orchestration DSL compiled to a generic workflow

The initial orchestration model supports invoke, transform, conditional branch, parallel group, wait/timer, and compensation references. Definitions are declarative JSON and are validated before publication. Workflow code remains deterministic; current time, random selection, networking, and persistence are confined to Temporal APIs or activities.

Alternative considered: dynamically generate workflow classes. Rejected because it complicates deployment, replay compatibility, security review, and versioning.

### 9. Treat secrets as external references

Connector authentication supports no-auth, API key, OAuth2 client credentials, basic authentication, and request signing through a credential-provider abstraction. HashiCorp Vault is the default production credential backend; local development may use a non-production provider selected by configuration. Metadata stores only secret reference identifiers. Logs redact configured sensitive fields and never persist authorization headers or resolved credentials.

### 10. Persist metadata in PostgreSQL and cache compiled releases

PostgreSQL is the source of truth. Published release snapshots and compiled mapping plans can be cached in Redis by tenant, environment, route key, and release version. Publication and rollback invalidate caches. Runtime must fall back to PostgreSQL if Redis is unavailable.

### 11. Expose separate management and runtime APIs

Management APIs provide CRUD, validation, test, publish, deprecate, archive, and rollback operations and require administrative permissions. Runtime APIs invoke by route key; callers cannot choose connector URLs, secret references, or unpublished versions.

OpenAPI import accepts component schemas and operation request/response schemas. Export emits a valid OpenAPI 3.x document plus `x-triobase-*` extensions for lifecycle and route metadata.

### 12. Include callbacks as first-class runtime operations

The initial release provides a dedicated callback endpoint addressed by an opaque callback key rather than exposing management identifiers. A callback profile pins partner authentication/signature verification, callback request structure, inbound mapping, correlation fields, duplicate policy, and the workflow signal to emit. Callback receipt is durably recorded before signaling Temporal, repeated partner event identifiers are idempotent, and unknown or terminal correlations are quarantined for operator inspection instead of silently discarded.

Alternative considered: reuse the normal route invocation endpoint. Rejected because callbacks require distinct authentication, replay protection, correlation, acknowledgement latency, and workflow-resumption behavior.

### 13. Model external callers as applications with environment clients

An application is the stable business identity of a consuming system and contains owner, tenant, contacts, purpose, status, risk level, and approval metadata. Each application has separate DEV/TEST/PROD clients so credentials, callbacks, network policies, product subscriptions, and quotas cannot leak across environments. Production activation and high-risk product or route access require approval. Suspension or revocation blocks new calls immediately without deleting audit history.

Each client can have multiple credential bindings to support rotation overlap. Secret material is generated or imported through the configured credential provider; management APIs reveal generated secrets only once where technically required.

### 13a. Group external capabilities into versioned API products

An API product is the unit exposed to consuming applications. It has a stable product key, owner, audience, risk classification, lifecycle, documentation, terms, default scopes, default traffic/security policy, and immutable published versions. A product version pins one or more published route releases and their request/response contracts.

Applications normally request a product subscription rather than individual routes. An approved subscription grants only the routes and operations present in its pinned product version. Route-level exclusions, lower quotas, additional scopes, source-network restrictions, or extra approval may narrow product access for sensitive operations; subscription overrides cannot broaden the product's published permissions.

Product versions do not automatically float. Adding a compatible route or optional contract capability produces a new product version that an application may explicitly adopt; removing routes, weakening guarantees, or introducing breaking contract changes requires a new major product version and migration notice.

### 14. Publish access subscriptions and security policy snapshots

Applications do not gain access merely by possessing a credential. A versioned subscription explicitly grants an environment client access to a pinned API product version and therefore its contained route keys, operations, canonical structure versions, scopes, allowed source networks, effective period, and optional field restrictions. Deny takes precedence over allow, and no matching active subscription means access is denied.

Authorization and traffic policy changes are published as immutable snapshots with a monotonically increasing policy version. Gateway caches fail closed for new or revoked access; a short bounded last-known-good policy window may be used only for already authorized non-sensitive reads during control-plane outages.

### 15. Split flow control across gateway and integration runtime

Gateway enforcement covers requests per second/minute/day, burst capacity, source IP/network, request body size, authentication, and route admission. Runtime enforcement covers per-application/per-route concurrency, active workflow count, callback rate, partner-endpoint circuit state, and tenant consumption budgets. Policy dimensions are tenant, environment, application client, route key, and optional operation.

Rate-limit responses use standard HTTP 429 semantics and include safe retry guidance. Limits must not be implemented as sleeps inside request threads or Temporal workflows.

### 16. Apply layered integration security controls

The platform supports OAuth2 client credentials, API key, Basic authentication for legacy systems, HMAC/RSA request signatures, and mTLS profiles. Production defaults require TLS, credential expiry/rotation policy, replay protection for signed requests, configurable IP/network restrictions, request timestamp and nonce validation, and route-specific sensitive-field allow/deny rules.

Security-relevant actions and failures produce audit events without credentials or sensitive payload content. Repeated authentication failures, signature failures, replay attempts, quota abuse, or denied-route probes can automatically suspend a client according to policy, with controlled operator reactivation.

### 17. Support three API product visibility levels

API products are `PRIVATE`, `TENANT`, or `PLATFORM_PUBLIC`. Private products are discoverable only by explicitly permitted applications or organizations. Tenant products are discoverable within the owning tenant. Platform-public products are discoverable across tenants and require platform administrator approval before publication. The default visibility is `TENANT`.

### 18. Combine structural compatibility with semantic review

The system automatically evaluates JSON Schema compatibility, including required fields, types, constraints, enums, and sensitivity changes. Publishers must provide semantic change declarations for units, timezone, meaning, enumeration semantics, and other behavior not safely inferable from schemas. Breaking or security-sensitive changes require review and a new major compatibility line.

### 19. Require production dual approval

Production publication of structures, mappings, routes, orchestration definitions, callback profiles, API products, subscriptions, and security/traffic policies requires approval by the asset owner and a platform administrator. The editor cannot satisfy both roles. Emergency rollback may be performed by one authorized platform operator but requires a reason, immutable audit event, and automatic owner notification.

### 20. Standardize API product semantic versions

API products use `MAJOR.MINOR.PATCH`. Documentation-only and behavior-preserving corrections increment patch; compatible route or optional contract additions increment minor; route removal, breaking contract change, or weakened guarantee increments major. Subscriptions remain pinned to an exact version until an explicit approved upgrade.

### 21. Persist callbacks before acknowledgement

Callback profiles may return a configured HTTP status, fixed text, or fixed JSON acknowledgement and may echo an approved partner event identifier. The callback inbox record and deduplication key must commit before success is returned. Mapping and Temporal signaling continue asynchronously so temporary worker unavailability does not lose accepted callbacks.

### 22. Apply explicit quota and retention defaults

Synchronous quota exhaustion returns HTTP 429. Asynchronous orchestration may use a bounded queue; a full queue rejects admission. Temporary quota increases require approval, an expiry time, and audit evidence. Applications cannot directly raise production quotas.

Execution metadata and sanitized error summaries are retained for 180 days by default. Request and response bodies are not persisted by default. An explicitly authorized diagnostic mode may retain redacted bodies for no more than seven days. Credentials, signatures, authorization headers, and classified sensitive values are never retained in execution evidence.

### 23. Deliver internal operations UI first

The first release includes internal management interfaces for structures, mappings, routes, orchestration, callback profiles, API products, applications, subscriptions, approvals, flow/security policies, executions, and callback quarantine. An external self-service developer portal is a later capability and is not part of this change.

## Risks / Trade-offs

- [Canonical-model governance becomes a bottleneck] -> Allow domain ownership and namespaces, require review only for shared/public contracts, and support additive version evolution.
- [Mapping DSL grows into an unsafe programming language] -> Keep operations allow-listed, prohibit arbitrary expressions, and require reviewed converter plugins for exceptional cases.
- [Generic orchestration duplicates domain workflows] -> Limit orchestration to integration transport/adaptation; business decisions and aggregate state remain in domain services.
- [Runtime metadata lookup increases latency] -> Compile immutable release plans, cache them, and retain database fallback.
- [Gateway and OpenAPI service policies drift] -> Publish immutable versioned policy snapshots, expose applied versions, monitor lag, and reject access when revocation state cannot be trusted.
- [One compromised application affects shared integrations] -> Isolate credentials and quotas per environment client, enforce least-privilege subscriptions, and support immediate suspension and revocation.
- [Partner endpoints expose SSRF or data-exfiltration paths] -> Register and approve endpoints, validate schemes/hosts, deny private or metadata networks by policy, and route public calls through controlled egress.
- [Schema evolution breaks consumers] -> Classify compatibility, block breaking publication on an existing major version, and require a new major/versioned route where needed.
- [Retries duplicate partner-side effects] -> Require idempotency keys, persist attempt state, and make activities idempotent; surface unsupported partner idempotency as a publication warning/risk.

## Migration Plan

1. Add the Maven module, database schema, Nacos configuration, health endpoints, permissions, and internal gateway route while leaving public runtime disabled.
2. Deliver structure registry, mapping/value-map management, validation, preview, and OpenAPI import/export.
3. Deliver connector endpoint registry, release publication, and a read-only synchronous runtime pilot with one internal integration.
4. Add Temporal worker, orchestration DSL, idempotency records, retry/compensation, and execution observability.
5. Enable selected public routes through `platform-gateway` after security, load, failure, and rollback tests.
6. Migrate existing one-off integrations incrementally; no big-bang migration is required.

Rollback disables affected gateway routes and atomically repoints route keys to the previous published release. Database migrations are forward-compatible; published history remains retained for audit.

## Open Questions

None. The requirements and design decisions for this change are confirmed.
