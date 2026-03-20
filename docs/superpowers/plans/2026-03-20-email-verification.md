# Email Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Redis-backed email verification so registration sends a confirmation email, login is blocked until verification succeeds, and users can request a new verification email.

**Architecture:** Persist `email_verified` on `auth_user`, store short-lived verification tokens in Redis, and extend the existing auth/mail flow with public `verify-email` and `resend-verification-email` endpoints. Keep tests, docs, and OpenAPI in sync with the new behavior.

**Tech Stack:** Kotlin, Spring Boot, Spring Security, Spring Data JPA, Spring Data Redis, MockMvc, Testcontainers PostgreSQL

---

### Task 1: Red tests for the new auth contract

**Files:**
- Modify: `src/test/kotlin/me/topilov/springplayground/SecurityEndpointsTest.kt`

- [ ] Add failing tests for registration, verification, resend, and blocked login.
- [ ] Run the targeted test class and confirm the failures describe the missing behavior.

### Task 2: Redis-backed verification flow

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-test.yml`
- Modify: `src/main/resources/db/migration/V1__create_auth_and_profile_tables.sql` or add a new migration for `email_verified`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/domain/AuthUser.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/service/AuthService.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/security/AppUserDetailsService.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/config/SecurityConfig.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/dto/VerifyEmailRequest.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/dto/VerifyEmailResponse.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/dto/ResendVerificationEmailRequest.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/dto/AcceptedResponse.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/exception/EmailNotVerifiedException.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/exception/InvalidEmailVerificationTokenException.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/verification/EmailVerificationTokenStore.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/verification/RedisEmailVerificationTokenStore.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/web/AuthController.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/web/AuthExceptionHandler.kt`

- [ ] Implement the minimal production code to satisfy the failing tests.
- [ ] Run the targeted test class and keep iterating until it passes.

### Task 3: Email templates and delivery

**Files:**
- Modify: `src/main/kotlin/me/topilov/springplayground/mail/EmailService.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/mail/MailProperties.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/mail/ThymeleafEmailService.kt`
- Create: `src/main/resources/templates/mail/verify-email.html`

- [ ] Add verification email delivery and template with a visible confirm button link.
- [ ] Re-run the auth integration tests to confirm the email content works with token extraction.

### Task 4: Contract documentation

**Files:**
- Modify: `docs/contracts/auth.md`
- Modify: `openapi/openapi.yaml`

- [ ] Update the human-readable and machine-readable contracts, including examples and the breaking login behavior.
- [ ] Re-run the API/auth tests that cover OpenAPI exposure.
