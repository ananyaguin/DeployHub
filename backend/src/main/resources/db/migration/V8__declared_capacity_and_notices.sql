-- Declared scheduling capacity and in-app notices. CPU and memory are not host measurements.
-- Apply only after V7. Do not edit earlier migrations.

ALTER TABLE worker_nodes
    ADD COLUMN cpu_millis INT NOT NULL DEFAULT 1000,
    ADD COLUMN memory_mb INT NOT NULL DEFAULT 512;

ALTER TABLE executions
    ADD COLUMN cpu_millis INT NOT NULL DEFAULT 100,
    ADD COLUMN memory_mb INT NOT NULL DEFAULT 64;

ALTER TABLE deployment_events
    ADD COLUMN actor_label VARCHAR(160) NULL,
    ADD COLUMN execution_id BIGINT UNSIGNED NULL,
    ADD KEY idx_deployment_events_execution (execution_id),
    ADD CONSTRAINT fk_deployment_events_execution FOREIGN KEY (execution_id) REFERENCES executions (id) ON DELETE SET NULL;

ALTER TABLE notification_deliveries
    ADD COLUMN summary VARCHAR(500) NULL,
    ADD UNIQUE KEY uq_notification_deliveries_user_execution_channel (user_id, execution_id, channel);

ALTER TABLE notification_deliveries DROP CHECK chk_notification_deliveries_channel;

ALTER TABLE notification_deliveries
    ADD CONSTRAINT chk_notification_deliveries_channel CHECK (channel IN ('EMAIL', 'IN_APP'));
