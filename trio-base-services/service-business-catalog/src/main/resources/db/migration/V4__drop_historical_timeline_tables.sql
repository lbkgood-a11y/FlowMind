-- V4: Remove read-only historical timeline projection tables.
-- Timeline events are now pushed directly into bc_document_timeline_event
-- by each service's audit sink, eliminating cross-service database reads.
DROP TABLE IF EXISTS act_action_event;
DROP TABLE IF EXISTS act_action_execution;
DROP TABLE IF EXISTS act_document_timeline_event;
