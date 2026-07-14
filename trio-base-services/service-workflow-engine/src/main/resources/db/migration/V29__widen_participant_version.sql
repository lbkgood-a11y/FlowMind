-- participant_version stores "visit-N:{SHA-256}" which exceeds VARCHAR(64).
ALTER TABLE wf_participant_resolution ALTER COLUMN participant_version TYPE VARCHAR(128);
