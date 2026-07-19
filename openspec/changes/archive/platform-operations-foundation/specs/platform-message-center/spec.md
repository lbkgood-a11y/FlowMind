## ADDED Requirements

### Requirement: System message delivery
The system SHALL allow backend services and authorized administrators to create station messages for one or more recipients.

#### Scenario: Send message to users
- **WHEN** a message is created with recipient user IDs, title, content, and message type
- **THEN** the system creates one inbox record per recipient and sets each record to unread

#### Scenario: Send message from announcement
- **WHEN** a published announcement requests station message delivery
- **THEN** the system creates message records for users in the announcement target scope

### Requirement: User inbox management
The system SHALL provide inbox APIs for users to query, read, and delete their own station messages.

#### Scenario: Query inbox
- **WHEN** an authenticated user queries the inbox by read state, type, keyword, or time range
- **THEN** the system returns only that user's messages with pagination

#### Scenario: Mark message read
- **WHEN** an authenticated user marks one of their messages as read
- **THEN** the system updates read state and read time for that user's inbox record

### Requirement: Message administration
The system SHALL allow authorized administrators to query sent messages and delivery state.

#### Scenario: Query delivery state
- **WHEN** an authorized administrator opens message administration
- **THEN** the system returns message batches and recipient delivery statistics

### Requirement: Message authorization
The system MUST enforce permissions for message administration while allowing authenticated users to manage only their own inbox.

#### Scenario: User reads another user's message
- **WHEN** a user attempts to read or delete a message belonging to another user
- **THEN** the system rejects the operation
