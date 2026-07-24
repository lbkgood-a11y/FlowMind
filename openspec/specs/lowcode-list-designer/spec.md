# lowcode-list-designer Specification

## Purpose
TBD - created by archiving change complete-lowcode-development-loop. Update Purpose after archive.
## Requirements
### Requirement: Designers configure list pages declaratively
The application workbench SHALL let designers select list columns, filters, default sort, page size, status display, and row actions from fields and actions registered on the application draft.

#### Scenario: Build leave request list
- **WHEN** a designer selects applicant, leave type, start date, end date, and status columns plus applicant and status filters
- **THEN** the workbench saves allowlisted LIST page metadata referencing those field keys

### Requirement: List metadata is validated before publication
The lowcode service SHALL validate column and filter field references, formats, widths, sort directions, page size bounds, and row action references before publishing an application.

#### Scenario: Reject unknown list field
- **WHEN** LIST metadata references a field absent from the published form snapshot
- **THEN** publication fails and identifies the invalid field key

### Requirement: Generic runtime renders configured lists
The runtime SHALL render list columns, filters, sorting, paging, formatting, and authorized row actions from the immutable published LIST metadata.

#### Scenario: User opens configured application
- **WHEN** a user with VIEW authorization opens the leave application
- **THEN** the runtime shows the configured list and only row actions allowed by authorization decisions

