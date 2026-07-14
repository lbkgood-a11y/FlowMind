-- Standardized business process events for data and AI consumers.
-- Consumers should read this contract table instead of workflow/task internals.

CREATE TABLE IF NOT EXISTS wf_process_business_event (
    id                  VARCHAR(32)  PRIMARY KEY,
    event_key           VARCHAR(160) NOT NULL,
    event_type          VARCHAR(64)  NOT NULL
                        CHECK (event_type IN ('ProcessOutcomeCreated', 'ProcessClosureCreated')),
    tenant_id           VARCHAR(32)  NOT NULL DEFAULT 'GLOBAL',
    process_instance_id VARCHAR(32)  NOT NULL REFERENCES wf_process_instance(id) ON DELETE CASCADE,
    process_outcome_id  VARCHAR(32)  REFERENCES wf_process_outcome(id) ON DELETE CASCADE,
    process_closure_id  VARCHAR(32)  REFERENCES wf_process_closure(id) ON DELETE CASCADE,
    process_key         VARCHAR(128) NOT NULL,
    process_version     INTEGER      NOT NULL,
    business_type       VARCHAR(128),
    business_id         VARCHAR(128),
    outcome_status      VARCHAR(32),
    closure_status      VARCHAR(32),
    payload_json        TEXT         NOT NULL,
    trace_id            VARCHAR(64),
    status              VARCHAR(16)  NOT NULL DEFAULT 'AVAILABLE'
                        CHECK (status IN ('AVAILABLE', 'CONSUMED', 'FAILED')),
    created_by          VARCHAR(32),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(32),
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_process_business_event_key
    ON wf_process_business_event(event_key);
CREATE INDEX IF NOT EXISTS idx_wf_process_business_event_available
    ON wf_process_business_event(status, created_at);
CREATE INDEX IF NOT EXISTS idx_wf_process_business_event_instance
    ON wf_process_business_event(process_instance_id, created_at);
CREATE INDEX IF NOT EXISTS idx_wf_process_business_event_business
    ON wf_process_business_event(business_type, business_id, created_at);
