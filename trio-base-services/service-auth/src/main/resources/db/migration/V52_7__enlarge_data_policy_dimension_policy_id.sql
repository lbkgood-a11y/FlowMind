-- Enlarge sys_data_policy_dimension.policy_id from VARCHAR(32) to VARCHAR(64)
-- before V53 references longer policy IDs like DP_EXPENSE_RUNTIME_QUERY_ADMIN_ALL.
ALTER TABLE sys_data_policy_dimension ALTER COLUMN policy_id TYPE VARCHAR(64);
