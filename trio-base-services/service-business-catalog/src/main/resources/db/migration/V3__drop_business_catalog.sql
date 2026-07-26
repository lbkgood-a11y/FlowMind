-- V3: Remove local business catalog storage in favor of workflow-engine's catalog.
-- Business object metadata now lives exclusively in service-workflow-engine
-- (wf_biz_object* tables), accessible via /api/v1/process-business-objects.
DROP TABLE IF EXISTS bc_business_object;
