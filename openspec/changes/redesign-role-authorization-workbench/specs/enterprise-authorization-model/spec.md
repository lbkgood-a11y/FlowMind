## ADDED Requirements

### Requirement: Authorization resources declare field enforcement capability
Every resource exposing configurable fields SHALL declare whether its owner runtime enforces read hiding, read masking, and write denial, and the registry SHALL expose the verified capability metadata to management clients.

#### Scenario: Owner advertises complete enforcement
- **WHEN** an owner service registers read-hide, read-mask, and write-deny support and passes its authorization contract tests
- **THEN** the registry marks all three capabilities enforced for that resource

#### Scenario: Owner does not implement field enforcement
- **WHEN** a resource registers fields without a verified enforcement adapter
- **THEN** the registry marks the operations unsupported and management clients cannot represent the rules as effective runtime protection

### Requirement: Field-policy capable owners enforce decisions server-side
An owner service that declares a field enforcement capability MUST apply the corresponding field decision to API reads or writes independently of frontend rendering.

#### Scenario: Hidden field requested through API
- **WHEN** a caller bypasses the frontend and requests a field whose effective read mode is `HIDDEN`
- **THEN** the owner service omits the field from the response

#### Scenario: Denied field submitted through API
- **WHEN** a caller submits a change to a field whose effective write mode is `DENIED`
- **THEN** the owner service rejects or removes the unauthorized change according to its declared endpoint contract and records authorization evidence

### Requirement: Decision preview supports a simulated role subject
The authorization service SHALL evaluate a non-persistent role simulation through the same grant, data-scope, field-rule, and guard-requirement pipeline used for actual users.

#### Scenario: Simulate role without assigned user
- **WHEN** an authorized administrator previews a role with supplied tenant and organization context
- **THEN** the service returns a simulation-marked decision without creating or changing a user-role assignment

#### Scenario: Simulation request crosses tenant boundary
- **WHEN** an administrator requests simulation for a role outside the authorized tenant
- **THEN** the service denies the request before evaluating grants or policies
