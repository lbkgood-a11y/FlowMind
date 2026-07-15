-- Backfill form-to-process business references created before legacy package binding support.

UPDATE wf_process_instance process
SET business_type = 'expense_report',
    business_id = form_instance.id,
    launch_mode = COALESCE(process.launch_mode, 'EXISTING_DOCUMENT'),
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
FROM lc_form_instance form_instance
WHERE form_instance.process_instance_id = process.id
  AND form_instance.form_key = 'expense'
  AND process.process_key = 'expense_report'
  AND process.business_id IS NULL;
