# CloudFlow implementation plan

Inspection date: 2026-10-24. Workspace: `/Users/AnanyasMac/Desktop/DeployHub`.

This document records the 2026-10-24 workspace audit and the order to build CloudFlow. Stage 1 is implemented. The audit sections below describe the empty tree before that scaffold.

## Workspace verdict

The workspace contains a Git repository and one document. It does not contain an application.

The product name is CloudFlow. The directory name is DeployHub. Keep this directory as the repository root. Do not add a nested `CloudFlow/` folder.

End-state layout, created only when each stage starts:

```
DeployHub/
  backend/
  worker-agent/
  frontend/
  docs/
  .github/workflows/
```

## Files inspected

| Path | Result |
| --- | --- |
| `/Users/AnanyasMac/Desktop/DeployHub` | Directory listing: `.git/`, `docs/`. No `backend/`, `worker-agent/`, `frontend/`, or `.github/`. |
| `docs/implementation-plan.md` | Only non-Git file. Previous inspection from 2026-10-23. It said the directory was not a Git repository. That is no longer true. |
| `git ls-files` | Tracks only `docs/implementation-plan.md`. |
| `find .` excluding `.git` | Same single file. |

No `pom.xml`, `package.json`, `application.yml`, `application.properties`, Flyway migration, controller, service, repository, entity, DTO, security class, frontend route, Dockerfile, Compose file, or GitHub Actions workflow exists, so none of those were opened.

## Git

| Check | Result |
| --- | --- |
| `git status` | Branch `main`. Working tree clean. |
| `git branch -vv` | `* main d7d3579` with no upstream. |
| `git remote -v` | No remote. |
| `git log -5 --oneline --decorate` | One commit: `d7d3579 (HEAD -> main) docs: add CloudFlow implementation plan`. |

Git was not initialized, reset, or given a remote during this inspection.

## Tool checks

These commands were run on this machine on 2026-10-24.

| Check | Result |
| --- | --- |
| `java -version` | Java 24.0.1 (Oracle). `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-24.jdk/Contents/Home`. |
| `javac -version` | javac 24.0.1. |
| `/usr/libexec/java_home -V` | Default is 24.0.1. JDK 17.0.17 is also installed at `/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`. |
| JDK 17 binary | `java version "17.0.17" 2025-10-21 LTS` when invoked by full path. |
| `mvn -version` | Apache Maven 3.9.16. It runs on Java 24.0.1, not 17. Maven home: `/opt/homebrew/Cellar/maven/3.9.16/libexec`. |
| Homebrew OpenJDK | `/opt/homebrew/opt/openjdk` points at OpenJDK 26.0.1. It is not the active `JAVA_HOME`. |
| `node -v` | v24.18.1 (`/usr/local/bin/node`). |
| `npm -v` | 11.16.0. |
| `git --version` | git version 2.39.5 (Apple Git-154). |
| `docker --version` | `command not found: docker`. |
| `docker compose version` | `command not found: docker`. |
| `colima version` | `command not found: colima`. |
| `podman --version` | `command not found: podman`. |
| `mysql --version` | Client `Ver 26.7.0` (Homebrew), on `PATH`. |
| `mysqld --version` | `Ver 26.7.0` at `/opt/homebrew/Cellar/mysql/26.7.0_2/bin/mysqld`. |
| `mysqladmin ping` | `Access denied for user 'root'@'localhost' (using password: NO)`. The server answered. No password was supplied and no login was attempted. |
| MySQL socket | `/tmp/mysql.sock` exists. |
| `gh --version` | `command not found: gh`. |
| `flyway -v` | `command not found: flyway`. Flyway will be a Maven dependency, so a CLI is unnecessary. |

## Findings

- No application source to preserve, overwrite, or migrate.
- No dependency conflicts, duplicate classes, or broken imports, because there is no source.
- No committed secrets. The only tracked file is this plan.
- No security configuration to review.
- Desktop folders named `Cloudhub` and `Cloudhub-1` sit beside this workspace. Their contents were not opened.

## Risks and missing prerequisites

1. **Default JDK is 24.** Maven compiles with Java 24 unless Stage 1 pins release 17 and points the build at `/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`. JDK 17.0.17 is already installed.
2. **Database.** A later inspection is recorded in `docs/database.md`. Port 3306 is MySQL 9.3.0. Homebrew 26.7.0 is installed and is not the listening server. The proposed target is MySQL 8.0 or newer, using the 9.3.0 server on this machine. No database was created.
3. **Docker is not installed.** Compose and a pinned `mysql:8` service are blocked until Docker is installed. Colima and Podman are also absent.
4. **GitHub CLI is not installed.** Workflow files can still be written locally. Opening pull requests from this machine needs `gh` later.
5. **No Git remote.** Do not invent one. Commits wait for an explicit commit request.

## Package rule

When a class is first needed, put it in its own package under `com.cloudflow`:

- `controller`
- `service`
- `repository`
- `entity`
- `dto`
- `security`

Do not add one-implementation interfaces. The parent POM exists because Stage 1 created both Java modules.

## Stage order

Each stage stops when its check passes. Later stages are not generated early.

### Stage 1 — Backend boots on Java 17

Git already exists on `main` with no remote. Do not run `git init` again and do not add a remote.

- Root `.gitignore` for Maven, Node, IDE files, and `.env`.
- `backend/` Maven project: Spring Boot 3.5.x (exact patch taken from Maven Central at implementation time), `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-test`.
- Compiler release 17, JDK 17 toolchain. One application class and a health endpoint.
- Check: `mvn -version` during the build shows Java 17, `mvn test` passes, and the application starts.

Stage 1, as built, also includes the worker module shell, the React shell, and repository docs. Still deferred: JPA, Security, JWT, Flyway, MySQL, job execution, Compose, Actions.

### Stage 2 — MySQL and Flyway

Waiting for approval of `docs/database.md`. The proposed server is the MySQL 9.3.0 instance already listening on port 3306, with Spring Boot's Connector/J 9.7.0. Any MySQL 8.0 or newer server is acceptable. MySQL 8 is not a separate prerequisite.

- Add `spring-boot-starter-data-jpa`, Flyway, and `mysql-connector-j` at the Spring Boot 3.5.16 managed versions.
- Credentials only from environment variables. Commit an example env file with placeholders.
- First migration uses portable SQL: InnoDB, `utf8mb4`, and no version-specific syntax.
- Check: application starts, Flyway applies, and a repository test or one SQL smoke check passes against the configured server.

### Stage 3 — Security and JWT

- Spring Security, stateless sessions, Jakarta Validation on auth inputs.
- Password hashing, JWT issue and verify, security classes in `com.cloudflow.security`.
- No default secrets in source. JWT secret and DB password stay in the environment.
- Check: unauthenticated requests to protected routes fail, a valid login token succeeds, tests cover both.

### Stage 4 — First job API

- One vertical slice: create a deployment job, list it, read it.
- Controller, service, repository, entity, and DTO in separate packages.
- Worker execution is out of scope here. The API records the job only.
- Check: API tests for create, list, validation failure, and unauthenticated rejection.

### Stage 5 — Worker agent

- The `worker-agent/` module and the parent POM already exist. This stage adds the job loop: claim one queued job and update its status. Same MySQL database, separate process.
- Check: a queued row transitions when the worker runs; the backend test suite still passes.

### Stage 6 — Frontend

- The Vite and React 18 shell already exists. This stage adds login and a jobs page that call the Stage 4 API.
- Check: production build succeeds, and the login-to-job-list path works in a browser against the running backend.

### Stage 7 — Docker Compose

Blocked until Docker is installed.

- Services: MySQL 8.0 or newer, backend, worker-agent, frontend.
- Check: `docker compose up` brings up the database service and the Stage 6 path works against the containers.

### Stage 8 — GitHub Actions

- Workflow runs backend tests on Temurin 17 and the frontend build.
- Check: the workflow file is valid YAML and a push to a real remote shows the jobs green. Local presence of the file alone is not a passing check.

## Stage 1 result

Completed 2026-10-24. Git `main` was left in place. `git remote -v` still prints nothing.

| Check | Result |
| --- | --- |
| `mvn -version` with `JAVA_HOME` set to JDK 17 | Java version 17.0.17, Maven 3.9.16 |
| `mvn verify` | BUILD SUCCESS. Reactor: `cloudflow`, `cloudflow-backend`, `cloudflow-worker-agent`. Compiler `release 17`. |
| Backend test | `HealthControllerTest`: Tests run: 1, Failures: 0 |
| Worker test | `WorkerAgentApplicationTest`: Tests run: 1, Failures: 0. The Spring context started. |
| `npm install` in `frontend/` | 264 packages added, 0 vulnerabilities |
| `npm run build` | Vite 8.3.5 production build succeeded |
| `npm run lint` | ESLint finished with no findings |
| Browser | `http://127.0.0.1:5173/` rendered the CloudFlow shell, title CloudFlow, API `http://localhost:8080`, health `http://localhost:8080/api/health` |

`spring-boot:run` was not left running. The health check that passed is the MockMvc test inside `mvn verify`.

## API foundation

Completed 2026-10-24 after the database plan, and before any database code. `GET /api/v1/health` and `GET /actuator/health` return `{"status":"UP"}`. Unknown paths return `ApiError` with no stack trace. `mvn verify` on JDK 17 passed: backend tests 4, worker tests 1. The jar was started with Java 17.0.17 on port 8080, the default profile, and then stopped. `dev` and `prod` profile files are present and were not booted on their own.

The database stage below is still waiting for approval. It was not started.

## Recommended next step

Stop. Database setup still waits for approval of `docs/database.md`. Stage 7 stays blocked until Docker is installed.
