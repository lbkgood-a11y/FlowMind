## ADDED Requirements

### Requirement: User Registration
The system SHALL allow new users to register with a unique username, password (min 8 chars, mixed case + digit), and optional email. Password MUST be stored as BCrypt hash.

#### Scenario: Successful registration
- **WHEN** a visitor submits valid username, password, and email
- **THEN** the system creates a user account, returns `R<LoginResponse>` with JWT access token and refresh token, and records the registration timestamp

#### Scenario: Duplicate username
- **WHEN** a visitor submits a username that already exists
- **THEN** the system returns `R.fail(USER_ALREADY_EXISTS)` with HTTP 409

#### Scenario: Weak password
- **WHEN** a visitor submits a password shorter than 8 characters or without mixed case and digit
- **THEN** the system returns `R.fail(PASSWORD_TOO_WEAK)` with HTTP 400 and does not create the user

### Requirement: User Login
The system SHALL authenticate users by username + password and issue JWT AccessToken (TTL 5 min) + RefreshToken (TTL 30 min).

#### Scenario: Successful login
- **WHEN** a user submits correct username and password
- **THEN** the system returns `R<LoginResponse>` containing `accessToken`, `refreshToken`, token expiry, and basic user info (id, username, roles)

#### Scenario: Invalid credentials
- **WHEN** a user submits incorrect username or password
- **THEN** the system returns `R.fail(BAD_CREDENTIALS)` with HTTP 401

#### Scenario: Account disabled
- **WHEN** a disabled user attempts to log in
- **THEN** the system returns `R.fail(ACCOUNT_DISABLED)` with HTTP 403

### Requirement: Token Refresh
The system SHALL accept a valid RefreshToken and issue a new AccessToken without requiring re-login.

#### Scenario: Valid refresh
- **WHEN** a client submits a valid, unexpired RefreshToken
- **THEN** the system returns a new `LoginResponse` with updated `accessToken` and `refreshToken`

#### Scenario: Expired refresh token
- **WHEN** a client submits an expired RefreshToken
- **THEN** the system returns `R.fail(TOKEN_EXPIRED)` with HTTP 401 and the client MUST re-login

### Requirement: Token Validation
The system SHALL provide a `/api/v1/auth/validate` endpoint that accepts a Bearer token and returns whether it is valid, along with the user's identity and permissions.

#### Scenario: Valid token
- **WHEN** the API gateway sends a valid AccessToken to the validate endpoint
- **THEN** the system returns `R<TokenValidateResult>` with `valid: true`, `userId`, `username`, and `permissions` list

#### Scenario: Invalid or expired token
- **WHEN** the API gateway sends an expired or tampered token
- **THEN** the system returns `R<TokenValidateResult>` with `valid: false` and an empty permissions list

### Requirement: RBAC — Role Management
The system SHALL support CRUD operations on roles. Each role has a unique `roleCode` and can be associated with multiple permissions.

#### Scenario: Create a role
- **WHEN** an admin user creates a role with `roleCode: "TENANT_ADMIN"` and attaches permission IDs
- **THEN** the system persists the role and its permission associations, returns `R<RoleVO>`

#### Scenario: Assign role to user
- **WHEN** an admin assigns role "TENANT_ADMIN" to a user
- **THEN** the user's subsequent token validation returns the union of all permissions from all assigned roles

### Requirement: RBAC — Permission Guard
The system SHALL resolve user permissions from their roles and expose them via TokenValidation. Each permission is a `resource:action` pair (e.g., `GET:/api/v1/users`).

#### Scenario: User with multiple roles
- **WHEN** a user has roles ["READER", "EDITOR"] and role READER grants `GET:/api/v1/docs` while EDITOR grants `POST:/api/v1/docs`
- **THEN** the TokenValidateResult.permissions list contains both `GET:/api/v1/docs` and `POST:/api/v1/docs`
