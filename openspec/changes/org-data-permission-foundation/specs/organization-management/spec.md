## ADDED Requirements

### Requirement: Organization Dimension Management
The system SHALL provide built-in organization dimensions for administrative, legal, accounting, and business organization trees. Each dimension MUST have a stable code, display name, status, and default flag.

#### Scenario: List organization dimensions
- **WHEN** an authorized user requests the organization dimensions
- **THEN** the system returns the enabled dimensions including `ADMIN`, `LEGAL`, `ACCOUNTING`, and `BUSINESS`

#### Scenario: Default administrative dimension
- **WHEN** the system initializes organization data
- **THEN** the `ADMIN` dimension is available and marked as the default dimension for user primary organization assignment

### Requirement: Organization Unit Management
The system SHALL manage organization units as reusable organization objects independent from any single tree. Organization units MUST include code, name, type, status, sort order, and description.

#### Scenario: Create organization unit
- **WHEN** an authorized user creates an organization unit with a unique code and valid name
- **THEN** the system persists the organization unit and returns the created record

#### Scenario: Reject duplicate organization unit code
- **WHEN** an authorized user creates an organization unit using a code that already exists in the same tenant
- **THEN** the system rejects the request and does not create a duplicate organization unit

#### Scenario: Update organization unit
- **WHEN** an authorized user updates an organization unit name, type, status, sort order, or description
- **THEN** the system persists the changes without changing organization relationships in other dimensions

### Requirement: Dimension Tree Management
The system SHALL maintain organization parent-child relationships per organization dimension. A single organization unit MAY appear under different parents in different dimensions.

#### Scenario: List organization tree by dimension
- **WHEN** an authorized user requests the tree for a dimension code
- **THEN** the system returns organization units ordered by tree path and sort order for that dimension

#### Scenario: Attach organization unit to dimension
- **WHEN** an authorized user attaches an organization unit under a parent in a dimension
- **THEN** the system creates or updates the dimension relationship and computes the tree path for that dimension

#### Scenario: Prevent cyclic relationship
- **WHEN** an authorized user attempts to move an organization unit under one of its descendants in the same dimension
- **THEN** the system rejects the request and leaves the existing tree unchanged

#### Scenario: Delete organization relation with children
- **WHEN** an authorized user attempts to remove an organization relation that has child relations in the same dimension
- **THEN** the system rejects the request until the children are moved or removed

### Requirement: User Organization Assignment
The system SHALL allow authorized users to assign users to organization units per dimension. At most one assignment per user per dimension SHALL be marked as primary.

#### Scenario: Assign user to organization units
- **WHEN** an authorized user saves organization assignments for a user and dimension
- **THEN** the system replaces the user's assignments for that dimension and marks one assignment as primary when provided

#### Scenario: Reject primary assignment outside selected organizations
- **WHEN** the primary organization is not included in the submitted organization assignment list
- **THEN** the system rejects the request and preserves the existing assignments

#### Scenario: List user organization assignments
- **WHEN** an authorized user requests a user's organization assignments
- **THEN** the system returns assignments grouped by dimension with primary, leader, position, and status metadata

### Requirement: Organization Management Authorization
The system SHALL protect organization management APIs and frontend actions with the same API permission code model used by the rest of system management.

#### Scenario: Read organization management
- **WHEN** a user has `/api/v1/org/units:GET`
- **THEN** the frontend allows viewing, searching, refreshing, and loading organization trees

#### Scenario: Write organization management
- **WHEN** a user lacks the relevant POST, PUT, or DELETE organization permission code
- **THEN** the frontend hides or disables the corresponding create, save, move, assign, or delete action and the backend rejects unauthorized calls
