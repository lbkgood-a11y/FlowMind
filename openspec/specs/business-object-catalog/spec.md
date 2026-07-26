# business-object-catalog Specification

## Purpose

The Business Object Catalog defines process-aware business object metadata for workflow package design, runtime closure, permission mapping, and AI follow-up governance. The catalog is hosted by `service-workflow-engine` because the current metadata is tightly coupled to process launch, outcome, closure, and executor registration. `service-business-catalog` is not the object metadata owner; it owns only the document timeline projection read model.

## Requirements

### Requirement: Workflow engine owns process business object metadata
The system SHALL store process business object metadata in `service-workflow-engine` using the `wf_biz_object*` tables and SHALL expose it through `/api/v1/process-business-objects`.

#### Scenario: Register process business object
- **WHEN** an expense report or purchase order process object is published
- **THEN** `service-workflow-engine` stores the object type, owner service code, version, status definitions, form bindings, permission mappings, business actions, domain events, Agent actions, and recommended templates

#### Scenario: Query process object metadata
- **WHEN** the workflow designer opens a process package configuration page
- **THEN** it loads published object metadata from `/api/v1/process-business-objects` and renders selectable statuses, forms, actions, events, permissions, and Agent follow-up options

### Requirement: Service-business-catalog does not store object manifests
The system SHALL NOT use `service-business-catalog` as the source of truth for business object manifests, object metadata, status options, field metadata, action metadata, page metadata, or tenant overrides.

#### Scenario: Deprecated catalog storage removed
- **WHEN** `service-business-catalog` starts after the refactor
- **THEN** it has no `bc_business_object` runtime dependency and does not expose `/api/v1/business-catalog/**` object metadata APIs

#### Scenario: Shared metadata bridge
- **WHEN** another service needs shared process object metadata
- **THEN** it calls a workflow-owned API or internal bridge such as `/internal/v1/workflow-business-catalog/objects/{typeCode}` instead of reading `service-business-catalog` storage

### Requirement: Status groups are normalized across process business domains
The Business Object Catalog SHALL define normalized status groups for process-enabled business documents while allowing owner services to expose domain-specific statuses.

#### Scenario: Domain status maps to normalized group
- **WHEN** an expense report has domain status `IN_APPROVAL`
- **THEN** the catalog maps it to a normalized status group that workflow runtime, frontend page standards, and closure policies can understand

#### Scenario: Terminal status
- **WHEN** a document status is marked terminal
- **THEN** workflow runtime and frontend affordances treat lifecycle actions and editing behavior according to the terminal status metadata unless owner-hosted action availability returns a more specific result

### Requirement: Catalog exposes governed executor metadata
The Business Object Catalog SHALL expose only governed executor metadata for process launch, closure effects, domain events, and Agent follow-up actions and SHALL NOT store arbitrary URLs, SQL, scripts, class names, or free Prompt execution definitions.

#### Scenario: Render process action option
- **WHEN** a process designer selects an object action such as `updateStatus`
- **THEN** the catalog provides action code, display name, action type, executor key, default mode, permission action, parameter schema, and sort order

#### Scenario: Reject arbitrary execution definition
- **WHEN** a catalog row attempts to define an arbitrary URL, SQL statement, dynamic class, script, or Prompt as executable behavior
- **THEN** the catalog and runtime reject it because side effects must be implemented by registered code executors

### Requirement: Catalog supports forms, permissions, events, Agent actions, and templates
The Business Object Catalog SHALL expose the metadata required for workflow design and runtime validation, including form bindings, permission mappings, domain events, Agent follow-up actions, and recommended templates.

#### Scenario: Configure workflow package
- **WHEN** a process package references a published business object type
- **THEN** the workflow designer can select forms, status gates, permissions, outcome actions, events, Agent follow-ups, and templates from the catalog without hard-coding them in the frontend

#### Scenario: Validate closure policy
- **WHEN** a workflow package is saved or published
- **THEN** `service-workflow-engine` validates referenced statuses, permission actions, business action executors, domain events, and Agent action executors against the published catalog

### Requirement: Tenant-specific metadata overrides global defaults
The Business Object Catalog SHALL support `GLOBAL` defaults with tenant-scoped overrides and offline markers inside the workflow-owned catalog tables.

#### Scenario: Tenant override
- **WHEN** tenant `T1` overrides a global business object status label, form binding, permission mapping, action, event, Agent action, or template
- **THEN** catalog reads for tenant `T1` merge the tenant-specific child rows over the `GLOBAL` defaults while other tenants continue to receive global metadata

#### Scenario: Tenant offline object
- **WHEN** tenant `T1` marks a globally available object offline
- **THEN** catalog reads for tenant `T1` omit or reject that object while global and other tenant reads remain unaffected

### Requirement: Workflow catalog remains separate from document timeline projection
The system SHALL keep workflow-owned process object metadata separate from `service-business-catalog` document timeline projection data.

#### Scenario: Process metadata read
- **WHEN** the frontend needs object metadata for workflow design
- **THEN** it calls `/api/v1/process-business-objects/**` through `service-workflow-engine`

#### Scenario: Document timeline read
- **WHEN** the frontend needs a document timeline
- **THEN** it calls `/api/v1/business-timeline` through `service-business-catalog`, which reads only `bc_document_timeline_event`
