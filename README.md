# Spring Playground

Template Spring Boot project on Kotlin with session-based security, PostgreSQL, Flyway, Actuator, and a small auth/profile example domain.

## Stack

- Kotlin `2.3.20`
- Java `25`
- Gradle `9.3.0`
- Spring Boot `4.1.0-M3`
- PostgreSQL
- Flyway
- Actuator
- Testcontainers
- Spring Mail
- Thymeleaf email templates
- Yubico `java-webauthn-server`

## Prerequisites

- Java 25 available on `PATH`
- Docker with Compose support

## Local run

1. Start PostgreSQL:

   ```bash
   docker compose up -d
   ```

2. Run the application:

   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local'
   ```

3. Check health:

   ```bash
   curl http://localhost:8080/actuator/health
   ```

4. Login with the seeded demo account:

   ```bash
   curl -i \
     -c cookies.txt \
     -H 'Content-Type: application/json' \
     -d '{"usernameOrEmail":"demo","password":"demo-password"}' \
     http://localhost:8080/api/auth/login
   ```

   The same account also supports login by email: `demo@example.com`.

5. Call a protected endpoint with the session cookie:

   ```bash
   curl \
     -b cookies.txt \
     http://localhost:8080/api/profile/me
   ```

6. Update the profile:

   ```bash
   curl \
     -b cookies.txt \
     -H 'Content-Type: application/json' \
     -X PUT \
     -d '{"displayName":"Updated Demo","bio":"Updated from README"}' \
     http://localhost:8080/api/profile/me
   ```

7. Logout:

   ```bash
   curl -i -b cookies.txt -X POST http://localhost:8080/api/auth/logout
   ```

## Auth flows

### Register

```bash
curl \
  -H 'Content-Type: application/json' \
  -d '{"username":"new-user","email":"new-user@example.com","password":"very-secret-password"}' \
  http://localhost:8080/api/auth/register
```

This endpoint creates the account and profile, sends a welcome email, and does not auto-login the user.

### Forgot password

```bash
curl \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com"}' \
  http://localhost:8080/api/auth/forgot-password
```

The response is always generic:

```json
{"accepted":true}
```

When the account exists, the backend creates a one-time reset token in Redis and sends an email link to `APP_PUBLIC_BASE_URL + APP_RESET_PASSWORD_PATH?token=...`.

### Reset password

```bash
curl \
  -H 'Content-Type: application/json' \
  -d '{"token":"token-from-email","newPassword":"new-very-secret-password"}' \
  http://localhost:8080/api/auth/reset-password
```

Successful reset updates the stored password hash, invalidates all active reset tokens for the user, and does not auto-login the user.

### Passkeys

The backend supports:

- authenticated passkey registration and management at `/api/auth/passkeys*`
- unauthenticated passkey login at `/api/auth/passkey-login/*`

Passkey ceremonies are production-shaped:

- durable credentials are stored in PostgreSQL
- short-lived registration and login ceremony state is stored in Redis
- successful passkey login returns the same response body as password login and creates the normal `JSESSIONID` session

The server-side WebAuthn implementation uses Yubico `java-webauthn-server`. It was chosen because it is a mature Java WebAuthn relying-party library with first-class support for registration and assertion ceremonies, credential repositories, and discoverable passkey login.

## Mail configuration

The application uses Spring Mail for delivery and Thymeleaf templates from `src/main/resources/templates/mail`.

Available properties:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_URL`
- `APP_CORS_ALLOWED_ORIGINS`
- `APP_PASSKEY_RP_ID`
- `APP_PASSKEY_RP_NAME`
- `APP_PASSKEY_ORIGINS`
- `APP_PASSKEY_CEREMONY_TTL`
- `MAIL_HOST`
- `MAIL_PORT`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MAIL_SMTP_AUTH`
- `MAIL_SMTP_STARTTLS`
- `MAIL_FROM`
- `MAIL_APP_NAME`
- `APP_PUBLIC_BASE_URL`
- `APP_RESET_PASSWORD_PATH`
- `APP_PASSWORD_RESET_TTL`
- `SESSION_COOKIE_SECURE`
- `SESSION_COOKIE_SAME_SITE`

Example local setup for a frontend reset link:

```bash
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173 \
APP_PUBLIC_BASE_URL=http://localhost:5173 \
APP_PASSKEY_RP_ID=localhost \
APP_PASSKEY_ORIGINS=http://localhost:5173 \
APP_RESET_PASSWORD_PATH=/reset-password \
./gradlew bootRun --args='--spring.profiles.active=local'
```

`SESSION_COOKIE_SECURE` defaults to `true` outside the `local` and `test` profiles so the session cookie is only sent over HTTPS in non-local environments.

Production-oriented defaults use `https://topilov.dev` for browser origins and public links. The `local` and `test` profiles keep localhost-oriented fallbacks so frontend development can still run over HTTP.

## Build and test

```bash
./gradlew clean test
./gradlew build
```

## Contract documentation

This repository publishes the HTTP API contract in two forms:

- Human-readable docs in `docs/contracts/*`
- Machine-readable OpenAPI in `openapi/openapi.yaml`

Frontend should use the OpenAPI contract for generated clients and types, then use the markdown contract docs for session auth flow, cookies, CSRF notes, and behavior details.

## GitHub Actions

### CI

Workflow: `.github/workflows/ci.yml`

- Runs on every `push`
- Runs on every `pull_request`
- Uses Java `25`, matching the project toolchain
- Executes `./gradlew build`

### Contract site and GitHub Pages

Workflow: `.github/workflows/contract-site.yml`

- Runs on pushes to `main`
- Can also be triggered manually with `workflow_dispatch`
- Starts PostgreSQL
- Starts the backend with the repository default configuration
- Waits for `http://127.0.0.1:8080/actuator/health`
- Fetches runtime OpenAPI from:
  - `/v3/api-docs`
  - `/v3/api-docs.yaml`
- Publishes a small GitHub Pages contract site containing:
  - generated `openapi/openapi.json`
  - generated `openapi/openapi.yaml`
  - `docs/contracts/*`
  - `docs/openapi.md`
- Publishes both the machine-readable OpenAPI contract and the human-readable markdown contract docs
- Shows `backend.log` in workflow output if startup or OpenAPI export fails
- Uploads `backend.log` as a workflow artifact on failure

To use the Pages deployment workflow, set the repository Pages source to `GitHub Actions` in GitHub settings.

### Pages URL structure

The published site will typically live at:

```text
https://<owner>.github.io/<repo>/
```

Published contract URLs under that site:

```text
https://<owner>.github.io/<repo>/openapi/openapi.yaml
https://<owner>.github.io/<repo>/openapi/openapi.json
https://<owner>.github.io/<repo>/docs/contracts/auth.md
https://<owner>.github.io/<repo>/docs/contracts/profile.md
https://<owner>.github.io/<repo>/docs/contracts/public.md
https://<owner>.github.io/<repo>/docs/openapi.md
```

### Frontend usage

Frontend should consume the published OpenAPI contract from GitHub Pages for generated types and API clients.
Frontend should not depend on a locally running backend instance to discover the contract.

Recommended source:

```text
https://<owner>.github.io/<repo>/openapi/openapi.yaml
```

Use the markdown docs as the companion reference for:

- login and logout flow
- session cookie handling
- authenticated endpoint behavior
- practical integration notes not captured cleanly in OpenAPI

## Demo account

- Username: `demo`
- Email: `demo@example.com`
- Password: `demo-password`
