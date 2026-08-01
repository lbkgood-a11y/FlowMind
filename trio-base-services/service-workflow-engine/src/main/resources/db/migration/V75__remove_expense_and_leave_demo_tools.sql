-- Retire the built-in expense/leave workflow examples and their registered tools.
-- User-created rapid-development applications are not matched by these stable seed IDs.

DELETE FROM wf_expense_report_action_log;
DELETE FROM wf_expense_report_fixture;

DELETE FROM wf_process_package package
WHERE (package.id IN ('PKG001', 'PKG_LEAVE_001')
       OR package.process_key IN ('expense_report', 'leave_request'))
  AND NOT EXISTS (
      SELECT 1
      FROM wf_process_instance instance
      WHERE instance.process_package_id = package.id
         OR instance.process_key = package.process_key
  );

DELETE FROM wf_biz_object object
WHERE (object.id = 'BIZ_EXPENSE_REPORT' OR object.type_code = 'expense_report')
  AND NOT EXISTS (
      SELECT 1
      FROM wf_process_instance instance
      WHERE instance.business_type = object.type_code
  );
