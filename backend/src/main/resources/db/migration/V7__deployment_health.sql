-- Simulated deployment health. This is not the API liveness check.
-- Apply only after V6. Do not edit earlier migrations.

ALTER TABLE deployments
    ADD COLUMN health_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN health_checked_at DATETIME(6) NULL,
    ADD COLUMN health_detail VARCHAR(500) NULL,
    ADD COLUMN health_failure_streak INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_deployments_health_status CHECK (health_status IN ('UNKNOWN', 'HEALTHY', 'UNHEALTHY'));

ALTER TABLE executions DROP CHECK chk_executions_task_type;

ALTER TABLE executions
    ADD CONSTRAINT chk_executions_task_type CHECK (task_type IN ('DEPLOY', 'ROLLBACK', 'STOP', 'HEALTH'));
