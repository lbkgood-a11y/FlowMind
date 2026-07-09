-- V6__add_role_status.sql — 角色表增加启停状态
ALTER TABLE sys_role
    ADD COLUMN IF NOT EXISTS status SMALLINT NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_sys_role_status ON sys_role(status);
