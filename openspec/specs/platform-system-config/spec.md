# platform-system-config Specification

## Purpose
TBD - created by archiving change platform-governance-foundation. Update Purpose after archive.
## Requirements
### Requirement: System Config Management
The system SHALL allow authorized administrators to view and update platform system configuration items.

#### Scenario: Query system configs
- **WHEN** an authorized administrator queries system configs by group, key, status, or keyword
- **THEN** the system returns matching config records ordered by group and sort order

#### Scenario: Update system config
- **WHEN** an authorized administrator updates a config value and description
- **THEN** the system validates the value type, persists the update, and records update metadata

#### Scenario: Unauthorized config update rejected
- **WHEN** a user lacks system config update permission
- **THEN** the backend rejects the update and the frontend hides the save action

### Requirement: Sensitive Config Protection
The system SHALL protect sensitive system config values from casual disclosure.

#### Scenario: Sensitive config masked in management list
- **WHEN** a sensitive config appears in a management list or detail response
- **THEN** the system returns a masked display value instead of the raw config value

#### Scenario: Sensitive config update
- **WHEN** an authorized administrator submits a new value for a sensitive config
- **THEN** the system stores the new raw value and continues masking it in subsequent read responses

### Requirement: Runtime Config Reading
The system SHALL provide a runtime-safe read API for enabled config values.

#### Scenario: Read config by key
- **WHEN** platform code requests an enabled config by key
- **THEN** the system returns the raw value for server-side use or the default value when no enabled override exists

#### Scenario: Disabled config falls back
- **WHEN** a config item is disabled
- **THEN** runtime reads do not use the disabled value and fall back to default behavior

