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

### Reset password

```bash
curl \
  -H 'Content-Type: application/json' \
  -d '{"token":"token-from-email","newPassword":"new-very-secret-password"}' \
  http://localhost:8080/api/auth/reset-password
```

## Mail configuration

The application uses Spring Mail for delivery and Thymeleaf templates from `src/main/resources/templates/mail`.

Available properties:

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

Example local setup for a frontend reset link:

```bash
APP_PUBLIC_BASE_URL=http://localhost:3000 \
APP_RESET_PASSWORD_PATH=/reset-password \
./gradlew bootRun --args='--spring.profiles.active=local'
```

## Build and test

```bash
./gradlew clean test
./gradlew build
```

## Demo account

- Username: `demo`
- Email: `demo@example.com`
- Password: `demo-password`
