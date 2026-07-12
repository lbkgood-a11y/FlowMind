-- Keep user-maintained equivalent policies and remove duplicated fixed baseline seeds.

DELETE FROM sys_data_policy seed
WHERE seed.id = 'DP_USER_QUERY_ADMIN_ALL'
  AND EXISTS (
      SELECT 1
      FROM sys_data_policy other
      JOIN sys_data_policy_dimension other_dimension ON other_dimension.policy_id = other.id
      WHERE other.id <> seed.id
        AND other.tenant_id = seed.tenant_id
        AND other.subject_type = seed.subject_type
        AND other.subject_id = seed.subject_id
        AND other.resource_code = seed.resource_code
        AND other.action_code = seed.action_code
        AND other.effect = seed.effect
        AND other.combine_mode = seed.combine_mode
        AND other.status = seed.status
        AND other_dimension.dimension_code = 'ADMIN'
        AND other_dimension.scope_type = 'ALL'
        AND COALESCE(other_dimension.org_unit_ids, '') = ''
  );

DELETE FROM sys_data_policy seed
WHERE seed.id = 'DP_USER_QUERY_USER_SELF'
  AND EXISTS (
      SELECT 1
      FROM sys_data_policy other
      JOIN sys_data_policy_dimension other_dimension ON other_dimension.policy_id = other.id
      WHERE other.id <> seed.id
        AND other.tenant_id = seed.tenant_id
        AND other.subject_type = seed.subject_type
        AND other.subject_id = seed.subject_id
        AND other.resource_code = seed.resource_code
        AND other.action_code = seed.action_code
        AND other.effect = seed.effect
        AND other.combine_mode = seed.combine_mode
        AND other.status = seed.status
        AND other_dimension.dimension_code = 'ADMIN'
        AND other_dimension.scope_type = 'SELF'
        AND COALESCE(other_dimension.org_unit_ids, '') = ''
  );
