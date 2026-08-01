-- Remove authorization metadata for the retired expense and leave demo forms.

DELETE FROM sys_role_auth_intent
WHERE capability_id IN (
    SELECT id FROM sys_auth_page_capability WHERE menu_id IN ('M032', 'M037')
);

DELETE FROM sys_auth_page_capability_dependency
WHERE capability_id IN (
        SELECT id FROM sys_auth_page_capability WHERE menu_id IN ('M032', 'M037')
    )
   OR required_capability_id IN (
        SELECT id FROM sys_auth_page_capability WHERE menu_id IN ('M032', 'M037')
    );

DELETE FROM sys_auth_page_capability_target
WHERE capability_id IN (
    SELECT id FROM sys_auth_page_capability WHERE menu_id IN ('M032', 'M037')
);

DELETE FROM sys_auth_page_capability
WHERE menu_id IN ('M032', 'M037');

DELETE FROM sys_menu
WHERE parent_id IN ('M032', 'M037');

DELETE FROM sys_menu
WHERE id IN ('M032', 'M037')
   OR menu_key IN ('LowcodeExpense', 'LowcodeLeave')
   OR path IN ('/lowcode/expense', '/lowcode/apps/leave');

DELETE FROM wf_process_package
WHERE id = 'PKG_LEAVE_001'
  AND process_key = 'leave_request'
  AND NOT EXISTS (
      SELECT 1
      FROM wf_process_instance
      WHERE process_package_id = wf_process_package.id
  );

DELETE FROM sys_auth_field_policy
WHERE resource_code IN (
    'LOWCODE_APP:EXPENSE_REPORT',
    'LOWCODE_FORM:EXPENSE',
    'LOWCODE_APP:LEAVE',
    'LOWCODE_FORM:LEAVE'
);

DELETE FROM sys_auth_grant
WHERE resource_code IN (
    'LOWCODE_APP:EXPENSE_REPORT',
    'LOWCODE_FORM:EXPENSE',
    'LOWCODE_APP:LEAVE',
    'LOWCODE_FORM:LEAVE'
);

DELETE FROM sys_data_policy
WHERE resource_code IN (
    'LOWCODE_APP:EXPENSE_REPORT',
    'LOWCODE_FORM:EXPENSE',
    'LOWCODE_APP:LEAVE',
    'LOWCODE_FORM:LEAVE'
);

DELETE FROM sys_auth_action
WHERE resource_code IN (
    'LOWCODE_APP:EXPENSE_REPORT',
    'LOWCODE_FORM:EXPENSE',
    'LOWCODE_APP:LEAVE',
    'LOWCODE_FORM:LEAVE'
);

DELETE FROM sys_auth_field
WHERE resource_code IN (
    'LOWCODE_APP:EXPENSE_REPORT',
    'LOWCODE_FORM:EXPENSE',
    'LOWCODE_APP:LEAVE',
    'LOWCODE_FORM:LEAVE'
);

DELETE FROM sys_auth_page_capability_target
WHERE resource_code IN (
    'LOWCODE_APP:EXPENSE_REPORT',
    'LOWCODE_FORM:EXPENSE',
    'LOWCODE_APP:LEAVE',
    'LOWCODE_FORM:LEAVE'
);

DELETE FROM sys_role_auth_compiled_evidence
WHERE resource_code IN (
    'LOWCODE_APP:EXPENSE_REPORT',
    'LOWCODE_FORM:EXPENSE',
    'LOWCODE_APP:LEAVE',
    'LOWCODE_FORM:LEAVE'
);

DELETE FROM sys_auth_resource
WHERE resource_code IN (
    'LOWCODE_APP:EXPENSE_REPORT',
    'LOWCODE_FORM:EXPENSE',
    'LOWCODE_APP:LEAVE',
    'LOWCODE_FORM:LEAVE'
)
   OR business_object_id IN (
       'LC_APPV_EXPENSE_REPORT_001',
       'LC_FORM_EXPENSE_001',
       'LC_APPV_LEAVE_001',
       'LC_FORM_LEAVE_001'
   );
