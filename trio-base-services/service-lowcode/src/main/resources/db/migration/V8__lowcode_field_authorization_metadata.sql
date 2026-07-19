-- Field authorization metadata used by enterprise authorization synchronization.

ALTER TABLE lc_form_field_definition
    ADD COLUMN IF NOT EXISTS sensitivity_classification VARCHAR(64),
    ADD COLUMN IF NOT EXISTS default_mask_strategy VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_lc_form_field_definition_sensitivity
    ON lc_form_field_definition(tenant_id, sensitivity_classification)
    WHERE sensitivity_classification IS NOT NULL;
