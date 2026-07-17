# integration-application-access Specification

## Purpose
Define governed application onboarding, credentials, API products, subscriptions, approvals, access policies, traffic controls, and security-event handling for external integrations.

## Requirements

### Requirement: Manage consuming applications
The system SHALL manage consuming applications with tenant, owner, contacts, purpose, risk level, lifecycle status, approval evidence, and separate environment clients.

#### Scenario: Create a test client
- **WHEN** an authorized application owner registers an approved application for the test environment
- **THEN** the system creates a distinct test client identity without granting route access automatically

#### Scenario: Suspend an application client
- **WHEN** an authorized operator suspends a client
- **THEN** new requests and callbacks for that client are denied immediately while metadata and audit history remain available

### Requirement: Manage application credentials safely
The system SHALL bind versioned credential references to environment clients, support overlapping credential rotation and revocation, and SHALL NOT persist or repeatedly expose plaintext secret material.

#### Scenario: Rotate an API credential
- **WHEN** an owner creates a replacement credential with an overlap period
- **THEN** both approved versions work during the overlap and the previous version is automatically rejected after its retirement time

#### Scenario: Retrieve a stored secret
- **WHEN** a caller requests plaintext for an existing credential
- **THEN** the system refuses to return it and offers rotation instead

### Requirement: Authorize explicit route subscriptions
The system SHALL require an active published API product subscription granting an environment client access to the product version's route keys, operations, scopes, structure versions, effective period, and optional source-network or field restrictions.

#### Scenario: Invoke subscribed route
- **WHEN** an authenticated active client invokes a route and operation covered by an effective subscription and required scopes
- **THEN** the request proceeds to traffic and runtime validation

#### Scenario: Invoke unsubscribed route
- **WHEN** an authenticated client invokes a route not covered by an active subscription
- **THEN** the system denies access without revealing whether other tenants or applications can use the route

### Requirement: Manage versioned API products
The system SHALL manage API products with stable identity, ownership, audience, risk classification, lifecycle, documentation, terms, default scopes, default policies, and immutable published versions that pin published route releases and contracts.

#### Scenario: Publish an API product version
- **WHEN** an authorized product owner publishes a valid product draft containing published compatible route releases
- **THEN** the system creates an immutable product version with pinned routes, contracts, scopes, documentation, and default policies

#### Scenario: Include unpublished route
- **WHEN** a product draft references an unpublished or policy-forbidden deprecated route release
- **THEN** the system blocks product publication and identifies the invalid route reference

### Requirement: Control API product visibility
The system SHALL support `PRIVATE`, `TENANT`, and `PLATFORM_PUBLIC` API product visibility, default new products to `TENANT`, and require platform approval for platform-public publication.

#### Scenario: Discover tenant product
- **WHEN** an authenticated application owner browses products in its tenant
- **THEN** the system returns eligible tenant products and explicitly granted private products but not inaccessible products from other tenants

#### Scenario: Publish platform-public product
- **WHEN** a product owner requests platform-public publication
- **THEN** the system requires platform administrator approval before exposing the product across tenants

### Requirement: Subscribe applications to API products
The system SHALL manage environment-specific application subscriptions to pinned API product versions with request, approval, activation, suspension, expiry, upgrade, and revocation lifecycle states.

#### Scenario: Approve production subscription
- **WHEN** the required application administrator and API product owner approve a production subscription
- **THEN** the system activates access to only the pinned product version subject to its scopes and effective policies

#### Scenario: Product publishes a new version
- **WHEN** an API product publishes a newer version
- **THEN** existing subscriptions remain pinned until an authorized upgrade is requested or approved

### Requirement: Restrict subscription overrides
The system SHALL allow route exclusions and stricter route-level scopes, quotas, networks, fields, and approval requirements, but MUST NOT allow a subscription override to broaden the published API product permissions.

#### Scenario: Apply stricter route quota
- **WHEN** an approver assigns a lower quota to a sensitive route within a subscribed product
- **THEN** the effective route quota is the stricter subscription value

#### Scenario: Attempt to grant absent route
- **WHEN** a subscription override references a route not contained in the pinned product version
- **THEN** the system rejects the override

### Requirement: Govern API product compatibility
The system SHALL classify API product version changes and require a new major version for removed routes, breaking contract changes, or weakened published guarantees.

#### Scenario: Add compatible route
- **WHEN** a product draft adds a new route without changing existing pinned route contracts
- **THEN** the system may publish it as a compatible new product version without changing existing subscriptions

#### Scenario: Remove subscribed route
- **WHEN** a product draft removes a route from the preceding published product version
- **THEN** the system classifies the change as breaking and requires a new major product version and migration notice

### Requirement: Version API products semantically
The system SHALL version API products as `MAJOR.MINOR.PATCH` and SHALL keep subscriptions pinned to an exact version until an approved upgrade.

#### Scenario: Add compatible optional capability
- **WHEN** a product version adds a compatible route or optional contract capability
- **THEN** the system requires at least a minor version increment and does not modify existing subscriptions

#### Scenario: Correct documentation only
- **WHEN** a product changes documentation without changing runtime behavior or guarantees
- **THEN** the system permits a patch version increment

### Requirement: Approve production assets and subscriptions
The system SHALL require distinct asset-owner and platform-administrator approvals for production publication and production subscriptions.

#### Scenario: Editor attempts both approvals
- **WHEN** the same actor who submitted a production change attempts to satisfy all required approvals
- **THEN** the system rejects self-approval for the remaining role

#### Scenario: Emergency rollback
- **WHEN** one authorized platform operator performs an emergency rollback
- **THEN** the system requires a reason, records an immutable audit event, and notifies the asset owner

### Requirement: Enforce traffic policies
The system SHALL support per-tenant, environment, application client, route, and operation limits for rate, burst, daily quota, request size, concurrency, active workflows, and callback volume.

#### Scenario: Exceed request rate
- **WHEN** a client exceeds its effective gateway request rate or burst limit
- **THEN** the gateway returns HTTP 429 with safe retry guidance and records a rate-limit metric

#### Scenario: Exceed workflow concurrency
- **WHEN** a client has reached its active orchestration limit
- **THEN** the runtime rejects or queues the request according to the published policy without starting an untracked workflow

### Requirement: Enforce application security policy
The system SHALL support TLS enforcement, authentication profile, credential expiry, signature timestamp and nonce validation, IP/network restrictions, replay protection, and sensitive-field policies per application subscription.

#### Scenario: Replay a signed request
- **WHEN** a valid signed request repeats a nonce within the configured replay window
- **THEN** the system rejects the request and emits a sanitized security event

#### Scenario: Call from disallowed network
- **WHEN** an otherwise valid client request originates outside its effective source-network policy
- **THEN** the gateway denies admission before the integration runtime is invoked

### Requirement: Publish policies to enforcement points
The system SHALL publish immutable versioned access and traffic policy snapshots to `platform-gateway` and the integration runtime and SHALL expose their applied policy versions.

#### Scenario: Revoke access
- **WHEN** a credential or subscription is revoked
- **THEN** the new policy version is propagated and new access fails closed at every enforcement point

#### Scenario: Detect policy lag
- **WHEN** an enforcement point reports an applied version older than the required version
- **THEN** the system surfaces the lag operationally and does not allow revoked or newly uncertain privileged access

### Requirement: Audit and respond to security events
The system SHALL audit application lifecycle, credential rotation, subscription approval, policy publication, denied access, authentication failure, replay, quota abuse, suspension, and reactivation without recording secrets.

#### Scenario: Trigger automatic suspension
- **WHEN** a client crosses a configured threshold of security violations
- **THEN** the system suspends it according to policy, records the reason, and notifies authorized owners and operators
