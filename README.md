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

Git remotes were left unchanged. There is no remote on this repository.
