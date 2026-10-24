# Architecture

Stage 1 establishes three processes and the package boundaries. It does not implement deployments, accounts, or persistence.

```
browser  ->  frontend (Vite, React 18)
                |
                v
         cloudflow-backend   GET /api/health
         cloudflow-worker-agent   separate JVM, no queue yet
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
| `com.cloudflow.dto` | Request and response records |
| `com.cloudflow.security` | Authentication and request security |
| `com.cloudflow.exception` | API errors |
| `com.cloudflow.configuration` | Spring configuration outside security and persistence |

`service`, `entity`, `repository`, `security`, `exception`, and `configuration` contain a package marker only. The first real type in each package arrives with the stage that needs it.

## Frontend

JavaScript and JSX only. Pages compose through `src/routes/AppRoutes.jsx`. There is no router library and no TypeScript. Design tokens live in `src/styles/tokens.css` and are light-theme only.

`VITE_API_BASE_URL` comes from the repository-root `.env`. The shell displays that address. It does not call the API yet.
