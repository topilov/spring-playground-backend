# Passkey WebAuthn Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add production-oriented WebAuthn passkey registration, passkey management, and passkey login that create the normal `JSESSIONID` session and keep the backend contract/docs in sync.

**Architecture:** Use Yubico `java-webauthn-server` for ceremony generation and verification, persist durable passkey credentials in PostgreSQL, keep short-lived registration/authentication ceremony state in Redis behind a `PasskeyCeremonyStore`, and funnel both password and passkey login through one canonical session-establishment service.

**Tech Stack:** Kotlin, Spring Boot, Spring Security, Spring Data JPA, Spring Data Redis, Flyway, MockMvc, Testcontainers PostgreSQL, Yubico `webauthn-server-core`

---

### Task 1: Red tests for passkey behavior

**Files:**
- Modify: `src/test/kotlin/me/topilov/springplayground/SecurityEndpointsTest.kt`
- Create: `src/test/kotlin/me/topilov/springplayground/auth/passkey/PasskeyManagementServiceTest.kt`
- Create: `src/test/kotlin/me/topilov/springplayground/auth/passkey/InMemoryPasskeyCeremonyStoreTest.kt`
- Create: `src/test/kotlin/me/topilov/springplayground/auth/passkey/TestPasskeyConfiguration.kt`

- [ ] Add failing integration tests for authenticated registration options/verify, list/rename/delete, unauthenticated passkey login, and session creation with the same response shape as password login.
- [ ] Add the required failure-path test proving a ceremony cannot be reused successfully.
- [ ] Add focused failing tests for ceremony-store one-time semantics and management ownership logic.
- [ ] Run the targeted tests and confirm they fail for the expected missing behavior.

### Task 2: Durable passkey persistence and configuration

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-test.yml`
- Create: `src/main/resources/db/migration/V8__create_auth_passkey_credential_table.sql`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/passkey/domain/PasskeyCredential.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/passkey/repository/PasskeyCredentialRepository.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/passkey/config/PasskeyProperties.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/passkey/config/PasskeyConfiguration.kt`

- [ ] Add the WebAuthn dependency and passkey configuration properties.
- [ ] Add the Flyway migration and JPA entity/repository for stored passkeys.
- [ ] Run the targeted persistence tests and keep iterating until the schema and mapping behave correctly.

### Task 3: Ceremony store and WebAuthn adapter layer

**Files:**
- Create: `src/main/kotlin/me/topilov/springplayground/auth/passkey/ceremony/PasskeyCeremonyStore.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/passkey/ceremony/RedisPasskeyCeremonyStore.kt`
- Create: `src/test/kotlin/me/topilov/springplayground/auth/passkey/InMemoryPasskeyCeremonyStore.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/passkey/webauthn/PasskeyWebAuthnService.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/passkey/webauthn/YubicoPasskeyWebAuthnService.kt`

- [ ] Implement separate registration and authentication ceremony records with a configurable ~5 minute TTL.
- [ ] Ensure ceremony state is fail-closed, user-bound where required, and invalidated after successful verification.
- [ ] Add the WebAuthn adapter that converts DTO payloads and persisted credentials to and from Yubico library types.
- [ ] Run the focused ceremony tests and keep iterating until the one-time-use behavior is green.

### Task 4: Shared session login path and passkey application services

**Files:**
- Create: `src/main/kotlin/me/topilov/springplayground/auth/service/SessionLoginService.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/service/AuthService.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/passkey/service/PasskeyRegistrationService.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/passkey/service/PasskeyAuthenticationService.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/passkey/service/PasskeyManagementService.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/passkey/exception/...`

- [ ] Extract the canonical session-establishment logic so password and passkey login share the same response/session behavior.
- [ ] Implement passkey registration, duplicate-credential rejection, login, last-used updates, and management ownership checks.
- [ ] Run targeted auth/passkey tests after each slice and keep iterating until green.

### Task 5: HTTP DTOs, controllers, and security wiring

**Files:**
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/web/AuthController.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/web/AuthExceptionHandler.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/config/SecurityConfig.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/passkey/dto/...`

- [ ] Add explicit DTOs for passkey registration, management, and login.
- [ ] Expose the authenticated and unauthenticated passkey endpoints.
- [ ] Mark the login endpoints public and keep management endpoints authenticated-only.
- [ ] Re-run the MockMvc auth tests and keep iterating until status codes and response bodies match the contract.

### Task 6: Contract and README updates

**Files:**
- Modify: `docs/contracts/auth.md`
- Create: `docs/contracts/auth-api.md`
- Modify: `openapi/openapi.yaml`
- Modify: `README.md`

- [ ] Document the new passkey flows, auth requirements, JSON payloads, and example requests/responses.
- [ ] Keep `docs/contracts/auth.md` canonical while ensuring `docs/contracts/auth-api.md` exists and points to the same contract content.
- [ ] Document why Yubico `java-webauthn-server` was chosen and note the Redis ceremony-state behavior.
- [ ] Re-run the OpenAPI exposure tests after the contract updates.

### Task 7: Broad verification

**Files:**
- No additional file targets; this is verification only.

- [ ] Run `./gradlew test` as the broadest relevant backend verification command.
- [ ] If failures appear, fix them and rerun until green or clearly identify any environment blocker.
