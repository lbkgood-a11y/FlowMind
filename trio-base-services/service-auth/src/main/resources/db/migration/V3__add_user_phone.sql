-- V3__add_user_phone.sql — 用户表增加手机号字段
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_phone ON sys_user(phone) WHERE phone IS NOT NULL;
