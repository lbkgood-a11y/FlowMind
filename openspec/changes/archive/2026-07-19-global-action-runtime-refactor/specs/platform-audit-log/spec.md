## MODIFIED Requirements

### Requirement: Operation Audit Recording
The system SHALL record auditable platform management and business-changing operations with actor, request, resource, Global Action identity when available, result, latency, and trace metadata.

#### Scenario: Successful operation audit
- **WHEN** an authenticated user performs a protected management API operation or Global Action
- **THEN** the system records an audit log containing user ID, username, permission code, HTTP method or action source, path or action type, target resource, result status, latency, client IP, user agent, action id when available, correlation id when available, and TraceId

#### Scenario: Failed operation audit
- **WHEN** a protected management API operation or Global Action fails with a business or system error
- **THEN** the system records an audit log with failure status, normalized action status when available, and a bounded error message

#### Scenario: Public endpoint excluded from operation audit
- **WHEN** a public endpoint such as login or health check is called
- **THEN** the system does not create an operation audit log unless a dedicated login log requirement applies

### Requirement: Operation Audit Query
The system SHALL allow authorized administrators to query operation audit logs by user, action, path, result, time range, Global Action id, action type, source, target, correlation id, trace id, and idempotency key.

#### Scenario: Query audit logs
- **WHEN** an authorized administrator queries audit logs with filters and pagination
- **THEN** the system returns matching audit records ordered by operation time descending

#### Scenario: Query by action id
- **WHEN** an authorized administrator searches for a specific Global Action id
- **THEN** the system returns the related operation audit record and its bounded action execution summary

#### Scenario: Unauthorized audit query rejected
- **WHEN** a user lacks the audit log read permission
- **THEN** the backend rejects the audit log query and the frontend hides the audit log page

### Requirement: Operation Audit Detail
The system SHALL provide a detail view for an operation audit record including request parameters summary, response summary, Global Action summary when available, error message, TraceId, and operator metadata.

#### Scenario: View audit detail
- **WHEN** an authorized administrator opens an audit log detail for an HTTP-only operation
- **THEN** the system returns request parameters summary, response summary, error message, TraceId, and operator metadata for that audit record

#### Scenario: View action audit detail
- **WHEN** an authorized administrator opens an audit log detail linked to a Global Action
- **THEN** the system returns action id, action type, source, actor, target, normalized status, idempotency key, correlation id, owner service, execution reference, result summary, and redacted payload summary
