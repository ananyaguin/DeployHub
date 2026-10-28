-- One QUEUED or RUNNING execution per deployment. Closed rows store NULL and do not collide.
-- Multiple NULLs are allowed by the unique index. Apply fails if a deployment already has two open rows.

ALTER TABLE executions
    ADD COLUMN open_deployment_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (
            CASE
                WHEN status IN ('QUEUED', 'RUNNING') THEN deployment_id
                ELSE NULL
            END
        ) VIRTUAL,
    ADD UNIQUE KEY uq_executions_one_open_deployment (open_deployment_id);
