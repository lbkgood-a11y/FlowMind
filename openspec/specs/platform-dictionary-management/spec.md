# platform-dictionary-management Specification

## Purpose
TBD - created by archiving change platform-governance-foundation. Update Purpose after archive.
## Requirements
### Requirement: Dictionary Type Management
The system SHALL allow authorized administrators to manage dictionary types.

#### Scenario: Create dictionary type
- **WHEN** an authorized administrator creates a dictionary type with a unique dictionary code and valid name
- **THEN** the system persists the dictionary type and returns the created record

#### Scenario: Reject duplicate dictionary code
- **WHEN** an administrator creates or updates a dictionary type using a duplicate code in the same tenant
- **THEN** the system rejects the request and preserves existing data

#### Scenario: Disable dictionary type
- **WHEN** an administrator disables a dictionary type
- **THEN** the enabled item query for that dictionary code returns no active options

### Requirement: Dictionary Item Management
The system SHALL allow authorized administrators to manage dictionary items under a dictionary type.

#### Scenario: Create dictionary item
- **WHEN** an authorized administrator creates a dictionary item with label, value, sort order, and status
- **THEN** the system persists the item under the selected dictionary type

#### Scenario: Query enabled dictionary items
- **WHEN** a frontend page requests enabled dictionary items by dictionary code
- **THEN** the system returns enabled items ordered by sort order and value

#### Scenario: System dictionary deletion guarded
- **WHEN** an administrator attempts to delete a system built-in dictionary type or item
- **THEN** the system rejects deletion and instructs the administrator to disable it instead

### Requirement: Dictionary Authorization
The system SHALL protect dictionary management APIs with API permission codes.

#### Scenario: Hide unauthorized dictionary actions
- **WHEN** a user lacks dictionary create, update, or delete permissions
- **THEN** the frontend hides or disables corresponding actions and the backend rejects unauthorized calls

