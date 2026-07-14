-- Audited task operations, linked tasks, and repeatable node visits.

ALTER TABLE wf_task
    ADD COLUMN IF NOT EXISTS source_task_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS root_task_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS node_visit_no INTEGER NOT NULL DEFAULT 1;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_wf_task_source') THEN
        ALTER TABLE wf_task
            ADD CONSTRAINT fk_wf_task_source
            FOREIGN KEY (source_task_id) REFERENCES wf_task(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_wf_task_root') THEN
        ALTER TABLE wf_task
            ADD CONSTRAINT fk_wf_task_root
            FOREIGN KEY (root_task_id) REFERENCES wf_task(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_wf_task_source ON wf_task(source_task_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_root ON wf_task(root_task_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_node_visit
    ON wf_task(process_instance_id, node_id, node_visit_no);

ALTER TABLE wf_node_record
    ADD COLUMN IF NOT EXISTS visit_no INTEGER NOT NULL DEFAULT 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_node_record_visit
    ON wf_node_record(process_instance_id, node_id, visit_no);

CREATE TABLE IF NOT EXISTS wf_task_operation (
    id                  VARCHAR(32)  PRIMARY KEY,
    operation_id        VARCHAR(64)  NOT NULL,
    process_instance_id VARCHAR(32)  NOT NULL REFERENCES wf_process_instance(id) ON DELETE CASCADE,
    source_task_id      VARCHAR(32)  REFERENCES wf_task(id) ON DELETE SET NULL,
    target_task_id      VARCHAR(32)  REFERENCES wf_task(id) ON DELETE SET NULL,
    action              VARCHAR(16)  NOT NULL
                        CHECK (action IN ('APPROVE', 'REJECT', 'RETURN', 'TRANSFER', 'ADD_SIGN')),
    operator_id         VARCHAR(32)  NOT NULL,
    operator_name       VARCHAR(64),
    target_user_id      VARCHAR(32),
    target_user_name    VARCHAR(64),
    target_node_id      VARCHAR(64),
    comment             TEXT,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACCEPTED'
                        CHECK (status IN ('ACCEPTED', 'COMPLETED', 'FAILED')),
    trace_id            VARCHAR(64),
    result_json         TEXT,
    created_by          VARCHAR(32),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(32),
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_task_operation_id
    ON wf_task_operation(operation_id);

CREATE INDEX IF NOT EXISTS idx_wf_task_operation_instance
    ON wf_task_operation(process_instance_id, created_at);

CREATE INDEX IF NOT EXISTS idx_wf_task_operation_source
    ON wf_task_operation(source_task_id);
