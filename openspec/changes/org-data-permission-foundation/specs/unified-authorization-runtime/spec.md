## ADDED Requirements

### Requirement: Unified Permission Source
The system SHALL use `service-auth` as the single source of truth for users, roles, menus, API permissions, and data permission policies.

#### Scenario: Service uses shared permission model
- **WHEN** a Java microservice protects an endpoint
- **THEN** it uses the shared permission code model and does not define an independent role or menu permission model

#### Scenario: API permission code consistency
- **WHEN** a backend endpoint is protected by `@RequirePermission`
- **THEN** the same permission code is registered in permission seed data and used by frontend access checks

### Requirement: Service-Side Authorization Enforcement
The system SHALL enforce authorization at the gateway and again inside business services. Gateway authorization MUST NOT be the only security boundary.

#### Scenario: Gateway request
- **WHEN** a request enters through the gateway with a valid token
- **THEN** the gateway validates identity, injects authorization context, and forwards the request

#### Scenario: Internal service request
- **WHEN** a service endpoint is called by another service, Temporal Activity, AI Agent, or scheduler
- **THEN** the target service still applies its own permission checks unless the endpoint is explicitly public

### Requirement: Public Endpoint Declaration
The system SHALL require public endpoints to be explicitly declared. Controller methods without a permission requirement or public declaration MUST be treated as incomplete.

#### Scenario: Public endpoint
- **WHEN** an endpoint is intended to be callable without authentication
- **THEN** the endpoint is marked with a public declaration and documented as public

#### Scenario: Missing authorization metadata
- **WHEN** a controller method has neither permission metadata nor public metadata
- **THEN** CI or static validation reports the endpoint as an authorization coverage gap

### Requirement: Authorization Context
The system SHALL provide a standard authorization context containing user ID, username, tenant ID, roles, permission codes, and version fields for authorization and data policy evaluation.

#### Scenario: Read authorization context
- **WHEN** business code needs current caller information
- **THEN** it reads the standard authorization context instead of parsing raw headers directly

#### Scenario: Missing authorization context
- **WHEN** a protected service method is called without authorization context
- **THEN** the authorization runtime rejects the request

### Requirement: Permission Snapshot Compatibility
The system SHALL be designed to support permission snapshots and version-based invalidation. Initial implementation MAY remain compatible with the current permission header flow while exposing snapshot-ready DTOs and version fields.

#### Scenario: Snapshot-ready token context
- **WHEN** a token or validation result is generated
- **THEN** the model can carry user, tenant, role, auth, and data policy version fields without embedding full permission data in long-lived tokens

#### Scenario: Permission change invalidation
- **WHEN** an administrator changes role, menu, API permission, organization assignment, or data policy data
- **THEN** the system can increment the relevant version so services can invalidate cached authorization data
