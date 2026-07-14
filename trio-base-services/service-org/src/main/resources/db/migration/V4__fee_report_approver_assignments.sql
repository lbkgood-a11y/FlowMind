-- Administrative organization assignments used by the expense-report fixture.

INSERT INTO sys_user_org_unit(
    id, user_id, org_unit_id, tenant_id, dimension_id,
    is_primary, position_name, is_leader, status
) VALUES
    ('ORG_ASSIGN_DEPT_HEAD', 'U004', 'O004',
     'default', 'ORG_DIM_ADMIN', 1, '技术中心负责人', 1, 1),
    ('ORG_ASSIGN_FINANCE', 'U005', 'O003',
     'default', 'ORG_DIM_ADMIN', 1, '财务审批人', 0, 1)
ON CONFLICT (tenant_id, user_id, dimension_id, org_unit_id) DO UPDATE SET
    is_primary = EXCLUDED.is_primary,
    position_name = EXCLUDED.position_name,
    is_leader = EXCLUDED.is_leader,
    status = EXCLUDED.status;
