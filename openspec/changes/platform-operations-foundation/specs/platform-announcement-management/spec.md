## ADDED Requirements

### Requirement: Announcement lifecycle management
The system SHALL allow authorized administrators to create, edit, publish, unpublish, and delete platform announcements.

#### Scenario: Create announcement draft
- **WHEN** an authorized administrator creates an announcement with title, content, priority, and target scope
- **THEN** the system persists the announcement as a draft and records creator metadata

#### Scenario: Publish announcement
- **WHEN** an authorized administrator publishes a valid draft announcement
- **THEN** the system marks the announcement as published and makes it visible to users in the target scope

#### Scenario: Unpublish announcement
- **WHEN** an authorized administrator unpublishes a published announcement
- **THEN** the system hides the announcement from user-facing announcement queries without deleting its history

### Requirement: Announcement target scope
The system SHALL support announcement target scopes for all users, specific organizations, and specific users.

#### Scenario: Query visible announcements
- **WHEN** a user queries available announcements
- **THEN** the system returns only published announcements whose target scope contains the user

#### Scenario: Administrator filters announcements
- **WHEN** an authorized administrator filters announcements by status, priority, keyword, or publish time
- **THEN** the system returns matching announcement records with pagination

### Requirement: Announcement read state
The system SHALL track whether each target user has read a published announcement.

#### Scenario: Mark announcement as read
- **WHEN** a target user marks an announcement as read
- **THEN** the system records read time for that user and announcement

#### Scenario: Query unread announcement count
- **WHEN** a target user queries unread announcement count
- **THEN** the system returns the number of published target announcements not yet read by that user

### Requirement: Announcement authorization
The system MUST enforce menu, API, and button permissions for announcement management operations.

#### Scenario: Missing publish permission
- **WHEN** a user without announcement publish permission attempts to publish an announcement
- **THEN** the system rejects the operation and the frontend does not render the publish button
