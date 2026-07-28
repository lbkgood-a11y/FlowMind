## ADDED Requirements

### Requirement: Role function grants are replaced atomically
The system SHALL replace a role's complete active ALLOW function-grant set in one transaction while preserving DENY grants and unrelated policy types.

#### Scenario: Replace complete role grant set
- **WHEN** an administrator submits a valid complete function-grant set for a role
- **THEN** the system persists exactly that ALLOW set, returns its count and version, and exposes no partial intermediate state

#### Scenario: Invalid action rolls back replacement
- **WHEN** any requested resource or action is not registered and active in the role tenant
- **THEN** the system rejects the request and leaves all existing role grants unchanged

#### Scenario: Concurrent role edit detected
- **WHEN** a replacement request supplies an authorization version older than the current grant version
- **THEN** the system rejects the stale update and requires the client to reload the role profile

### Requirement: Effective permission caches remain coherent
The system SHALL invalidate or bypass cached effective permissions whenever role membership or function grants change.

#### Scenario: User role reassigned
- **WHEN** an administrator changes a user's assigned roles
- **THEN** subsequent token validation and route resolution use permissions from the new role set without waiting for cache TTL expiry

#### Scenario: Permission cache unavailable
- **WHEN** Redis is unavailable or a cached permission entry cannot be decoded
- **THEN** the service queries authoritative authorization data and does not widen access because of the cache failure

### Requirement: Authorization clients observe persisted state
The role authorization client SHALL use the atomic replacement contract and SHALL display success only after the persisted role profile matches the requested grant set.

#### Scenario: Full selection saved
- **WHEN** an administrator selects all function actions and saves
- **THEN** one logical replacement succeeds and the displayed selected count equals the persisted active grant count

#### Scenario: Version conflict shown
- **WHEN** another administrator changes the role before the current save completes
- **THEN** the client reloads or prompts for reload and does not report the stale selection as saved
