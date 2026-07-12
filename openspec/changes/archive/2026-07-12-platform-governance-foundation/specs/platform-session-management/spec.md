## ADDED Requirements

### Requirement: Login Event Recording
The system SHALL record authentication events for successful and failed login attempts.

#### Scenario: Successful login event
- **WHEN** a user logs in with valid credentials
- **THEN** the system records a login log with username, user ID, result success, client IP, user agent, login time, and TraceId

#### Scenario: Failed login event
- **WHEN** a login attempt fails due to bad credentials or disabled account
- **THEN** the system records a login log with username, result failure, failure reason, client IP, user agent, login time, and TraceId

### Requirement: Session Tracking
The system SHALL track issued login sessions using token identifiers and expiration metadata.

#### Scenario: Create session on login
- **WHEN** a user logs in successfully
- **THEN** the system creates an active session record containing user ID, username, access token identifier, refresh token identifier, issued time, expiration time, client IP, and user agent

#### Scenario: Mark session logged out
- **WHEN** a user logs out
- **THEN** the system marks the related session as logged out when the token identifier can be resolved

### Requirement: Session Management
The system SHALL allow authorized administrators to query and revoke active sessions.

#### Scenario: Query active sessions
- **WHEN** an authorized administrator queries sessions by username, status, or time range
- **THEN** the system returns matching sessions ordered by last active time descending

#### Scenario: Revoke session
- **WHEN** an authorized administrator revokes an active session
- **THEN** the system marks the session as revoked and records the revocation operator and time

#### Scenario: Unauthorized session management rejected
- **WHEN** a user lacks the session management permission
- **THEN** the backend rejects session queries or revocation and the frontend hides the corresponding actions
