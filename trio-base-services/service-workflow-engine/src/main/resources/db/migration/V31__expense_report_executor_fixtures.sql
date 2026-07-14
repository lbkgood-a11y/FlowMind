-- Local expense-report fixture storage for code-registered business executors.
-- This is not the target business service model; it lets the workflow engine
-- run the first closed-loop acceptance flow without opening arbitrary calls.

CREATE TABLE IF NOT EXISTS wf_expense_report_fixture (
    id            VARCHAR(32)  PRIMARY KEY,
    tenant_id     VARCHAR(32)  NOT NULL DEFAULT 'GLOBAL',
    applicant_id  VARCHAR(32),
    amount        NUMERIC(18, 2) NOT NULL,
    reason        VARCHAR(512) NOT NULL,
    dept          VARCHAR(128),
    status        VARCHAR(32)  NOT NULL DEFAULT 'DRAFT'
                  CHECK (status IN ('DRAFT', 'IN_APPROVAL', 'APPROVED', 'REJECTED')),
    trace_id      VARCHAR(64),
    payload_json  TEXT,
    created_by    VARCHAR(32),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(32),
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_wf_expense_fixture_tenant_status
    ON wf_expense_report_fixture(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_wf_expense_fixture_applicant
    ON wf_expense_report_fixture(applicant_id);

CREATE TABLE IF NOT EXISTS wf_expense_report_action_log (
    id              VARCHAR(32)  PRIMARY KEY,
    tenant_id       VARCHAR(32)  NOT NULL DEFAULT 'GLOBAL',
    executor_key    VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    business_id     VARCHAR(32),
    action_code     VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    result_code     VARCHAR(64),
    request_json    TEXT,
    result_json     TEXT,
    last_error      TEXT,
    trace_id        VARCHAR(64),
    created_by      VARCHAR(32),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(32),
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_expense_action_idempotency
    ON wf_expense_report_action_log(executor_key, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_wf_expense_action_business
    ON wf_expense_report_action_log(business_id);

INSERT INTO wf_expense_report_fixture(
    id, tenant_id, applicant_id, amount, reason, dept, status, created_by, updated_by
) VALUES
    ('ER_DRAFT_001', 'GLOBAL', 'user-1', 128.50, '本地差旅交通费', 'sales', 'DRAFT', 'SYSTEM', 'SYSTEM'),
    ('ER_REJECTED_001', 'GLOBAL', 'user-1', 688.00, '补充后重新提交的招待费', 'sales', 'REJECTED', 'SYSTEM', 'SYSTEM')
ON CONFLICT (id) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    applicant_id = EXCLUDED.applicant_id,
    amount = EXCLUDED.amount,
    reason = EXCLUDED.reason,
    dept = EXCLUDED.dept,
    status = EXCLUDED.status,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;
