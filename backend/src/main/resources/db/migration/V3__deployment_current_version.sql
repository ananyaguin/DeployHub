-- Current version number for rollback. No foreign key: deployment_versions already
-- references deployments, and a reverse key would block ON DELETE CASCADE.

ALTER TABLE deployments
    ADD COLUMN current_version_number INT UNSIGNED NULL;
