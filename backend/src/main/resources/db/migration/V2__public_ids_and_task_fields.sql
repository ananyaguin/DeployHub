-- Public identifiers and the two fields V1 did not store.
-- Apply only after V1. Do not edit V1.

ALTER TABLE tenants
    ADD COLUMN public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD UNIQUE KEY uq_tenants_public_id (public_id);

ALTER TABLE users
    ADD COLUMN public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD COLUMN role VARCHAR(32) NOT NULL,
    ADD UNIQUE KEY uq_users_public_id (public_id),
    ADD CONSTRAINT chk_users_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'));

ALTER TABLE deployments
    ADD COLUMN public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD UNIQUE KEY uq_deployments_public_id (public_id);

ALTER TABLE deployment_versions
    ADD COLUMN public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD UNIQUE KEY uq_deployment_versions_public_id (public_id);

ALTER TABLE executions
    ADD COLUMN public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD COLUMN task_type VARCHAR(32) NOT NULL,
    ADD UNIQUE KEY uq_executions_public_id (public_id),
    ADD CONSTRAINT chk_executions_task_type CHECK (task_type IN ('DEPLOY', 'ROLLBACK', 'STOP'));

ALTER TABLE worker_nodes
    ADD COLUMN public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD UNIQUE KEY uq_worker_nodes_public_id (public_id);
