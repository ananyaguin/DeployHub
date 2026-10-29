# DeployHub

Deployment and job orchestration. The backend registers and logs in users, then scopes deployment reads and writes to the caller's tenant. A deploy request queues an execution. The worker claims it and runs either the simulator or a local Docker executor. It does not contact a cloud provider. Token rules are in `docs/security.md`.

The product name is CloudFlow. The directory name is DeployHub. This directory is the repository root.

## Layout

| Path | Artifact | Role |
| --- | --- | --- |
| `pom.xml` | `com.cloudflow:cloudflow` | Maven parent, Java 17, Spring Boot 3.5.16 |
| `backend/` | `cloudflow-backend` | HTTP API. |
| `worker-agent/` | `cloudflow-worker-agent` | Polls the API and runs simulated or Docker tasks. |
| `frontend/` | `cloudflow-frontend` | React 18 and Vite. JavaScript and JSX. |

Backend packages under `com.cloudflow` stay separate: `controller`, `service`, `entity`, `repository`, `dto`, `security`, `exception`, `configuration`. The worker lives in `com.cloudflow.worker`.

## Prerequisites

- JDK 17. This machine has it at `/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`. The default `java` is 24, and the build rejects that.
- Maven 3.9 or newer.
- Node.js 20.19+ or 22.12+, with npm. Vite 8 does not support older Node 20 releases.

MySQL 8 and Flyway are configured on the backend. Setup, the schema, and local credentials are in `docs/database.md`. Java stays on 17. Spring Boot stays on 3.5.16.

## Architecture

The API owns the schema, authentication, and the execution queue. The worker polls that API, sends heartbeats, and runs a task. The frontend talks only to the API. Simulator mode records a result and does not contact a cloud provider. Docker mode, when selected on the worker, talks to a local daemon. Tenant isolation is the tenant id on the signed-in user: another tenant's deployment, execution, audit row, or notice is a 404 or an empty page. Worker counts on the operations summary are the shared pool and do not include another tenant's rows.

## Scheduling, audit, and notices

A worker registers a declared CPU (`cpuMillis`) and memory (`memoryMb`). Omitted values default to 1000 CPU milliseconds and 512 MB. Those numbers are the capacity the worker claims it can take. The API does not read the host. A queued execution defaults to 100 CPU milliseconds and 64 MB. Send `cpuMillis` and `memoryMb` on `POST /api/v1/executions` to ask for more, within 1–16000 and 1–65536. The worker process sends its `DOCKER_CPU_MILLIS` and `DOCKER_MEMORY_MB` at registration as that declared capacity. Those Docker settings still cap a container when Docker mode is on. They are not a measurement of the machine.

Claim picks the oldest queued execution that fits the worker's declared capacity. A task larger than the worker's declared capacity is left for another worker. A task that would fit only after current work finishes stays queued, so a newer smaller task does not take its place. An offline worker is rejected. Two claims for the same worker lock that worker before reading capacity, so the same free capacity is not handed out twice. Attempt limits, retries, and the worker token are unchanged.

`GET /api/v1/audit` lists the caller's tenant events, newest page first. Events stay append-only. They can name the acting user email or `worker:<name>`, the deployment, and the execution. They do not include JWTs, worker tokens, or environment values. The dashboard shows this list and in-app notices.

In-app notices use the existing notification deliveries with channel `IN_APP`. One notice is stored for a failed deploy, stop, or rollback, for a successful rollback, and for the first simulated unhealthy result in a streak. The same result submitted again does not add another notice. `GET /api/v1/notification-deliveries` returns only the caller's rows.

`GET /api/v1/operations` returns counts for the caller's tenant: queued, running, succeeded, failed, retried (`attemptCount` greater than 1), rollback succeeded, and rollback failed. `schedulingBlocked` is the number of that tenant's queued executions that fit no online worker's declared capacity, including the case where no worker is online. `workersOnline` and `workersOffline` count the shared pool and do not name workers or include tokens. `capacityDeclared` is true. This endpoint is not `GET /api/v1/health` and it is not `GET /api/v1/deployments/{id}/health`.

`mvn verify` from the repository root runs the backend and worker tests. It does not need MySQL, SMTP, Docker, or a network. The live Docker ping skips unless `CLOUDFLOW_DOCKER_TESTS=true` and `/var/run/docker.sock` is readable. In `frontend/`, `npm ci`, `npm run lint`, and `npm run build` check the UI.

## Run locally

JDK 17, Maven 3.9+, and Node.js 20.19+ or 22.12+ are required. MySQL 8 is required only to boot the API. Tests do not open it.

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
cp .env.example .env
```

Export the backend variables from `.env.example` in the API shell. Replace the placeholder password, JWT secret, and worker token with local values. Do not commit `.env`.

```bash
mvn -pl backend spring-boot:run
```

In a second shell, with the same `WORKER_TOKEN`:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export WORKER_TOKEN=change-me-worker-token
export WORKER_NAME=worker-1
export WORKER_BACKEND_URL=http://localhost:8080
export WORKER_EXECUTOR=simulator
mvn -pl worker-agent spring-boot:run
```

In a third shell:

```bash
cd frontend
npm install
npm run dev
```

The API listens on port 8080. `GET /api/v1/health` returns `{"status":"UP"}` and means the process is up. It does not mean a deployment is healthy. Actuator exposes only `GET /actuator/health`, with component details hidden. Other actuator paths are not public. Set `SPRING_PROFILES_ACTIVE` to `dev` or `prod` when you want that profile. `dev` enables Swagger at `/swagger-ui.html`. The default and `prod` profiles leave Swagger off. The frontend dev server listens on port 5173.

`npm run build` writes `frontend/dist`. `npm run lint` checks `frontend/src`.

The worker has no web server. An empty `WORKER_TOKEN` makes it exit after startup.

## API authentication

| Endpoint | Who |
| --- | --- |
| `GET /api/v1/health`, `GET /actuator/health` | Public. Process liveness only. |
| `POST /api/v1/auth/register`, `login`, `verify-email`, `password-reset`, `password-reset/confirm` | Public. |
| `GET /api/v1/operations`, deployments, executions, audit, notices | Signed-in user. Tenant-scoped, except the shared worker counts on operations. |
| `GET /api/v1/deployments/{id}/health` | Signed-in user. Simulated deployment health for that tenant. |
| `POST /api/v1/worker/**` | `Authorization: Worker <token>`. |
| `/swagger-ui.html`, `/v3/api-docs` | `dev` profile only. |

A missing or invalid user token returns 401 `Authentication required`. Another tenant's resource returns 404 `Resource not found`.

## Flyway

Migrations live in `backend/src/main/resources/db/migration`, versions V1 through V8. Hibernate does not create tables (`ddl-auto: none`). Flyway applies pending versions when the API starts, with `clean` disabled and validation on. Do not edit a migration that has already been applied. The next schema change is a new `V9__` file.

After the API has started against a database, the history table shows the applied versions:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

`success` should be 1 for versions 1 through 8. `mvn verify` does not run this check and does not need MySQL.

## Troubleshooting

- Database connection failure: MySQL must be listening, and `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` must be set. Quote the JDBC URL in zsh so `&` is not treated as a job control character. The Compose stack uses its own database and does not read the host URL.
- Worker token rejected: the API and the worker must use the same non-empty `WORKER_TOKEN`. A mismatch or a blank token returns 401. The worker then exits or retries without claiming work.
- Frontend API errors: `VITE_API_BASE_URL` must be the API the browser can reach, default `http://localhost:8080`. `CORS_ALLOWED_ORIGINS` must include the frontend origin, default `http://localhost:5173`. A 401 means the session token is missing or expired.
- Docker-unavailable tests: `mvn verify` still passes. The live daemon ping runs only when `CLOUDFLOW_DOCKER_TESTS=true` and `/var/run/docker.sock` is readable. Simulator mode does not need a daemon.

## Deployment readiness

These steps were not executed as a cloud deployment. Check them in the environment that will run the software.

- Local tests, no database and no Docker: `mvn verify`, then `npm ci`, `npm run lint`, and `npm run build` in `frontend/`.
- Real database: export the datasource variables and `JWT_SECRET`, start the API, and confirm `flyway_schema_history` versions 1–8. This needs a running MySQL.
- Worker: set a shared `WORKER_TOKEN` and `WORKER_EXECUTOR=simulator`. Confirm the worker stays running and `GET /api/v1/operations` shows `workersOnline` after a heartbeat. This needs the API.
- Docker Compose: install Docker, build with `mvn -DskipTests package` and `VITE_API_BASE_URL=http://localhost:8080 npm run build`, set `JWT_SECRET`, `WORKER_TOKEN`, `COMPOSE_MYSQL_PASSWORD`, and `COMPOSE_MYSQL_ROOT_PASSWORD`, then `docker compose up --build`. This stack was not run here because Docker is not installed. Do not treat Compose as verified until those commands succeed.
- Cloud account or production credentials: not used. This project does not create cloud resources. Do not put real secrets in git.

Git remote `origin` points at GitHub. `.github/workflows/ci.yml` defines CI. A passing local build does not mean the Actions run has succeeded.
