-- Workflow status audit for lowcode form instances.

CREATE TABLE IF NOT EXISTS lc_form_instance_workflow_audit (
    id                       VARCHAR(32) PRIMARY KEY,
    tenant_id                VARCHAR(32) NOT NULL,
    form_instance_id         VARCHAR(32) NOT NULL REFERENCES lc_form_instance(id) ON DELETE CASCADE,
    form_key                 VARCHAR(128) NOT NULL,
    process_key              VARCHAR(128),
    process_instance_id      VARCHAR(128),
    previous_workflow_status VARCHAR(32),
    workflow_status          VARCHAR(32) NOT NULL,
    change_type              VARCHAR(32) NOT NULL,
    trace_id                 VARCHAR(128),
    created_by               VARCHAR(64),
    created_at               TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by               VARCHAR(64),
    updated_at               TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_lc_form_instance_workflow_audit_instance
    ON lc_form_instance_workflow_audit(tenant_id, form_instance_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_lc_form_instance_workflow_audit_process
    ON lc_form_instance_workflow_audit(tenant_id, process_instance_id, created_at DESC)
    WHERE process_instance_id IS NOT NULL;
