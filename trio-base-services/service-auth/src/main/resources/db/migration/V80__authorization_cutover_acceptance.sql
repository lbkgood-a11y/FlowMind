-- Persist the explicit implementation-person acknowledgement separately from
-- the detected expansion evidence. Detection remains auditable; cutover only
-- treats an expansion as unresolved until it has been reviewed explicitly.
ALTER TABLE sys_role_auth_draft
    ADD COLUMN IF NOT EXISTS migration_expansion_acknowledged SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE sys_role_auth_draft
    DROP CONSTRAINT IF EXISTS ck_role_auth_draft_migration_expansion_ack;
ALTER TABLE sys_role_auth_draft
    ADD CONSTRAINT ck_role_auth_draft_migration_expansion_ack
        CHECK (migration_expansion_acknowledged IN (0, 1));

CREATE INDEX IF NOT EXISTS idx_role_auth_draft_cutover
    ON sys_role_auth_draft(tenant_id, migration_expansion_detected,
                           migration_expansion_acknowledged, draft_status);
