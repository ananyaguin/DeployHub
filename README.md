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

## Run

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
mvn verify
```

```bash
cp .env.example .env
cd frontend
npm install
npm run dev
```

The API listens on port 8080. `GET /api/v1/health` returns `{"status":"UP"}`. Actuator health is `GET /actuator/health`. Set `SPRING_PROFILES_ACTIVE` to `dev` or `prod` when you want that profile. The frontend dev server listens on port 5173.

`npm run build` writes `frontend/dist`. `npm run lint` checks `frontend/src`.

The worker jar starts and then exits. It has no web server and no work to wait on.

## Scheduling, audit, and notices

A worker registers a declared CPU (`cpuMillis`) and memory (`memoryMb`). Omitted values default to 1000 CPU milliseconds and 512 MB. Those numbers are the capacity the worker claims it can take. The API does not read the host. A queued execution defaults to 100 CPU milliseconds and 64 MB. Send `cpuMillis` and `memoryMb` on `POST /api/v1/executions` to ask for more, within 1–16000 and 1–65536. The worker process sends its `DOCKER_CPU_MILLIS` and `DOCKER_MEMORY_MB` at registration as that declared capacity. Those Docker settings still cap a container when Docker mode is on. They are not a measurement of the machine.

Claim picks the oldest queued execution that fits the worker's declared capacity. A task larger than the worker's declared capacity is left for another worker. A task that would fit only after current work finishes stays queued, so a newer smaller task does not take its place. An offline worker is rejected. Two claims for the same worker lock that worker before reading capacity, so the same free capacity is not handed out twice. Attempt limits, retries, and the worker token are unchanged.

`GET /api/v1/audit` lists the caller's tenant events, newest page first. Events stay append-only. They can name the acting user email or `worker:<name>`, the deployment, and the execution. They do not include JWTs, worker tokens, or environment values. The dashboard shows this list and in-app notices.

In-app notices use the existing notification deliveries with channel `IN_APP`. One notice is stored for a failed deploy, stop, or rollback, for a successful rollback, and for the first simulated unhealthy result in a streak. The same result submitted again does not add another notice. `GET /api/v1/notification-deliveries` returns only the caller's rows.

`mvn verify` from the repository root runs the backend and worker tests. It does not need MySQL, SMTP, Docker, or a network. One worker test skips when `/var/run/docker.sock` is missing. In `frontend/`, `npm ci`, `npm run lint`, and `npm run build` check the UI.

Git remote `origin` points at GitHub. `.github/workflows/ci.yml` defines CI. A passing local build does not mean the Actions run has succeeded.
