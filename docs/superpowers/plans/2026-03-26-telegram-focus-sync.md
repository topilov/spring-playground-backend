# Telegram Focus Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dedicated `telegram` domain that supports backend-driven Telegram account connection through TDLight, persistent server-side Telegram sessions, one hashed automation token per user, user-configurable emoji mappings for supported focus modes, and a minimal token-authenticated iOS Shortcut endpoint that recalculates and syncs Telegram emoji status.

**Architecture:** Keep all feature ownership in a new top-level `telegram` domain. Persist durable account linkage, focus state, mappings, and token metadata in PostgreSQL; keep pending Telegram auth state in Redis; store TDLight session data on disk using a per-user session directory and encrypted TDLib database key; rate-limit automation requests through the existing protection module; and keep controllers thin by pushing orchestration into Telegram application services.

**Tech Stack:** Kotlin, Spring Boot, Spring Security, Spring Data JPA, Spring Data Redis, Flyway, MockMvc, Testcontainers PostgreSQL, TDLight Java

---

### Task 1: Red tests for Telegram domain behavior

**Files:**
- Create: `src/test/kotlin/me/topilov/springplayground/TelegramIntegrationTest.kt`
- Create: `src/test/kotlin/me/topilov/springplayground/telegram/application/TelegramFocusAutomationApplicationServiceTest.kt`
- Create: `src/test/kotlin/me/topilov/springplayground/telegram/application/TelegramConnectionApplicationServiceTest.kt`
- Create: `src/test/kotlin/me/topilov/springplayground/telegram/domain/FocusModeResolutionTest.kt`
- Create: `src/test/kotlin/me/topilov/springplayground/telegram/TestTelegramConfiguration.kt`
- Modify: `src/test/kotlin/me/topilov/springplayground/support/SecurityIntegrationTestSupport.kt`

- [ ] Add failing integration tests for viewing Telegram settings, starting Telegram connect, completing code/password steps, creating/regenerating/revoking the automation token, updating focus settings, calling the token-authenticated automation endpoint, restoring no-focus status, and preserving preferences across disconnect.
- [ ] Add focused failing unit tests for effective-focus resolution priority and mapping fallback behavior.
- [ ] Add focused failing application-service tests for automation token lifecycle, pending-auth step transitions, and Telegram sync orchestration.
- [ ] Add a test Telegram configuration with fake infrastructure so tests prove backend behavior without relying on a real Telegram account.
- [ ] Run the targeted Telegram tests and confirm they fail for the expected missing behavior.

### Task 2: Add dependencies, config, and schema

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-local.yml`
- Modify: `src/main/resources/application-test.yml`
- Create: `src/main/resources/db/migration/V10__create_telegram_tables.sql`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/config/TelegramProperties.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/config/TelegramConfiguration.kt`

- [ ] Add the TDLight Java dependency and the repository entries it requires, plus any platform-native dependency choice that is safe for the project’s current runtime expectations.
- [ ] Add Telegram-focused application properties for session root, pending-auth TTL, default focus mappings, automation-token length, rate-limit defaults, and encryption key material.
- [ ] Add the Flyway migration that creates durable Telegram account, focus state, mapping, and automation token tables with the correct uniqueness and foreign-key constraints.
- [ ] Run the targeted persistence and context-startup tests and iterate until the schema and property binding are green.

### Task 3: Durable Telegram domain persistence

**Files:**
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramAccountConnection.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramAutomationToken.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramFocusMapping.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramFocusState.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramConnectionStatus.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramFocusMode.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/repository/TelegramAccountConnectionRepository.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/repository/TelegramAutomationTokenRepository.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/repository/TelegramFocusMappingRepository.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/repository/TelegramFocusStateRepository.kt`

- [ ] Implement the JPA entities with focused responsibilities and predictable file boundaries.
- [ ] Model the fixed supported focus modes and connection statuses as enums with stable persisted values.
- [ ] Ensure one active automation token row and one per-mode focus-state/mapping row per user through unique constraints and repository methods.
- [ ] Run the targeted JPA tests and iterate until the mappings and uniqueness rules behave correctly.

### Task 4: Pending auth store and crypto helpers

**Files:**
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/application/TelegramPendingAuthStore.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/store/RedisTelegramPendingAuthStore.kt`
- Create: `src/test/kotlin/me/topilov/springplayground/telegram/InMemoryTelegramPendingAuthStore.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/crypto/TelegramSessionSecretCrypto.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/crypto/AesGcmTelegramSessionSecretCrypto.kt`

- [ ] Implement a Redis-backed pending-auth store keyed by opaque pending-auth id with a short TTL and strict user ownership.
- [ ] Keep pending Telegram auth state fully inside the `telegram` domain, including auth step tracking and the session-directory reference.
- [ ] Add AES-GCM encryption for the TDLib database key using configured app-level key material, following the repo’s existing pattern for encrypted secrets.
- [ ] Add focused tests for pending-auth expiration/user binding and crypto round-trip behavior.

### Task 5: TDLight integration seam

**Files:**
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/tdlight/TelegramClientGateway.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/tdlight/TdlightTelegramClientGateway.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/infrastructure/tdlight/TelegramSessionDirectoryResolver.kt`
- Create: `src/test/kotlin/me/topilov/springplayground/telegram/FakeTelegramClientGateway.kt`

- [ ] Introduce a focused gateway interface that hides TDLight authorization-state handling and emoji-status updates from application services.
- [ ] Model gateway operations for starting auth, submitting code, submitting password, loading self-account info, and setting or clearing emoji status.
- [ ] Resolve per-user session directories from the configured root and persisted directory key.
- [ ] Implement a fake gateway for tests so service and endpoint behavior can be exercised without real Telegram I/O.
- [ ] Run the focused gateway and service tests and keep iterating until the fake and real adapter boundaries are stable.

### Task 6: Focus resolution, mapping, and token services

**Files:**
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramFocusPriorityResolver.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/domain/TelegramEmojiMappingResolver.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/application/TelegramAutomationTokenService.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/application/TelegramFocusSettingsService.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/application/TelegramFocusAutomationApplicationService.kt`

- [ ] Implement deterministic focus resolution with the fixed priority `sleep > do_not_disturb > reduce_interruptions > airplane > personal`.
- [ ] Implement default-mapping fallback plus user override resolution, including default no-focus behavior.
- [ ] Generate secure raw automation tokens, persist only their SHA-256 hashes, and expose raw tokens only from create/regenerate methods.
- [ ] Update token `last_used_at` on successful automation requests and ensure revoke/regenerate semantics invalidate the prior token immediately.
- [ ] Re-run the focused resolver and token lifecycle tests until green.

### Task 7: Telegram connection and summary application services

**Files:**
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/application/TelegramConnectionApplicationService.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/application/TelegramQueryService.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/domain/exception/...`

- [ ] Implement the authenticated connect/start, confirm-code, confirm-password, disconnect, and summary flows.
- [ ] Persist the durable Telegram account linkage only after successful completion of the TDLight auth flow.
- [ ] Capture the currently set Telegram emoji status as the initial default no-focus status when available; otherwise leave the default null so no-focus clears the status.
- [ ] Keep disconnect focused on severing Telegram access only; do not wipe stored focus preferences implicitly.
- [ ] Add and map stable exception codes for missing pending auth, invalid auth step, premium-required sync failures, invalid token, not-connected, and generic Telegram sync failures.
- [ ] Re-run the focused application-service tests after each slice and keep iterating until green.

### Task 8: HTTP DTOs, controllers, exception handling, and security

**Files:**
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/web/TelegramController.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/web/TelegramAutomationController.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/web/TelegramExceptionHandler.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/telegram/web/dto/...`
- Modify: `src/main/kotlin/me/topilov/springplayground/config/SecurityConfig.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/config/OpenApiConfig.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/protection/domain/ProtectionFlow.kt`
- Modify: `src/main/resources/application.yml`

- [ ] Add explicit DTOs for Telegram connect flow, focus settings, token lifecycle responses, and minimal automation requests/responses.
- [ ] Expose authenticated profile endpoints under `/api/profile/me/telegram`.
- [ ] Expose the minimal token-authenticated automation endpoint under `/api/telegram/focus-updates`.
- [ ] Permit the automation endpoint in Spring Security while keeping it protected by bearer-token validation rather than session auth.
- [ ] Add a dedicated protection flow and rate-limit policy for the automation endpoint with no captcha requirement.
- [ ] Re-run MockMvc integration tests until status codes, error codes, and response bodies match the written contract.

### Task 9: Contract, OpenAPI, and project docs

**Files:**
- Create: `docs/contracts/telegram.md`
- Modify: `docs/contracts/README.md`
- Modify: `openapi/openapi.yaml`
- Modify: `README.md`

- [ ] Document the Telegram connection flow, settings endpoints, automation-token management, minimal iOS Shortcut request shape, error codes, and no-focus behavior in `docs/contracts/telegram.md`.
- [ ] Update the contracts README so the new Telegram contract is discoverable.
- [ ] Update OpenAPI in the same task with the new schemas, security expectations, and example payloads.
- [ ] Update the main README with TDLight runtime requirements, session-directory expectations, and any local configuration notes needed for development.
- [ ] Mark the API change as non-breaking in the doc summary.

### Task 10: Broad verification

**Files:**
- No additional file targets; this is verification only.

- [ ] Run targeted Telegram tests during iteration and ensure each red-green cycle is observed before production code.
- [ ] Finish with `./gradlew test` as the broadest default backend verification.
- [ ] If the Telegram dependency or environment setup allows, finish with `./gradlew build`.
- [ ] If any broad verification cannot run, record the exact command, blocker, and whether the blocker is code-related or environment-related.
