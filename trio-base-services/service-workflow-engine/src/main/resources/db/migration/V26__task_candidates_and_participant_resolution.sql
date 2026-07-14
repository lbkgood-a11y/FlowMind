-- Immutable participant snapshots and candidate-backed approval tasks.

CREATE TABLE IF NOT EXISTS wf_participant_resolution (
    id                  VARCHAR(32)  PRIMARY KEY,
    resolution_key      VARCHAR(256) NOT NULL,
    process_instance_id VARCHAR(32)  NOT NULL REFERENCES wf_process_instance(id) ON DELETE CASCADE,
    node_id             VARCHAR(64)  NOT NULL,
    assignment_type     VARCHAR(16)  NOT NULL
                        CHECK (assignment_type IN ('ROLE', 'DEPT', 'USER')),
    assignment_ref      VARCHAR(128) NOT NULL,
    participant_version VARCHAR(64)  NOT NULL,
    participants_json   TEXT         NOT NULL,
    created_by          VARCHAR(32),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(32),
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_participant_resolution_key
    ON wf_participant_resolution(resolution_key);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_participant_resolution_version
    ON wf_participant_resolution(process_instance_id, node_id, participant_version);

CREATE TABLE IF NOT EXISTS wf_task_candidate (
    id              VARCHAR(32)  PRIMARY KEY,
    task_id         VARCHAR(32)  NOT NULL REFERENCES wf_task(id) ON DELETE CASCADE,
    user_id         VARCHAR(32)  NOT NULL,
    username        VARCHAR(64),
    source_type     VARCHAR(16)  NOT NULL
                    CHECK (source_type IN ('ROLE', 'DEPT', 'USER')),
    source_ref      VARCHAR(128) NOT NULL,
    created_by      VARCHAR(32),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(32),
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_task_candidate_user
    ON wf_task_candidate(task_id, user_id);

CREATE INDEX IF NOT EXISTS idx_wf_task_candidate_user
    ON wf_task_candidate(user_id, task_id);
