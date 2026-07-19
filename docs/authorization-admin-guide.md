# Authorization Administration Guide

The role authorization drawer separates navigation from business authorization. Administrators configure access in four business tabs and can preview the final decision before rollout.

## Menu Navigation

Use **菜单导航** only to decide what appears in the application menu. A menu entry can point users to a page, but it is not the durable authorization contract for lowcode forms or handwritten documents.

Keep menu grants narrow:

- Grant catalog and page menus for navigation.
- Avoid creating fake hidden menus for every document action.
- Keep legacy menu permission codes during migration, but prefer resource grants for new business documents.

## Function Permissions

Use **功能权限** to grant resource actions directly to a role.

Resources are grouped by business type, such as lowcode apps, lowcode forms, fields, workflow actions, and custom documents. Action labels use product language:

- `VIEW`: view app, page, document, or detail.
- `CREATE`: create a new document.
- `EDIT`: change an existing document.
- `SUBMIT`: submit a document or launch approval.
- `APPROVE` / `REJECT`: handle approval tasks.
- `EXPORT`: export data.
- `DESIGN`, `PUBLISH`, `OFFLINE`: manage lowcode lifecycle.

Deny grants are evaluated before allow grants. Use deny only for exceptional restrictions because it will override role allows and legacy fallback.

## Data Range

Use **数据范围** to configure which records the role can see for a resource action.

Supported business labels:

- **本人**: only records owned or submitted by the current user.
- **本组织**: records owned by the user's organization.
- **本组织及下级**: records owned by the user's organization tree.
- **指定组织**: records in administrator-selected organizations.
- **我参与的**: records where the user is a participant.
- **我的待办候选**: workflow records where the user is a candidate or assignee.
- **全部**: all tenant records for the resource.

If a service cannot compile a selected range safely for its own storage model, it must return no records instead of widening access.

## Field Rules

Use **字段规则** to configure read and write behavior for sensitive or operational fields.

Read modes:

- **可见**: return the field value.
- **脱敏**: return only the masked representation.
- **隐藏**: omit the field from responses.

Write modes:

- **可编辑**: accept changes.
- **只读**: render as non-editable and reject unauthorized changes server-side.
- **禁写**: block writes to the field.

Frontend rendering is a convenience. Services still enforce field masking and write rejection before returning or accepting data.

## Business Rules

Use **业务规则** to review domain guard templates such as pending task status, workflow candidate, no self-approval, document status, and archived lock.

These templates are not free-form ABAC expressions. They describe checks owned by the business service. `service-auth` returns required guards in the decision; the owning service evaluates the actual domain state and combines the result.

## Decision Preview

Use **决策预览** before assigning a role broadly.

Enter a user id, choose resource and action, then run preview. The result shows:

- allowed or denied
- matched grant
- data range
- field rules
- required business guards
- denial or allow reasons

Preview does not log raw form or document payload values. Production enforcement logs only keys, reasons, versions, and trace metadata needed for audit.
