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

`DockerDaemonTest` pings a live daemon only when `CLOUDFLOW_DOCKER_TESTS=true` and `/var/run/docker.sock` is readable. Routine `mvn verify` and GitHub Actions do not set that variable. A missing socket is covered by a test that does not need a daemon. GitHub-hosted runners have a socket, so socket presence alone is not the switch.

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

Registration creates the account only after the verification message is accepted by SMTP. An empty `SMTP_HOST` leaves the mail sender unset, `POST /api/v1/auth/register` returns 503 `Email could not be sent`, and the account is not stored. The same failure is returned when the server rejects the message. The response does not include the token or the SMTP error.

Export these in the backend shell. Placeholders are in `.env.example`. Do not commit real values.

| Variable | Local example | Required to send |
| --- | --- | --- |
| `SMTP_HOST` | `127.0.0.1` | Yes. Empty disables sending. |
| `SMTP_PORT` | `1025` | Yes when a host is set. Default in the file is `587`. |
| `SMTP_USERNAME` | empty | Only when the server requires auth. |
| `SMTP_PASSWORD` | empty | Only when the server requires auth. |
| `SMTP_FROM` | `no-reply@localhost` | Yes. |
| `MAIL_VERIFICATION_URL` | `http://localhost:5173/verify-email` | Yes. This is the page that reads `token` and posts it. |
| `MAIL_VERIFICATION_TTL` | `24h` | Yes. A positive duration. |
| `MAIL_RESET_URL` | `http://localhost:5173/reset-password` | Yes for password reset. |
| `MAIL_RESET_TTL` | `30m` | Yes for password reset. |

The message body is `MAIL_VERIFICATION_URL` plus `?token=` and the raw token. The frontend route is `/verify-email`. It posts `{ "token": "<value>" }` to `POST /api/v1/auth/verify-email` with no session. A valid unused token returns 204 and sets `email_verified_at`. The same token, an unknown token, or an expired token returns 400. Login before that timestamp returns 403 `Email verification required`.

The page is served by the frontend. `MAIL_VERIFICATION_URL` must be an origin the browser can open, and that frontend's `VITE_API_BASE_URL` must reach the API. Compose sets `SMTP_HOST` to empty, so the Compose stack does not send confirmation mail until those SMTP variables are supplied to the backend service.

Local catcher, from the repository root, if Docker is installed:

```bash
docker run --rm -p 1025:1025 -p 8025:8025 mailhog/mailhog
```

Then export `SMTP_HOST=127.0.0.1`, `SMTP_PORT=1025`, an empty `SMTP_USERNAME`, and `SMTP_FROM=no-reply@localhost`. Open `http://127.0.0.1:8025` to read the message. The API tests do not use this. They mock `JavaMailSender`.

A provider such as Gmail uses `SMTP_HOST=smtp.gmail.com`, `SMTP_PORT=587`, the mailbox as both `SMTP_USERNAME` and `SMTP_FROM`, and an app password in `SMTP_PASSWORD`. STARTTLS is required when a username is set. A local catcher with a blank username does not require STARTTLS. Production should use HTTPS frontend URLs. Do not commit those values. Rate-limit the public verification and reset endpoints at the reverse proxy, as described in `docs/security.md`.

## Environment

Copy `.env.example` to `.env` at the repository root. `.env` is gitignored. Spring Boot loads it from the repository root and from the parent directory of `backend/`, so `mvn -pl backend spring-boot:run` picks it up without exporting variables in each terminal. Vite reads the same file. Do not commit JWT secrets, database passwords, SMTP passwords, or worker tokens.

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

The API image is the backend jar on Java 17. Curl is installed there so Compose can call `GET /api/v1/health`. The frontend image is nginx serving `frontend/dist`, and `/verify-email` and `/reset-password` fall back to `index.html`. The worker image has no HTTP port. Compose sets `WORKER_EXECUTOR=simulator` and declared `DOCKER_CPU_MILLIS=500` and `DOCKER_MEMORY_MB=256`. The Docker socket is not mounted. Compose was not run in the environment where these files were added, because Docker was not installed. Do not treat the stack as verified until those commands succeed.

## Troubleshooting

- `Communications link failure` or a missing datasource property: MySQL is down, or `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, or `SPRING_DATASOURCE_PASSWORD` is unset. Quote the JDBC URL in zsh.
- Worker requests return 401: `WORKER_TOKEN` is empty or does not match the API. Set the same non-empty value in both shells.
- The browser shows a network error or 401: `VITE_API_BASE_URL` must point at the API, and `CORS_ALLOWED_ORIGINS` must include the page origin.
- `DockerDaemonTest` is skipped: `/var/run/docker.sock` is not readable. `mvn verify` does not need Docker. Simulator mode is the default.
