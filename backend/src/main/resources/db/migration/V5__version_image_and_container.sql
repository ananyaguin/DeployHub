-- Image and container identity for a Docker-backed version. Earlier migrations stay unchanged.
-- Environment values are JSON text. They are not returned on user-facing APIs.

ALTER TABLE deployment_versions
    ADD COLUMN image_ref VARCHAR(255) NULL,
    ADD COLUMN env_json TEXT NULL,
    ADD COLUMN container_id VARCHAR(64) NULL;
