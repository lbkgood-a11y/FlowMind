-- Administrative organization assignments used by the expense-report fixture.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM sys_org_unit WHERE id = '01HK153X130000000000000013')
       AND EXISTS (SELECT 1 FROM sys_org_unit WHERE id = '01HK153X120000000000000012') THEN
        INSERT INTO sys_user_org_unit(
            id, user_id, org_unit_id, tenant_id, dimension_id,
            is_primary, position_name, is_leader, status
        ) VALUES
            ('ORG_ASSIGN_DEPT_HEAD', 'U004', '01HK153X130000000000000013',
             'default', 'ORG_DIM_ADMIN', 1, '技术中心负责人', 1, 1),
            ('ORG_ASSIGN_FINANCE', 'U005', '01HK153X120000000000000012',
             'default', 'ORG_DIM_ADMIN', 1, '财务审批人', 0, 1)
        ON CONFLICT (tenant_id, user_id, dimension_id, org_unit_id) DO UPDATE SET
            is_primary = EXCLUDED.is_primary,
            position_name = EXCLUDED.position_name,
            is_leader = EXCLUDED.is_leader,
            status = EXCLUDED.status;
    END IF;
END $$;
