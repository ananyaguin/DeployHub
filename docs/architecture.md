# Architecture

The backend owns the MySQL schema through Flyway. JPA entities follow that schema. The worker does not migrate the database. Controllers return response records.

```
browser  ->  frontend (Vite, React 18)
                |
                v
         cloudflow-backend   health, accounts, deployment queue, worker claim API
         cloudflow-worker-agent   separate JVM, polls the API, simulator or local Docker
```

Both Java modules inherit `com.cloudflow:cloudflow`, which pins Java 17 and Spring Boot 3.5.16. A parent POM exists because there are two modules.

## Backend packages

| Package | Holds |
| --- | --- |
| `com.cloudflow` | `CloudFlowApplication` |
| `com.cloudflow.controller` | HTTP adapters |
| `com.cloudflow.service` | Business operations |
| `com.cloudflow.entity` | Persistence types |
| `com.cloudflow.repository` | Spring Data repositories |
| `com.cloudflow.dto.request` | Validated client input |
| `com.cloudflow.dto.response` | Client output, including `ApiError` |
| `com.cloudflow.security` | Authentication and request security |
| `com.cloudflow.exception` | API errors |
| `com.cloudflow.configuration` | Spring configuration outside security and persistence |

`configuration` holds mail settings and worker timeouts. Authentication decisions are in `docs/security.md`. The worker agent does not open the database. `WORKER_EXECUTOR=simulator` (the default) runs `DEPLOY`, `ROLLBACK`, and `STOP` in process. `WORKER_EXECUTOR=docker` uses the local Docker daemon for those same tasks and does not fall back to the simulator when the daemon is down. Neither mode calls Kubernetes or a cloud API.

## Frontend

JavaScript and JSX only. Pages compose through `src/routes/AppRoutes.jsx`. There is no router library and no TypeScript. Design tokens live in `src/styles/tokens.css` and are light-theme only.

`VITE_API_BASE_URL` comes from the repository-root `.env`. The signed-in UI calls the account, deployment, version, event, and execution endpoints. It does not send a tenant id for access checks, and it does not talk to Docker.
