ALTER TABLE wf_process_package
    ADD COLUMN IF NOT EXISTS business_binding_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS launch_plan_json TEXT,
    ADD COLUMN IF NOT EXISTS permission_plan_json TEXT,
    ADD COLUMN IF NOT EXISTS closure_plan_json TEXT,
    ADD COLUMN IF NOT EXISTS agent_follow_up_plan_json TEXT;
