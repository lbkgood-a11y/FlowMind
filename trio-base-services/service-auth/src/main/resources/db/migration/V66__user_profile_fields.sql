ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS real_name VARCHAR(64),
    ADD COLUMN IF NOT EXISTS avatar VARCHAR(512),
    ADD COLUMN IF NOT EXISTS introduction VARCHAR(512);

UPDATE sys_user
SET real_name = username
WHERE real_name IS NULL;
