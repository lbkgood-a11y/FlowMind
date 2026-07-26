# openapi-operations-console Specification

## Purpose
Define the tenant-scoped OpenAPI operations console that lets operators discover, govern, expose, monitor, and validate API lifecycle assets from contract design through runtime readiness while preserving tenant isolation, permissions, policy enforcement, and disabled-by-default public runtime.

## Requirements
### Requirement: Search every lifecycle asset
The system SHALL expose tenant-scoped paged management queries for structures, mappings, value maps, connectors, routes, releases, orchestrations, callback profiles, API products, applications, subscriptions, approvals, traffic policies, and policy snapshots.

#### Scenario: Search applications
- **WHEN** an authorized tenant operator opens the application-management page with a keyword and lifecycle filter
- **THEN** the system returns only matching applications visible to that tenant with stable identifiers, lifecycle state, timestamps, and redacted detail

#### Scenario: Reject unknown asset type
- **WHEN** a caller requests a catalog asset type outside the fixed allow-list
- **THEN** the system rejects the request without resolving a table or dynamic class

### Requirement: Navigate the complete API lifecycle
The system SHALL provide backend-driven menus for lifecycle overview, structures, mappings, value maps, connectors, routes and releases, orchestrations, callbacks, API products, applications, subscriptions and approvals, policies, executions, and callback quarantine.

#### Scenario: Administrator reloads menus
- **WHEN** the administrator signs in after the menu migration
- **THEN** the OpenAPI menu displays the design, implementation, exposure, and operations groups with their authorized child pages

### Requirement: Operate lifecycle assets from the console
The system SHALL provide functional internal pages that invoke typed management APIs for create, inspect, draft update, publish, deprecate, archive, activate, rollback, suspend, reactivate, revoke, approve, upgrade, and policy publication where those actions apply.

#### Scenario: Publish a route release
- **WHEN** an authorized operator validates a route version and publishes and activates its release from the route page
- **THEN** the console shows the immutable release identifier and updated active-release state without permitting a runtime URL override

#### Scenario: Onboard an application
- **WHEN** an authorized operator creates an application, creates an environment client, binds or rotates a credential reference, and activates the client
- **THEN** the console displays the resulting lifecycle state while revealing generated secret material only in the one-time response

### Requirement: Guide lifecycle readiness
The system SHALL show prerequisite readiness from contracts through runtime and SHALL identify missing structures, mappings, implementation bindings, active releases, products, applications, subscriptions, and current policies.

#### Scenario: Product has no active subscription
- **WHEN** a published API product exists but no active application subscription is present
- **THEN** the overview marks exposure as blocked and links the operator to application and subscription management

#### Scenario: Runtime adapter not enabled
- **WHEN** structures, mappings, connectors, routes, products, subscriptions, and policies are complete but no governed runtime adapter or gateway route is enabled
- **THEN** the overview reports management readiness separately from runtime exposure and does not imply that public invocation is available

#### Scenario: Owner-action routes do not require an external connector
- **WHEN** an OpenAPI route is implemented by a published owner-action orchestration and active release without any external connector
- **THEN** the implementation readiness stage is considered complete because the implementation binding is the owner-hosted action runtime, not a connector

### Requirement: Preserve governance in operations UI
The system MUST enforce existing permissions, tenant isolation, approval rules, secret redaction, policy fail-closed behavior, and disabled-by-default public runtime regardless of UI actions.

#### Scenario: Operator lacks write permission
- **WHEN** a read-only operator opens a lifecycle page
- **THEN** the operator can search and inspect authorized assets but mutation controls are hidden or rejected by the backend

#### Scenario: Lifecycle becomes ready
- **WHEN** every managed asset and policy prerequisite is satisfied
- **THEN** the console reports readiness but does not automatically enable the public gateway route

### Requirement: Use management APIs for control-plane operations
The OpenAPI console SHALL use typed management APIs under `/api/v1/openapi/management/**` for lifecycle assets and SHALL NOT call runtime invocation, callback reception, or arbitrary connector URLs directly.

#### Scenario: Operator publishes a policy snapshot
- **WHEN** an authorized operator publishes or inspects policy state from the console
- **THEN** the frontend calls the management API, and enforcement points consume the resulting immutable snapshot through their configured contract

#### Scenario: Operator inspects runtime evidence
- **WHEN** an authorized operator opens execution or callback quarantine pages
- **THEN** the console reads sanitized evidence and remediation metadata without exposing request bodies, resolved credentials, or direct partner endpoints
