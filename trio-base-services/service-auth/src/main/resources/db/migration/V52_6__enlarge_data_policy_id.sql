-- Enlarge sys_data_policy.id from VARCHAR(32) to VARCHAR(64)
-- before V53 inserts longer prefixed IDs (>32 chars).
ALTER TABLE sys_data_policy ALTER COLUMN id TYPE VARCHAR(64);
