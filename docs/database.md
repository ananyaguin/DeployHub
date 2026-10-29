# Database

The backend uses MySQL 8 and Flyway. Java is 17. Spring Boot is 3.5.16, which manages Connector/J 9.7.0, Flyway 11.7.2, and Hibernate 6.6.53. Hibernate `ddl-auto` is `none`. Flyway owns the schema. The worker does not.

No passwords belong in source, profiles, or this document.

## This machine

Checked on 2026-10-24:

| Install | Version | Result |
| --- | --- | --- |
| `/usr/local/mysql` | 9.3.0 | Answers on port 3306. `root` with no password is rejected. |
| Homebrew `mysql` | 26.7.0 | Installed. It does not hold port 3306. |
| Homebrew `mysql@8.0` and `mysql@8.4` | — | Not installed. |
| Docker | — | Not installed. |

Migrations were not applied. There is no MySQL 8 server here, and no datasource password was available. Do not point CloudFlow at the 9.3.0 server and call that MySQL 8.

## Install MySQL 8 locally

Homebrew can install MySQL 8.4 without Docker. On this Mac, port 3306 is already taken, so use 3307.

```bash
brew install mysql@8.4
```

Create a config file, for example `~/cloudflow-mysql8.cnf`:

```ini
[mysqld]
port=3307
mysqlx=0
socket=/tmp/cloudflow-mysql8.sock
datadir=/opt/homebrew/var/mysql@8.4
```

Start it:

```bash
$(brew --prefix mysql@8.4)/bin/mysqld --defaults-file=$HOME/cloudflow-mysql8.cnf --bind-address=127.0.0.1
```

A machine where port 3306 is free can use the formula's normal service and port 3306 instead.

## Database and user

Run the following as an administrator against the MySQL 8 instance. Replace the password locally. Do not commit it.

```sql
CREATE DATABASE cloudflow CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'cloudflow'@'127.0.0.1' IDENTIFIED BY 'choose-a-local-password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON cloudflow.* TO 'cloudflow'@'127.0.0.1';
```

The account can change the `cloudflow` schema and its rows. It is not a global administrator. It does not have `DROP`.

## Environment

Spring Boot reads the process environment. It does not load `.env` by itself. From the repository root:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3307/cloudflow?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
export SPRING_DATASOURCE_USERNAME=cloudflow
export SPRING_DATASOURCE_PASSWORD='choose-a-local-password'
```

Use port 3306 in the URL when that is the MySQL 8 port. Keep the password in `SPRING_DATASOURCE_PASSWORD`, not in the URL. `.env.example` lists the same names with placeholders. `.env` is gitignored.

Production should use a different user and password, supplied by the process environment or a secret store. `application-prod.yml` only changes log levels.

## Run migrations

Flyway runs when the backend starts. `clean` is disabled. Validation is on. There is no baseline that hides an existing schema.

```bash
mvn -pl backend spring-boot:run
```

A successful start logs Flyway applying `1` or validating it. Then:

```bash
curl -s http://127.0.0.1:8080/api/v1/health
```

## Check status

As the `cloudflow` user:

```sql
SELECT installed_rank, version, description, success, checksum
FROM flyway_schema_history;
```

`success` must be `1` for version `1`. If the backend is already running, its log is the other check. Do not turn validation off to hide a checksum mismatch.

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| Connection refused | The MySQL 8 process is down, or the URL port does not match it. Port 3306 on this Mac is the other server. |
| Access denied | User, host (`127.0.0.1`), or password does not match. The password is the environment variable, not a value from Git. |
| Unknown database | `CREATE DATABASE cloudflow` has not been run on that server. |
| Migration failed | Read the Flyway error. Add a new versioned migration for a later correction. Do not edit `V1__initial_schema.sql` after it has been applied. |
| Checksum mismatch | A migration file changed after it was applied. Restore the file. Do not use repair to bless an edited applied migration. |
| Placeholder could not be resolved | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, or `SPRING_DATASOURCE_PASSWORD` is missing from the environment. |

The backend log can include the JDBC URL. It must not include the password. Health responses do not include datasource settings.

## Reset a disposable development database

This deletes the local `cloudflow` database. Do this only on a development server you can throw away. Do not run it in production.

As an administrator:

```sql
DROP DATABASE cloudflow;
CREATE DATABASE cloudflow CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

Start the backend again. Flyway applies `V1` to the empty database. The application user still has no `DROP` grant, which is why an administrator runs the reset.

## Schema

`V1__initial_schema.sql` creates twelve tables. Keys are `BIGINT UNSIGNED`. Timestamps are `DATETIME(6)`. The JDBC session time zone is UTC.

`V2__public_ids_and_task_fields.sql` adds a `CHAR(36)` `public_id` on tenants, users, deployments, deployment versions, executions, and worker nodes. It also adds `users.role` and `executions.task_type`. Internal keys stay numeric. V1 was not edited.

`V3__deployment_current_version.sql` adds nullable `deployments.current_version_number`. It is not a foreign key, because `deployment_versions` already references `deployments`.

`V4__execution_attempt_count.sql` adds `executions.attempt_count`. It counts claims, not a new execution row.

`V5__version_image_and_container.sql` adds nullable `deployment_versions.image_ref`, `env_json`, and `container_id`. `env_json` holds environment values for the worker. User APIs do not return it. V1 through V4 were not edited.

`V6__one_open_execution.sql` adds a virtual column so one deployment has at most one `QUEUED` or `RUNNING` execution.

`V7__deployment_health.sql` adds simulated health columns on `deployments` and allows task type `HEALTH`.

`V8__declared_capacity_and_notices.sql` adds declared `cpu_millis` and `memory_mb` on workers and executions, actor and execution columns on events, and in-app notification support. Those CPU and memory values are declared, not measured on the host.

Flyway applies a pending file when the API starts. Confirm the database with:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

`success` should be 1 for every applied version. Do not edit a file that is already in that table. The next change is a new version, not an edit of V1–V8.

| Table | Owned by | Notes |
| --- | --- | --- |
| `tenants` | — | `slug` is unique. |
| `users` | tenant, cascade delete | One tenant per user. Email is unique inside the tenant. `password_hash` is required. Role is `OWNER`, `ADMIN`, or `MEMBER`. |
| `email_verification_tokens` | user, cascade delete | `token_hash` is unique SHA-256 hex. `used_at` null means unused. |
| `password_reset_tokens` | user, cascade delete | Same hash and expiry rules. |
| `worker_nodes` | global | No tenant. Status `ONLINE` or `OFFLINE`. V8 adds declared `cpu_millis` and `memory_mb`. |
| `deployments` | tenant, cascade delete | Slug is unique per tenant. Status `DRAFT`, `ACTIVE`, or `ARCHIVED`. `current_version_number` is the live version after V3. V7 adds simulated health columns. |
| `deployment_versions` | deployment, cascade delete | `version_number` is unique per deployment. V5 adds nullable `image_ref`, `env_json`, and `container_id`. |
| `deployment_events` | deployment, cascade delete | Optional version. Version delete also removes those events. V8 adds `actor_label` and a nullable execution id. |
| `executions` | deployment and version, cascade delete | Worker delete sets `worker_node_id` null. Status `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, or `CANCELLED`. Task type `DEPLOY`, `ROLLBACK`, `STOP`, or `HEALTH`. `attempt_count` is added in V4. V8 adds declared `cpu_millis` and `memory_mb`. |
| `execution_logs` | execution, cascade delete | Unique `(execution_id, sequence_no)` is the read index. |
| `notification_preferences` | user, cascade delete | One row per user. `email_enabled` only. |
| `notification_deliveries` | user, cascade delete | Optional execution; execution delete sets that id null. Channel `EMAIL` or `IN_APP`. Status `PENDING`, `SENT`, or `FAILED`. V8 adds `summary`. |

Assumptions kept small on purpose:

- Accounts are local password accounts. There is no external-identity table.
- A person who works in two tenants has two user rows.
- A version may store one image reference and environment JSON. Schedules are not columns.
- Notification preferences cover email only. In-app notices are delivery rows, not a second preference.
- A delivery may exist before it is tied to an execution.
- Worker capacity is declared on the worker row. It is not a reading from the host.
