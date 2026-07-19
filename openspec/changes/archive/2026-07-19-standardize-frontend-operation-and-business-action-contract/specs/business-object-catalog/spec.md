## ADDED Requirements

### Requirement: Platform catalog stores business object metadata
The system SHALL maintain a platform-level Business Object Catalog for business object types, owner services, display metadata, status definitions, action definitions, field metadata, page metadata, permission mappings, and tenant overrides.

#### Scenario: Register purchase order object
- **WHEN** `service-scm` synchronizes a purchase order manifest
- **THEN** the catalog stores the purchase order object type, owner service, display name, status groups, supported actions, page metadata, field metadata, and permission mappings

#### Scenario: Query object metadata
- **WHEN** the frontend opens a document page for a registered object type
- **THEN** it can load the object metadata needed to render consistent status, actions, fields, page sections, and document chrome

### Requirement: Status groups are normalized across business domains
The Business Object Catalog SHALL define normalized status groups for business documents while allowing owner services to expose domain-specific statuses.

#### Scenario: Domain status maps to normalized group
- **WHEN** a WMS inbound order has domain status `RECEIVING`
- **THEN** the catalog or owner service maps it to a normalized status group that frontend page standards can understand

#### Scenario: Terminal status
- **WHEN** a document status is marked terminal
- **THEN** the frontend treats lifecycle actions and editing affordances according to the terminal status metadata unless backend action availability returns a more specific result

### Requirement: Catalog exposes action metadata for rendering
The Business Object Catalog SHALL expose action metadata including action code, Global Action type, display name, group, order, danger level, confirmation policy, default execution mode, owner service, and payload schema reference.

#### Scenario: Render primary action
- **WHEN** a document metadata response marks `SUBMIT` as a primary action
- **THEN** the frontend renders it in the standard primary action position if action availability also marks it visible

#### Scenario: Render more actions
- **WHEN** a document has secondary or dangerous actions beyond the direct action limit
- **THEN** the frontend renders them through the standard more-action surface using catalog ordering and grouping

### Requirement: Catalog supports page and field metadata
The Business Object Catalog SHALL expose page and field metadata required for consistent list pages, detail pages, query bars, document summary forms, editable grids, side panels, and timelines.

#### Scenario: Render document sections
- **WHEN** a document page loads page metadata
- **THEN** it renders configured sections using standard page pattern components without hard-coding page structure in each business module

#### Scenario: Apply field metadata
- **WHEN** a field is configured as required, readonly, hidden, masked, or grid-editable
- **THEN** the frontend renders the field consistently and owner services enforce the field rule during writes

### Requirement: Owner services synchronize catalog manifests
Owner services SHALL synchronize catalog manifests through an internal contract so business object metadata can be registered, versioned, updated, and taken offline without manual frontend duplication.

#### Scenario: Service publishes manifest
- **WHEN** an owner service starts or publishes a business object version
- **THEN** it synchronizes the object manifest to the Business Object Catalog with a stable version and owner-service identity

#### Scenario: Manifest invalid
- **WHEN** a manifest is missing required object type, owner service, status, or action metadata
- **THEN** the catalog rejects the manifest and records diagnostics without publishing partial metadata

### Requirement: Tenant-specific metadata overrides global defaults
The Business Object Catalog SHALL support global defaults with tenant-scoped overrides and offline markers.

#### Scenario: Tenant override
- **WHEN** tenant `T1` overrides a global business object field label or available action configuration
- **THEN** catalog reads for tenant `T1` return the tenant-specific metadata while other tenants continue to receive global defaults

#### Scenario: Tenant offline object
- **WHEN** tenant `T1` marks a globally available object offline
- **THEN** catalog reads for tenant `T1` omit or reject that object while global and other tenant reads remain unaffected

### Requirement: Workflow engine consumes catalog instead of owning all business object metadata
The system SHALL allow `service-workflow-engine` to consume and contribute Business Object Catalog metadata without being the exclusive owner of all business object definitions.

#### Scenario: Workflow uses catalog object
- **WHEN** a process package references a registered business object type
- **THEN** the workflow engine loads object actions, statuses, permissions, and form references from the catalog or synchronized local projection

#### Scenario: SCM object without workflow
- **WHEN** an SCM document does not currently use an approval workflow
- **THEN** the object can still be registered, rendered, authorized, and action-enabled through the Business Object Catalog
