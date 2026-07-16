## Why

TrioBase currently lacks a governed boundary for external-system integration: external payload structures, mapping rules, routing decisions, and multi-step calls tend to be implemented independently inside business services. A dedicated OpenAPI integration service is needed to turn reusable external contracts into versioned platform assets and shorten delivery without bypassing gateway security, audit, or Temporal orchestration rules.

## What Changes

- Add a Spring Boot microservice, provisionally named `service-openapi`, responsible for external integration metadata and runtime execution.
- Add lifecycle management for canonical structures and external structures, including draft, published, deprecated, and archived versions.
- Allow tenants to derive governed extension structures from published platform/domain canonical structures while preserving inherited field semantics.
- Add field mapping between canonical and external structures, covering nested paths, direction, required/default behavior, type conversion, and validation.
- Add dictionary-style field value mapping with forward/reverse lookup, defaults, and unmapped-value policies.
- Add route definitions that select a target integration by stable route key, tenant/environment context, conditions, priority, and active version.
- Add synchronous single-endpoint invocation for short, read-only calls and Temporal-based orchestration for state-changing, long-running, retryable, or multi-step integrations.
- Include authenticated external callback ingestion in the initial release, with correlation, deduplication, payload mapping, and workflow resumption.
- Add OpenAPI 3.x import/export so external contracts can seed registered structures and published TrioBase contracts can be deposited as standard OpenAPI documents.
- Add release snapshots, compatibility checks, audit records, execution logs, and rollback to a previously published route/configuration version.
- Add consuming-application lifecycle management with application ownership, environment-specific clients, credentials, route subscriptions, scopes, IP/network policy, status, expiry, and revocation.
- Add API product lifecycle management so related published routes, contracts, documentation, default scopes, approval policies, and quotas can be versioned and subscribed to as one governed external offering.
- Support private, tenant-visible, and platform-public API products; require governed production publication and subscription approval.
- Add integration traffic policies covering per-application/per-route quotas, burst limits, concurrency, payload size, timeout, circuit breaking, and temporary suspension, published for enforcement by `platform-gateway` and the integration runtime.
- Add security controls for application authentication, route-level authorization, sensitive-field policy, replay protection, signed requests, source-network restrictions, credential rotation, and security audit events.
- Integrate public runtime traffic with `platform-gateway`; credentials remain references to managed secrets and are never stored in mapping definitions or execution logs.
- Use Vault as the default production credential backend, retain a provider abstraction for other secret managers, and deliver an internal operations UI while deferring the external self-service developer portal.

## Capabilities

### New Capabilities

- `external-structure-registry`: Canonical/external structure registration, version lifecycle, OpenAPI import/export, compatibility validation, and release snapshots.
- `integration-data-mapping`: Field path mapping, type conversion, validation, value mapping, transformation preview, and mapping test cases.
- `integration-routing-orchestration`: Governed route publication, target selection, endpoint invocation, Temporal orchestration, runtime observability, and rollback.
- `integration-application-access`: Consuming-application registration, credentials, subscriptions, scopes, traffic policies, security controls, revocation, and access audit.

### Modified Capabilities

None.

## Impact

- New module under `trio-base-services/service-openapi`, registered through Nacos and exposed only through `platform-gateway` for public runtime traffic.
- New PostgreSQL schema and Flyway migrations for structures, versions, fields, mappings, routes, orchestration definitions, release snapshots, and execution records.
- New common DTO/event contracts where route invocation or orchestration crosses service boundaries; contracts use JSON-compatible data only.
- New Temporal workflows and activities embedded in `service-openapi`; no standalone worker service is introduced.
- Gateway route configuration, authentication/authorization policies, trace propagation, rate limiting, and sensitive-data filtering require extension.
- Operations UI will eventually need management pages for structures, mappings, routes, publication, tests, and execution logs; backend contracts are included in this change, while advanced visual orchestration can be phased.
