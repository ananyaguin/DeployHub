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

Run the worker:

```bash
mvn -pl worker-agent spring-boot:run
```

The worker process exits after the context starts. That is expected until it has a job loop.

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

Vite loads environment files from the repository root (`envDir` is `..`). The only variable is `VITE_API_BASE_URL`.

## Not built yet

- Spring Security and JWT
- Deployment APIs and the worker job loop
- Docker Compose
- GitHub Actions

Database setup is in `docs/database.md`. Hibernate does not create tables. Flyway does, from `backend/src/main/resources/db/migration`.
