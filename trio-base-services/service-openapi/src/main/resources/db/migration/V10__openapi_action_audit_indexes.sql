CREATE INDEX IF NOT EXISTS idx_oa_audit_action_trace
    ON oa_audit_event (tenant_id, action_trace_id, created_at DESC)
    WHERE action_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_oa_audit_action_correlation
    ON oa_audit_event (tenant_id, action_correlation_id, created_at DESC)
    WHERE action_correlation_id IS NOT NULL;
