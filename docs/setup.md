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

## Email

Copy the SMTP placeholders from `.env.example` and export them in the shell that starts the backend. Leave `SMTP_HOST` empty to boot without a mail server. Verification and reset links are built from `MAIL_VERIFICATION_URL` and `MAIL_RESET_URL`.

Local catcher, from the repository root, if Docker is installed:

```bash
docker run --rm -p 1025:1025 -p 8025:8025 mailhog/mailhog
```

Then export `SMTP_HOST=127.0.0.1`, `SMTP_PORT=1025`, an empty `SMTP_USERNAME`, and `SMTP_FROM=no-reply@localhost`. Open `http://127.0.0.1:8025` to read the message. The API tests do not use this. They mock `JavaMailSender`.

Production should set a real host, port 587 with STARTTLS, a mailbox user and password, and HTTPS frontend URLs. Do not commit those values. Rate-limit the public verification and reset endpoints at the reverse proxy, as described in `docs/security.md`.

## Not built yet

- The worker job loop
- Docker Compose
- GitHub Actions

Database setup is in `docs/database.md`. Hibernate does not create tables. Flyway does, from `backend/src/main/resources/db/migration`.
