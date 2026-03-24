# Abuse Protection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add centralized, production-grade abuse protection for sensitive public auth and recovery flows with Turnstile validation, Redis-backed throttling/cooldowns, stable error responses, and updated docs/tests.

**Architecture:** Introduce a dedicated `abuse` module that owns captcha verification, policy-driven throttling, cooldown state, and stable protection errors. Public auth/security services call a single orchestration service with typed flow identifiers so rules stay centralized, reusable, and consistent across login, recovery, 2FA login, passkey login, and public verification flows.

**Tech Stack:** Kotlin, Spring Boot Web/Security, Spring Validation, Spring Data Redis, MockMvc, Testcontainers/PostgreSQL, Cloudflare Turnstile Siteverify

---

### Task 1: Add failing tests for protection error contracts and public flow DTO changes

**Files:**
- Modify: `src/test/kotlin/me/topilov/springplayground/SecurityEndpointsTest.kt`
- Modify: `src/test/kotlin/me/topilov/springplayground/auth/TestEmailVerificationConfiguration.kt`
- Modify: `src/test/kotlin/me/topilov/springplayground/auth/TestPasswordResetConfiguration.kt`
- Create: `src/test/kotlin/me/topilov/springplayground/abuse/TestAbuseProtectionConfiguration.kt`

- [ ] **Step 1: Write failing integration tests for missing/invalid captcha on protected public flows**
- [ ] **Step 2: Write failing integration tests for login throttling, 2FA throttling, forgot-password cooldown, and resend-verification cooldown**
- [ ] **Step 3: Write failing integration tests that prove forgot-password and resend-verification stay enumeration-safe under suppression/cooldown**
- [ ] **Step 4: Run the targeted tests and confirm they fail for the intended missing behavior**

### Task 2: Add abuse configuration models and stable protection DTOs

**Files:**
- Create: `src/main/kotlin/me/topilov/springplayground/abuse/config/TurnstileProperties.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/abuse/config/AbuseProtectionProperties.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/abuse/dto/ProtectionErrorResponse.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/abuse/exception/CaptchaValidationFailedException.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/abuse/exception/RateLimitExceededException.kt`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-local.yml`
- Modify: `src/main/resources/application-test.yml`

- [ ] **Step 1: Add typed flow policy configuration with per-flow windows, limits, and cooldown durations**
- [ ] **Step 2: Add Turnstile configuration including enable flag, secret, endpoint, hostname, timeout, and action mapping defaults**
- [ ] **Step 3: Add stable error DTO and exception types with retry-after support**
- [ ] **Step 4: Run the targeted tests and confirm config/error-contract tests still fail only on missing behavior**

### Task 3: Add Redis-backed abuse state stores and Turnstile verification service

**Files:**
- Create: `src/main/kotlin/me/topilov/springplayground/abuse/store/RateLimitStore.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/abuse/store/CooldownStore.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/abuse/store/RedisRateLimitStore.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/abuse/store/RedisCooldownStore.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/abuse/captcha/CaptchaVerificationService.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/abuse/captcha/TurnstileCaptchaVerificationService.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/abuse/captcha/TurnstileSiteverifyModels.kt`
- Create: `src/test/kotlin/me/topilov/springplayground/abuse/RedisRateLimitStoreTest.kt`
- Create: `src/test/kotlin/me/topilov/springplayground/abuse/RedisCooldownStoreTest.kt`
- Create: `src/test/kotlin/me/topilov/springplayground/abuse/TurnstileCaptchaVerificationServiceTest.kt`

- [ ] **Step 1: Write failing store/service tests for counter increments, TTL behavior, cooldown TTL lookup, and captcha response mapping**
- [ ] **Step 2: Implement Redis stores with explicit keys and TTL handling**
- [ ] **Step 3: Implement Turnstile Siteverify integration with request timeout, remote IP forwarding, action validation, hostname validation, and safe failure handling**
- [ ] **Step 4: Run the focused tests and make them pass**

### Task 4: Add centralized orchestration service and wire it into sensitive public flows

**Files:**
- Create: `src/main/kotlin/me/topilov/springplayground/abuse/AbuseProtectionFlow.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/abuse/AbuseProtectionContext.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/abuse/AbuseProtectionService.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/service/AuthService.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/service/TwoFactorLoginService.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/passkey/service/PasskeyAuthenticationService.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/profile/service/ProfileService.kt`

- [ ] **Step 1: Write failing service-level behavior through existing integration tests for each protected flow**
- [ ] **Step 2: Implement policy-driven orchestration for captcha checks, rate limits, brute-force counters, and enumeration-safe cooldown suppression**
- [ ] **Step 3: Keep login throttling and 2FA throttling as separate flow domains with independent Redis keys and policies**
- [ ] **Step 4: Run the targeted tests and make them pass**

### Task 5: Update request DTOs, controllers, exception handling, and OpenAPI annotations

**Files:**
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/dto/RegisterRequest.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/dto/LoginRequest.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/dto/ForgotPasswordRequest.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/dto/ResendVerificationEmailRequest.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/dto/ResetPasswordRequest.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/dto/TwoFactorLoginVerifyRequest.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/dto/TwoFactorBackupCodeLoginVerifyRequest.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/passkey/dto/PasskeyLoginOptionsRequest.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/passkey/dto/PasskeyLoginVerifyRequest.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/profile/dto/VerifyEmailChangeRequest.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/web/AuthController.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/web/TwoFactorController.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/passkey/web/PasskeyController.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/web/AuthExceptionHandler.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/profile/web/ProfileExceptionHandler.kt`

- [ ] **Step 1: Add required captcha token fields to protected public request DTOs**
- [ ] **Step 2: Keep controllers thin by passing request metadata into the abuse orchestration layer**
- [ ] **Step 3: Add consistent 400/429 protection responses and `Retry-After` header support**
- [ ] **Step 4: Run the integration tests and make them pass**

### Task 6: Update contract docs and machine-readable API contract

**Files:**
- Modify: `docs/contracts/auth.md`
- Modify: `docs/contracts/auth-api.md`
- Modify: `docs/contracts/profile.md`
- Modify: `openapi/openapi.yaml`
- Modify: `README.md`

- [ ] **Step 1: Document new required captcha request fields and new protection error responses**
- [ ] **Step 2: Document enumeration-safe cooldown behavior for forgot-password and resend-verification**
- [ ] **Step 3: Mark the contract change as `Breaking` because protected request DTOs now require captcha tokens**
- [ ] **Step 4: Verify docs/examples match the implemented behavior**

### Task 7: Broad verification

**Files:**
- Modify as needed based on test/doc feedback from previous tasks

- [ ] **Step 1: Run focused abuse-related tests during iteration until green**
- [ ] **Step 2: Run `./gradlew test`**
- [ ] **Step 3: If practical in the environment, verify runtime OpenAPI and sensitive endpoints against a running app**
- [ ] **Step 4: Summarize exactly what was verified and any environment blockers**
