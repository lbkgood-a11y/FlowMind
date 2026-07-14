ALTER TABLE wf_process_instance
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) DEFAULT 'GLOBAL',
    ADD COLUMN IF NOT EXISTS business_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS business_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS launch_mode VARCHAR(32),
    ADD COLUMN IF NOT EXISTS launch_idempotency_key VARCHAR(256);

CREATE INDEX IF NOT EXISTS idx_wf_instance_business_ref
    ON wf_process_instance(business_type, business_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_instance_launch_idempotency
    ON wf_process_instance(process_package_id, launch_idempotency_key)
    WHERE launch_idempotency_key IS NOT NULL;
