-- Process package versioning and published form snapshot metadata.

DROP INDEX IF EXISTS uk_wf_package_key;

ALTER TABLE wf_process_package
    ADD COLUMN IF NOT EXISTS form_definition_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS form_definition_version INTEGER,
    ADD COLUMN IF NOT EXISTS source_package_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_wf_package_source'
    ) THEN
        ALTER TABLE wf_process_package
            ADD CONSTRAINT fk_wf_package_source
            FOREIGN KEY (source_package_id)
            REFERENCES wf_process_package(id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_package_key_version
    ON wf_process_package(process_key, version);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_package_single_draft
    ON wf_process_package(process_key)
    WHERE status = 'DRAFT';

CREATE INDEX IF NOT EXISTS idx_wf_package_form_definition
    ON wf_process_package(form_definition_id);

UPDATE wf_process_package
SET published_at = COALESCE(published_at, updated_at, created_at)
WHERE status IN ('PUBLISHED', 'OFFLINE');
