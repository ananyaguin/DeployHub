# Setup

## Java

Maven must run on JDK 17. `java.version` is 17, and the enforcer rejects every other major version.

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
mvn verify
```

`mvn -version` should report Java 17.0.17 or another 17 release. A build on JDK 24 fails in `enforce-java-17`.

Run the API:

```bash
mvn -pl backend spring-boot:run
```

Health check: `curl -s http://localhost:8080/api/v1/health`

Deployment writes need a signed-in user. A deploy, rollback, or stop queues an execution. The API does not run the task.

Run the worker in another shell:

```bash
export WORKER_TOKEN=change-me-worker-token
export WORKER_NAME=worker-1
export WORKER_BACKEND_URL=http://localhost:8080
mvn -pl worker-agent spring-boot:run
```

`WORKER_TOKEN` must match the backend. An empty token makes this process exit after startup. With a token, the process registers, sends a heartbeat, and claims queued work. Heartbeat and retry settings are `WORKER_HEARTBEAT_TIMEOUT`, `WORKER_RUNNING_TIMEOUT`, and `WORKER_MAX_ATTEMPTS`.

## Docker execution

Install Docker and leave the daemon running before selecting Docker mode. This repository does not install Docker and does not change the daemon configuration. Daemon access can control the host. Do not expose the socket to the frontend or to API callers.

Simulation is the default. Leave `WORKER_EXECUTOR` unset, or set `WORKER_EXECUTOR=simulator`. That mode does not open a Docker socket.

Docker mode, in the worker shell only:

```bash
export WORKER_EXECUTOR=docker
export DOCKER_HOST=unix:///var/run/docker.sock
export DOCKER_MEMORY_MB=256
export DOCKER_CPU_MILLIS=500
mvn -pl worker-agent spring-boot:run
```

`DOCKER_HOST` must start with `unix://` or `tcp://`. Memory must be 32–512 MB. CPU must be 100–1000 millis (1000 is one CPU). A version used for deploy or rollback needs an image such as `nginx:1.27`. A missing tag, an uppercase name, a registry port, `PATH`, and host paths are rejected. If the daemon is down, the task fails and is not simulated.

List containers this worker created:

```bash
docker ps -a --filter label=com.cloudflow.managed=true
```

Remove one by the id from that list: `docker rm -f <container-id>`. Do not remove containers that lack the `com.cloudflow.managed=true` label.

When Docker mode reports `Docker is unavailable.`, check that the daemon is running and that `DOCKER_HOST` points at it. `Docker could not complete the task.` means the daemon answered and the operation failed, for example a pull of a missing public image. There is no registry login in this stage.

## Frontend

From the repository root:

```bash
cp .env.example .env
cd frontend
npm install
npm run dev
npm run build
npm run lint
```

Vite loads environment files from the repository root (`envDir` is `..`). The only frontend variable is `VITE_API_BASE_URL`. Sign-in, registration, email verification, password reset, and deployment screens use that API. Verification links use `/verify-email` and reset links use `/reset-password`. The browser does not receive worker or Docker credentials.

## Email

Copy the SMTP placeholders from `.env.example` and export them in the shell that starts the backend. Leave `SMTP_HOST` empty to boot without a mail server. Verification and reset links are built from `MAIL_VERIFICATION_URL` and `MAIL_RESET_URL`.

Local catcher, from the repository root, if Docker is installed:

```bash
docker run --rm -p 1025:1025 -p 8025:8025 mailhog/mailhog
```

Then export `SMTP_HOST=127.0.0.1`, `SMTP_PORT=1025`, an empty `SMTP_USERNAME`, and `SMTP_FROM=no-reply@localhost`. Open `http://127.0.0.1:8025` to read the message. The API tests do not use this. They mock `JavaMailSender`.

Production should set a real host, port 587 with STARTTLS, a mailbox user and password, and HTTPS frontend URLs. Do not commit those values. Rate-limit the public verification and reset endpoints at the reverse proxy, as described in `docs/security.md`.

## Environment

Copy `.env.example` to `.env`. Real values stay in `.env` or the shell. `.env` is gitignored. Do not commit JWT secrets, database passwords, SMTP passwords, or worker tokens.

The API does not start unless these are set. `application.yml` has no defaults for them:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET` (at least 32 bytes)

`WORKER_TOKEN` defaults to empty, which rejects every worker. Set the same value for the API and the worker. The worker reads `WORKER_BACKEND_URL` (default `http://localhost:8080`), `WORKER_NAME`, `WORKER_EXECUTOR` (default `simulator`), and the Docker limit variables.

The frontend reads one variable, `VITE_API_BASE_URL`, when Vite builds or serves the app. The fallback in code is `http://localhost:8080`.

Profiles stay separate. Leave `SPRING_PROFILES_ACTIVE` unset for the default log level and Swagger off. `dev` turns Swagger on and logs `com.cloudflow` at debug. `prod` only quiets logs. Neither profile changes the schema. Flyway still owns the tables.

Local processes, from the repository root, with JDK 17 and those variables exported:

```bash
mvn -pl backend spring-boot:run
```

```bash
mvn -pl worker-agent spring-boot:run
```

```bash
cd frontend
npm install
npm run dev
```

Database setup is in `docs/database.md`. Hibernate does not create tables. Flyway does, from `backend/src/main/resources/db/migration`.

## GitHub Actions

`.github/workflows/ci.yml` runs on every push and pull request to `main`.

- The backend job installs Temurin 17, caches Maven dependencies, and runs `mvn verify` from the repository root. That verifies `backend` and `worker-agent`. The test configuration disables the datasource, JPA, and Flyway, so this job does not need MySQL, SMTP, Docker, or repository secrets.
- The frontend job installs Node.js 22, caches from `frontend/package-lock.json`, then runs `npm ci`, `npm run lint`, and `npm run build` in `frontend/`.

A workflow file in the repository is not a passing Actions run. The Actions tab is green only after GitHub has executed it.

## Compose

`docker-compose.yml` starts MySQL 8.0, the API, the worker, and the frontend. MySQL is a new database named `cloudflow` on the Compose network. Port 3306 is not published, and the JDBC host inside Compose is `mysql`, not `127.0.0.1`. That keeps the host MySQL database, including an existing `deployhub` schema, out of this stack. `docker compose down` leaves the Compose volume in place. `docker compose down -v` deletes that volume only.

The worker uses `WORKER_BACKEND_URL=http://backend:8080`, the same `WORKER_TOKEN` as the API, and `WORKER_EXECUTOR=simulator`. The Docker socket is not mounted. This stack does not run worker Docker mode and does not deploy to a cloud.

Build the jars and the frontend first. The browser calls the published API port, so the frontend build must use `http://localhost:8080`:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
mvn -DskipTests package
cd frontend
VITE_API_BASE_URL=http://localhost:8080 npm run build
```

Then, from the repository root, with `JWT_SECRET`, `WORKER_TOKEN`, `COMPOSE_MYSQL_PASSWORD`, and `COMPOSE_MYSQL_ROOT_PASSWORD` set:

```bash
docker compose up --build
curl -s http://127.0.0.1:8080/api/v1/health
docker compose down
```

The API image is the backend jar on Java 17. Curl is installed there so Compose can call `GET /api/v1/health`. The frontend image is nginx serving `frontend/dist`, and `/verify-email` and `/reset-password` fall back to `index.html`. Compose was not run in the environment where these files were added, because Docker was not installed. Do not treat the stack as verified until those commands succeed.
