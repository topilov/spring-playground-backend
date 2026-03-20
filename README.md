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

## Build and test

```bash
./gradlew clean test
./gradlew build
```

## Demo account

- Username: `demo`
- Email: `demo@example.com`
- Password: `demo-password`
