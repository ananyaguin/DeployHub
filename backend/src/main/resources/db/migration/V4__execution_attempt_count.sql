-- Bounded retries for a worker that stops mid-task. V1, V2, and V3 stay as they are.

ALTER TABLE executions
    ADD COLUMN attempt_count INT UNSIGNED NOT NULL DEFAULT 0;
