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

## Not built yet

- Docker Compose
- GitHub Actions

Database setup is in `docs/database.md`. Hibernate does not create tables. Flyway does, from `backend/src/main/resources/db/migration`.
