# Architecture Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate the backend refactor into durable architectural guidance and add explicit application/use-case boundaries only where they improve orchestration-heavy flows.

**Architecture:** Keep the codebase domain-oriented and pragmatic. Strengthen `AGENTS.md`, add a lightweight architecture note, and introduce application-layer entry points for session-establishing auth flows while leaving simple query and management paths compact.

**Tech Stack:** Kotlin, Spring Boot, Spring Security, Spring Data JPA, Spring Data Redis, Jackson, MockMvc, Testcontainers/PostgreSQL

---

### Task 1: Refresh repository guidance

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/reviews/backend-refactoring-review.md`
- Create: `docs/architecture/backend-architecture.md`

- [ ] Rewrite `AGENTS.md` around the current domain-oriented architecture and coding defaults.
- [ ] Add explicit anti-patterns, pragmatic exceptions, and a short review checklist.
- [ ] Document intentional compatibility debt such as the `app.abuse` configuration prefix.

### Task 2: Clarify auth application boundaries

**Files:**
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/web/TwoFactorController.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/passkey/web/PasskeyController.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/service/TwoFactorLoginService.kt`
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/passkey/service/PasskeyAuthenticationService.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/application/TwoFactorAuthenticationService.kt`
- Create: `src/main/kotlin/me/topilov/springplayground/auth/passkey/application/PasskeyAuthenticationService.kt`

- [ ] Move 2FA login orchestration into an auth application service that owns protection checks and session establishment.
- [ ] Move passkey login orchestration into a passkey application service that owns protection checks and session establishment.
- [ ] Keep lower-level verification and challenge mechanics in focused services.

### Task 3: Add focused regression coverage for the refined application layer

**Files:**
- Modify: `src/test/kotlin/me/topilov/springplayground/TwoFactorIntegrationTest.kt`
- Modify: `src/test/kotlin/me/topilov/springplayground/PasskeyIntegrationTest.kt`

- [ ] Add or adjust focused tests that pin the session-establishing auth flows while the orchestration layer is being moved.
- [ ] Use those tests to drive the refactor and confirm public behavior remains unchanged.

### Task 4: Verify the repository end to end

**Files:**
- Modify: none

- [ ] Run `./gradlew test`.
- [ ] Run `./gradlew build`.
- [ ] Report exact verification results and any blockers.
