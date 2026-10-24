# Authentication

User requests and worker requests use different credentials. A user JWT is not a worker credential.

## Passwords

Registration stores a BCrypt hash. The API never returns that hash. Login compares the submitted password with the hash. Unknown tenants, unknown emails, and wrong passwords all return 401 with `Authentication failed`.

## Accounts

`POST /api/v1/auth/register` creates a tenant and an `OWNER`. The new user's `email_verified_at` stays null. The response has no access token.

`REQUIRE_VERIFIED_EMAIL` defaults to true. A correct password for an unverified user returns 403 `Email verification required`. Stage 7 sends the verification email and sets `email_verified_at`. There is no separate disabled column. `AccountPolicy` is the place that will check one if the schema gains it.

An `OWNER` or `ADMIN` can add a user with `POST /api/v1/users`. That user joins the caller's tenant. A tenant id in the JSON body is ignored. An admin can add a `MEMBER`. Only an owner can add an `ADMIN`. Nobody can add another `OWNER` through this endpoint. A `MEMBER` receives 403.

## Access tokens

`POST /api/v1/auth/login` returns a bearer access token. Send it as `Authorization: Bearer <token>`.

| Decision | What the server does |
| --- | --- |
| Signing | HMAC SHA-256. The key is `JWT_SECRET` and must be at least 32 bytes. It is not in source. |
| Expiration | `JWT_EXPIRATION`, default 15 minutes. |
| Claims | `sub` is the user's public id. The tenant and role are loaded from that user on each request. A claim is not authorization. |
| Refresh | Not issued. Add rotating refresh tokens, stored only as hashes, when sessions need to outlive the access token. |
| Revocation | None. The server does not store access tokens, so it cannot revoke one before it expires. Logout means the client drops the token. |
| Storage | The browser should keep the token in memory. Do not put it in `localStorage`. |

## Tenant isolation

Deployment, version, event, execution, log, and notification reads use the tenant id on the authenticated user. A resource in another tenant is a 404, the same response as a missing id. Notification reads also use the caller's user id.

## Workers

`/api/v1/worker/**` is a separate filter chain. It accepts `Authorization: Worker <WORKER_TOKEN>` and rejects user bearer tokens. `WORKER_TOKEN` is its own environment variable. An empty value denies every worker request.

## CORS

`CORS_ALLOWED_ORIGINS` is a comma-separated list. The default is `http://localhost:5173`. `*` is rejected. Credentials are not allowed; the browser sends the bearer token in a header.
