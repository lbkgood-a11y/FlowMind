## 1. Service and Routing Setup

- [x] 1.1 Add `service-ops` Maven module and register it under `trio-base-services`.
- [x] 1.2 Add `service-ops` Spring Boot application, configuration, Flyway, MyBatis, validation, and test dependencies.
- [x] 1.3 Add gateway routes for announcements, messages, files, import/export tasks, and jobs.
- [x] 1.4 Add local development configuration for the operations service port and file storage base path.

## 2. Data Model and Authorization Metadata

- [x] 2.1 Add `service-ops` database migration for announcements, announcement reads, messages, message recipients, files, file references, import/export tasks, jobs, and job execution logs.
- [x] 2.2 Add `service-auth` migration to seed operations menus, API permissions, and button permissions.
- [x] 2.3 Ensure seeded button permission codes match frontend buttons and backend permission checks exactly.
- [x] 2.4 Add common request user context handling in `service-ops` compatible with gateway/auth headers.

## 3. Announcement and Message Backend

- [x] 3.1 Implement announcement DTOs, entities, mappers, service, and controller.
- [x] 3.2 Implement announcement publish, unpublish, target scope query, read state, and unread count.
- [x] 3.3 Implement station message batch creation, recipient inbox, read/unread, delete, and unread count.
- [x] 3.4 Implement message administration query and delivery statistics.

## 4. File, Import/Export, and Job Backend

- [x] 4.1 Implement local file storage adapter, file upload, metadata query, download, disable, enable, and delete.
- [x] 4.2 Implement business file reference bind and query APIs.
- [x] 4.3 Implement import/export task creation, query, cancellation, progress update, result file, and failure detail APIs.
- [x] 4.4 Implement job definition CRUD, enable/disable, manual trigger, scheduler registration, and execution logging.

## 5. Frontend Operations Console

- [x] 5.1 Add operations API clients and TypeScript types in `web-antd`.
- [x] 5.2 Add routes and locale entries for announcement management, message center, file center, import/export tasks, and job scheduler.
- [x] 5.3 Build announcement management page with search, create, edit, publish, unpublish, delete, and read-state actions.
- [x] 5.4 Build message center page with inbox actions and administrator delivery-state view.
- [x] 5.5 Build file center page with upload, download, reference query, enable/disable, and delete actions.
- [x] 5.6 Build import/export task page with task query, cancel, result download, and failure detail download.
- [x] 5.7 Build job scheduler page with definition CRUD, enable/disable, manual trigger, and execution log view.
- [x] 5.8 Bind every operation button to the seeded button permission code and keep search visible with page-level menu permission.

## 6. Verification

- [x] 6.1 Run OpenSpec validation for `platform-operations-foundation`.
- [x] 6.2 Run backend tests for changed Java modules.
- [x] 6.3 Run frontend type check for `web-antd`.
- [x] 6.4 Smoke test key APIs through the gateway with admin authorization.
- [x] 6.5 Smoke test frontend pages for routing, visible buttons, and no permission mismatch.
