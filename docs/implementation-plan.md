# CloudFlow implementation plan

Inspection date: 2026-10-23. Workspace: `/Users/AnanyasMac/Desktop/DeployHub`.

This document records what is actually in the workspace and the order to build CloudFlow. No application code has been generated.

## Workspace verdict

The workspace is empty. It is not a Git repository. There is no existing backend, frontend, worker, database schema, configuration, or documentation other than this file.

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

## Checks run

| Check | Command result |
| --- | --- |
| Directory listing | `ls -la` shows only `.` and `..`. Created 2026-10-23 10:03. |
| All files, including hidden | `find . -mindepth 1` printed nothing before this document. |
| Git status | `fatal: not a git repository (or any of the parent directories): .git` |
| Git remote | None. `git remote -v` produced no output. |
| Git log | None. |
| `java -version` | Java 24.0.1 (Oracle), `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-24.jdk/Contents/Home` |
| `javac -version` | javac 24.0.1 |
| `/usr/libexec/java_home -V` | 24.0.1 is the default. 17.0.17 is also installed at `/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`. |
| JDK 17 binary | `java version "17.0.17" 2025-10-21 LTS` when invoked by full path. |
| `mvn -version` | Apache Maven 3.9.16. It runs on Java 24.0.1, not 17. |
| `node -v` | v24.18.1 |
| `npm -v` | 11.16.0 |
| `git --version` | git version 2.39.5 (Apple Git-154) |
| `docker --version` | `command not found: docker` |
| `docker compose version` | `command not found: docker` |
| `colima`, `podman` | Both missing. |
| `mysql --version` | Client `Ver 26.7.0` (Homebrew), `/opt/homebrew/bin/mysql` |
| `mysqld --version` | `Ver 26.7.0`, Homebrew `mysql/26.7.0_2` |
| MySQL reachability | Socket `/tmp/mysql.sock` exists. `mysqladmin ping` returned `Access denied for user 'root'@'localhost' (using password: NO)`. The server is up. No login was attempted. |
| `gh` | Not on `PATH`. |
| `flyway` CLI | Not on `PATH`. Flyway will be a Maven dependency, so a CLI is unnecessary. |
| Homebrew OpenJDK | `/opt/homebrew/opt/openjdk` points at OpenJDK 26.0.1. It is not the active `JAVA_HOME`. |

No `pom.xml`, `package.json`, `application.yml`, Flyway migration, controller, service, repository, entity, security class, or frontend route exists, so none of those were opened.

## Findings

- No source to preserve, overwrite, or migrate.
- No dependency conflicts, duplicate classes, or broken imports.
- No committed secrets, because there are no files.
- No security configuration to review.
- Sibling Desktop folders named `Cloudhub` and `Cloudhub-1` are outside this workspace and were not inspected.

## Risks and missing prerequisites

1. **Default JDK is 24.** Maven will compile with Java 24 unless Stage 1 pins release 17 and points the build at `/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`. JDK 17.0.17 is already installed.
2. **Host MySQL is 26.7.0, not MySQL 8.** The target is MySQL 8. Do not point CloudFlow at this server until that mismatch is accepted or a MySQL 8 instance exists. No database was created.
3. **Docker is not installed.** Compose and a pinned `mysql:8` service are blocked until Docker is installed. Colima and Podman are also absent.
4. **GitHub CLI is not installed.** Workflow files can still be written locally. Opening pull requests from this machine needs `gh` later.
5. **No Git repository.** There is no history and no remote. Do not invent a remote. `git init` waits for Stage 1 approval. Commits wait for an explicit commit request.

## Package rule

When a class is first needed, put it in its own package under `com.cloudflow`:

- `controller`
- `service`
- `repository`
- `entity`
- `dto`
- `security`

Do not add empty packages, one-implementation interfaces, or a parent POM before a second module exists.

## Stage order

Each stage stops when its check passes. Later stages are not generated early.

### Stage 1 — Backend boots on Java 17

- `git init` in this directory. No remote.
- Root `.gitignore` for Maven, Node, IDE files, and `.env`.
- `backend/` Maven project: Spring Boot 3.5.x (exact patch taken from Maven Central at implementation time), `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-test`.
- Compiler release 17, JDK 17 toolchain. One application class and a health endpoint.
- Check: `mvn -version` during the build shows Java 17, `mvn test` passes, and the application starts.

Deferred: JPA, Security, JWT, Flyway, MySQL, worker, frontend, Compose, Actions.

### Stage 2 — MySQL 8 and Flyway

Blocked until a MySQL 8 instance is available. Preferred source is Compose `mysql:8` after Docker exists. Host MySQL 26.7.0 is not a silent substitute.

- Add `spring-boot-starter-data-jpa`, Flyway, and the MySQL driver.
- Credentials only from environment variables. Commit an example env file with placeholders.
- First migration and the first entity/repository pair only for the first real table.
- Check: application starts, Flyway applies, a repository test or a single SQL smoke check passes against MySQL 8.

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

- New `worker-agent/` Maven module on Java 17. Separate process, same MySQL database.
- Claims one queued job and updates its status. Add a parent POM only at this point to share the Java and dependency versions.
- Check: a queued row transitions when the worker runs; the backend test suite still passes.

### Stage 6 — Frontend

- `frontend/` with Vite, React 18, JavaScript and JSX only.
- Login and a jobs page that call the Stage 4 API.
- Check: production build succeeds, and the login-to-job-list path works in a browser against the running backend.

### Stage 7 — Docker Compose

Blocked until Docker is installed.

- Services: MySQL 8, backend, worker-agent, frontend.
- Check: `docker compose up` brings up MySQL 8 and the Stage 6 path works against the containers.

### Stage 8 — GitHub Actions

- Workflow runs backend tests on Temurin 17 and the frontend build.
- Check: the workflow file is valid YAML and a push to a real remote shows the jobs green. Local presence of the file alone is not a passing check.

## Recommended next step

Approve Stage 1 only: initialize Git with no remote, add `.gitignore`, and add a Java 17 Spring Boot backend that starts and exposes health.

Do not start Stage 2 until MySQL 8 is available. Do not start Stage 7 until Docker is installed.
