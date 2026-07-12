## ADDED Requirements

### Requirement: Operation Audit Recording
The system SHALL record auditable platform management operations with actor, request, resource, result, latency, and trace metadata.

#### Scenario: Successful operation audit
- **WHEN** an authenticated user performs a protected management API operation
- **THEN** the system records an audit log containing user ID, username, permission code, HTTP method, path, action, result status, latency, client IP, user agent, and TraceId

#### Scenario: Failed operation audit
- **WHEN** a protected management API operation fails with a business or system error
- **THEN** the system records an audit log with failure status and a bounded error message

#### Scenario: Public endpoint excluded from operation audit
- **WHEN** a public endpoint such as login or health check is called
- **THEN** the system does not create an operation audit log unless a dedicated login log requirement applies

### Requirement: Operation Audit Query
The system SHALL allow authorized administrators to query operation audit logs by user, action, path, result, and time range.

#### Scenario: Query audit logs
- **WHEN** an authorized administrator queries audit logs with filters and pagination
- **THEN** the system returns matching audit records ordered by operation time descending

#### Scenario: Unauthorized audit query rejected
- **WHEN** a user lacks the audit log read permission
- **THEN** the backend rejects the audit log query and the frontend hides the audit log page

### Requirement: Operation Audit Detail
The system SHALL provide a detail view for an operation audit record.

#### Scenario: View audit detail
- **WHEN** an authorized administrator opens an audit log detail
- **THEN** the system returns request parameters summary, response summary, error message, TraceId, and operator metadata for that audit record
