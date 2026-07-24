## ADDED Requirements

### Requirement: Published lowcode applications complete authorization navigation
Publishing a lowcode application SHALL synchronize its semantic application resource and SHALL make it discoverable through the application center only after the current user receives an applicable VIEW grant; role menu visibility SHALL remain a projection of grants.

#### Scenario: Role receives application and form grants
- **WHEN** an administrator grants `LOWCODE_APP:LEAVE_APP:VIEW` and appropriate `LOWCODE_FORM:LEAVE_REQUEST` actions to a user's role
- **THEN** the user can see the application center entry, discover the published leave application, and execute only the granted form actions

#### Scenario: Revoke application view
- **WHEN** the role's application VIEW grant is removed
- **THEN** the application disappears from application-center results without deleting the published application or its data

### Requirement: Related forms retain independent least-privilege decisions
The runtime SHALL evaluate function and field authorization for every form resource participating in a master-child-grandchild graph rather than inheriting unrestricted access from the master form.

#### Scenario: Child form read denied
- **WHEN** a user can view the master form but lacks VIEW authorization for a child form
- **THEN** the runtime omits or denies the child section and does not leak child instance data
