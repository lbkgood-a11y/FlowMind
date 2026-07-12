## ADDED Requirements

### Requirement: File upload and metadata
The system SHALL allow authenticated users with upload permission to upload files and store file metadata.

#### Scenario: Upload file
- **WHEN** an authenticated user uploads a valid file
- **THEN** the system stores the physical file, creates a metadata record, and returns the file identifier

#### Scenario: Reject invalid file
- **WHEN** an authenticated user uploads a file exceeding configured size or type limits
- **THEN** the system rejects the upload and does not create metadata

### Requirement: File download authorization
The system MUST authorize file download by ownership, business reference, or explicit file management permission.

#### Scenario: Owner downloads file
- **WHEN** the file owner downloads the file
- **THEN** the system streams the file content and records download metadata

#### Scenario: Unauthorized download
- **WHEN** a user without ownership, business access, or file management permission downloads the file
- **THEN** the system rejects the download

### Requirement: File lifecycle management
The system SHALL allow authorized administrators to query, disable, enable, and delete file metadata.

#### Scenario: Disable file
- **WHEN** an authorized administrator disables a file
- **THEN** the system prevents future normal downloads while retaining metadata and audit history

#### Scenario: Delete file metadata
- **WHEN** an authorized administrator deletes an unused file metadata record
- **THEN** the system marks it deleted and excludes it from normal list queries

### Requirement: Business file references
The system SHALL support binding files to business type and business ID references.

#### Scenario: Bind file reference
- **WHEN** a service binds a file to a business object
- **THEN** the system records business type, business ID, file ID, and creator metadata
