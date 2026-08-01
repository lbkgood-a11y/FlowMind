-- Remove the built-in expense and leave demo applications and their runtime data.
-- Authorization catalog records are removed by service-auth V95.

DELETE FROM lc_form_instance
WHERE form_key IN ('expense', 'leave')
   OR form_definition_id IN ('LC_FORM_EXPENSE_001', 'LC_FORM_LEAVE_001');

DELETE FROM lc_form_relation
WHERE parent_form_definition_id IN ('LC_FORM_EXPENSE_001', 'LC_FORM_LEAVE_001')
   OR child_form_definition_id IN ('LC_FORM_EXPENSE_001', 'LC_FORM_LEAVE_001')
   OR application_version_id IN ('LC_APPV_EXPENSE_REPORT_001', 'LC_APPV_LEAVE_001');

DELETE FROM lc_application
WHERE id IN ('LC_APP_EXPENSE_REPORT', 'LC_APP_LEAVE')
   OR app_key IN ('expense_report', 'leave');

DELETE FROM lc_form_definition
WHERE id IN ('LC_FORM_EXPENSE_001', 'LC_FORM_LEAVE_001')
   OR form_key IN ('expense', 'leave');
