# CloudFlow

Deployment and job orchestration. This repository is the Stage 1 scaffold: a Java 17 backend, a Java 17 worker process, and a React shell. Jobs, accounts, and the database are not built yet.

The product name is CloudFlow. The directory name is DeployHub. This directory is the repository root.

## Layout

| Path | Artifact | Role |
| --- | --- | --- |
| `pom.xml` | `com.cloudflow:cloudflow` | Maven parent, Java 17, Spring Boot 3.5.16 |
| `backend/` | `cloudflow-backend` | HTTP API. Health only. |
| `worker-agent/` | `cloudflow-worker-agent` | Separate process. No job loop yet. |
| `frontend/` | `cloudflow-frontend` | React 18 and Vite. JavaScript and JSX. |

Backend packages under `com.cloudflow` stay separate: `controller`, `service`, `entity`, `repository`, `dto`, `security`, `exception`, `configuration`. The worker lives in `com.cloudflow.worker`.

## Prerequisites

- JDK 17. This machine has it at `/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`. The default `java` is 24, and the build rejects that.
- Maven 3.9 or newer.
- Node.js 20.19+ or 22.12+, with npm. Vite 8 does not support older Node 20 releases.

The database is not wired in this stage. The compatibility plan is in `docs/database.md`: MySQL 8.0 or newer, with Connector/J 9.7.0 from Spring Boot 3.5.16. This machine's server on port 3306 is MySQL 9.3.0.

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

The API listens on port 8080. `GET /api/health` returns `{"status":"UP"}`. The frontend dev server listens on port 5173.

`npm run build` writes `frontend/dist`. `npm run lint` checks `frontend/src`.

The worker jar starts and then exits. It has no web server and no work to wait on.

Git remotes were left unchanged. There is no remote on this repository.
