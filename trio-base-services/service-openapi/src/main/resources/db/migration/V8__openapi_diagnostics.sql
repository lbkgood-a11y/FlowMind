CREATE TABLE oa_execution_diagnostic (
    id                  VARCHAR(32) PRIMARY KEY,
    execution_id        VARCHAR(32) NOT NULL UNIQUE REFERENCES oa_execution(id) ON DELETE CASCADE,
    request_payload     JSONB,
    response_payload    JSONB,
    captured_by         VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_oa_execution_diagnostic_expiry
        CHECK (expires_at <= created_at + INTERVAL '7 days')
);

CREATE INDEX idx_oa_execution_diagnostic_expiry ON oa_execution_diagnostic (expires_at);
