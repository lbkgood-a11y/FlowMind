ALTER TABLE lc_form_instance
    ADD COLUMN IF NOT EXISTS owner_org_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS owner_org_provenance VARCHAR(32) NOT NULL DEFAULT 'UNRESOLVED';

CREATE INDEX IF NOT EXISTS idx_lc_form_instance_tenant_form_owner_org
    ON lc_form_instance(tenant_id, form_key, owner_org_id, submitted_at DESC);

CREATE INDEX IF NOT EXISTS idx_lc_form_instance_unresolved_owner
    ON lc_form_instance(tenant_id, submitted_by, submitted_at)
    WHERE owner_org_id IS NULL OR owner_org_provenance = 'UNRESOLVED';
