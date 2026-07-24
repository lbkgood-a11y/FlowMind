# lowcode-form-relations Specification

## Purpose
TBD - created by archiving change complete-lowcode-development-loop. Update Purpose after archive.
## Requirements
### Requirement: Application versions define form relation graphs
The system SHALL let designers define tenant-scoped parent-child form relations on an application version using stable relation codes, published form definition ids, cardinality, parent key, child foreign key, sort order, and cascade policy.

#### Scenario: Define master detail relation
- **WHEN** a designer links a published leave-request master form to a published leave-day detail form as one-to-many
- **THEN** the draft stores the relation against the application version without mutating either published form schema

### Requirement: Relation publication validates a bounded acyclic graph
The system SHALL reject relation graphs containing cycles, self references, duplicate relation codes, cross-tenant forms, unpublished forms, incompatible relation fields, or depth greater than three levels.

#### Scenario: Publish master child grandchild graph
- **WHEN** an application defines one valid master, its child form, and the child's grandchild form
- **THEN** publication accepts and freezes the three-level graph

#### Scenario: Reject fourth level
- **WHEN** a relation graph introduces a fourth form level
- **THEN** publication fails with a validation error identifying the relation path

### Requirement: Runtime supports transactional cascade submission
The system SHALL validate and persist a master instance, child instances, grandchild instances, and their relation rows atomically.

#### Scenario: Submit nested data successfully
- **WHEN** all master, child, and grandchild payloads conform to their published snapshots and field permissions
- **THEN** the runtime saves all instances and ordered relation rows in one transaction

#### Scenario: Grandchild validation fails
- **WHEN** any grandchild payload is invalid
- **THEN** no master, child, grandchild, or relation record from that submission is committed

### Requirement: Runtime reads nested instance graphs safely
The system SHALL return an authorized instance graph bounded by the published relation snapshot, tenant, maximum depth, and field policies.

#### Scenario: Open master detail
- **WHEN** an authorized user opens a master instance detail
- **THEN** the response includes its permitted children and grandchildren in configured order with field masking applied per form resource

