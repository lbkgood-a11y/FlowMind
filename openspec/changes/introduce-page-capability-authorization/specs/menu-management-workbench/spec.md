## ADDED Requirements

### Requirement: Product pages register business capabilities
The menu management system SHALL associate each business page with versioned access, read, and operation capabilities whose display names match the actual page and whose technical targets are maintained by product developers or authorized platform administrators.

#### Scenario: Register User Management capabilities
- **WHEN** the User Management module publishes its capability manifest
- **THEN** menu management shows enter page, view users, add user, edit user, disable user, reset password, and export as page operations with mapping readiness

### Requirement: Implementation and diagnostic views are separated
The system SHALL hide resource codes, action codes, endpoints, HTTP methods, and owner services from normal implementation workflows and SHALL expose technical mapping evidence only in an authorized diagnostic view.

#### Scenario: Implementation person sees a broken operation
- **WHEN** a page operation is missing a required target
- **THEN** the role workflow shows a plain-language unavailable status while the platform diagnostic view identifies the missing target and manifest version

### Requirement: Page mapping readiness is validated
The system SHALL classify each active page and page capability as ready, partial, broken, or unmapped by validating dependencies, target resources and actions, declared scope support, field enforcement support, and guard ownership.

#### Scenario: Required field enforcement is unverified
- **WHEN** a page capability declares a field restriction that its owner service has not verified and enforced
- **THEN** the capability is not ready for production publication and diagnostics identify the missing enforcement contract

### Requirement: Mapping changes produce impact analysis
The system SHALL version page capability mappings and SHALL identify active role releases and users affected by additions, removals, or target changes before those changes can alter production authorization.

#### Scenario: Remove an operation from a page
- **WHEN** a product release removes a registered business operation used by active roles
- **THEN** menu management reports affected roles and users, marks their releases drifted, and leaves existing projections unchanged until reviewed

