# Authentication

User requests and worker requests use different credentials. A user JWT is not a worker credential.

## Passwords

Registration stores a BCrypt hash. The API never returns that hash. Login compares the submitted password with the hash. Unknown tenants, unknown emails, and wrong passwords all return 401 with `Authentication failed`.

## Accounts

`POST /api/v1/auth/register` creates a tenant and an `OWNER`. The new user's `email_verified_at` stays null. The response has no access token. Registration also stores a hashed verification token and sends the raw token in an email. The API response and logs do not include that raw token.

`REQUIRE_VERIFIED_EMAIL` defaults to true. A correct password for an unverified user returns 403 `Email verification required`. `POST /api/v1/auth/verify-email` sets `email_verified_at` only after the token is valid, unexpired, and unused. There is no separate disabled column. `AccountPolicy` is the place that will check one if the schema gains it.

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

Deployment, version, event, execution, log, and notification reads and writes use the tenant id on the authenticated user. A resource in another tenant is a 404, the same response as a missing id. A tenant id in a JSON body is ignored. Notification reads also use the caller's user id.

Deployment status stays `DRAFT`, `ACTIVE`, or `ARCHIVED`. Deploy, rollback, and stop record an execution as `SUCCEEDED` in the same request. That record is local: the API does not call a cloud provider or a container platform. `FAILED` remains an execution status for a later worker. Two requests cannot both move the same deployment; the update matches the status and current version number it read.

## Email verification and password reset

Both flows store SHA-256 hex of a 32-byte random token. The raw token is only in the email link. `used_at` is set by a conditional update (`used_at is null` and `expires_at` still in the future), so two concurrent requests cannot both consume one token. Other unused tokens for that user are retired in the same transaction. Invalid, expired, reused, and already-consumed tokens all return 400 `Request could not be processed`.

Verification links last `MAIL_VERIFICATION_TTL` (default 24 hours). Reset links last `MAIL_RESET_TTL` (default 30 minutes). Password reset does not set `email_verified_at`.

`POST /api/v1/auth/password-reset` returns 204 for a known account and an unknown account. A mail failure on that endpoint also returns 204. Registration mail failure returns 503 `Email could not be sent` and does not include the SMTP error.

These three endpoints are public. This process does not rate-limit them. Put a limit in front of production, such as 5 requests per client address per hour for `/api/v1/auth/verify-email`, `/api/v1/auth/password-reset`, and `/api/v1/auth/password-reset/confirm`.

SMTP settings are `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and `SMTP_FROM`. An empty host leaves mail unconfigured. Links use `MAIL_VERIFICATION_URL` and `MAIL_RESET_URL`.

## Workers

`/api/v1/worker/**` is a separate filter chain. It accepts `Authorization: Worker <WORKER_TOKEN>` and rejects user bearer tokens. `WORKER_TOKEN` is its own environment variable. An empty value denies every worker request.

## CORS

`CORS_ALLOWED_ORIGINS` is a comma-separated list. The default is `http://localhost:5173`. `*` is rejected. Credentials are not allowed; the browser sends the bearer token in a header.
