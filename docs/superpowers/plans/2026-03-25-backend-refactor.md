# Backend Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reshape the backend into a cleaner pragmatic modular monolith with clearer domain boundaries, quieter protection code, focused application services, shared infrastructure wiring, and maintainable integration tests.

**Architecture:** Keep the existing domain orientation, but make internal layering more explicit where it pays off. Move the current `abuse` slice into a `protection` domain, split large orchestration services by flow, keep controllers thin, and extract shared test support so the HTTP contract remains stable while the implementation gets easier to navigate.

**Tech Stack:** Kotlin, Spring Boot, Spring Security, Spring Data JPA, Spring Data Redis, Jackson, MockMvc, Testcontainers/PostgreSQL

---

### Task 1: Refactor the protection domain and Turnstile integration

**Files:**
- Modify: `src/main/kotlin/me/topilov/springplayground/abuse/**`
- Create: `src/main/kotlin/me/topilov/springplayground/protection/**`
- Modify: `src/test/kotlin/me/topilov/springplayground/abuse/**`

- [ ] Move the protection code into a `protection` domain with clearer package names.
- [ ] Split Turnstile verification into orchestration, HTTP client, and validation concerns.
- [ ] Replace local `ObjectMapper` creation with injected infrastructure.
- [ ] Update focused protection tests to match the quieter logging strategy.

### Task 2: Split overloaded auth and profile application services

**Files:**
- Modify: `src/main/kotlin/me/topilov/springplayground/auth/**`
- Modify: `src/main/kotlin/me/topilov/springplayground/profile/**`

- [ ] Replace `AuthService` with smaller use-case-oriented services.
- [ ] Replace `ProfileService` with smaller responsibility-focused services.
- [ ] Keep controller behavior stable while making dependencies more explicit.
- [ ] Split multi-declaration interfaces/data-holder files where navigation suffers today.

### Task 3: Standardize shared infrastructure usage

**Files:**
- Modify: `src/main/kotlin/me/topilov/springplayground/**`
- Modify: `src/test/kotlin/me/topilov/springplayground/**`

- [ ] Inject Spring-managed `ObjectMapper` wherever application code currently creates its own mapper.
- [ ] Move domain-owned configuration closer to the owning package where practical.
- [ ] Reduce small cross-module leakage in exception and support types.

### Task 4: Split the oversized integration suite

**Files:**
- Modify: `src/test/kotlin/me/topilov/springplayground/SecurityEndpointsTest.kt`
- Create: `src/test/kotlin/me/topilov/springplayground/support/**`
- Create: `src/test/kotlin/me/topilov/springplayground/**/*IntegrationTest.kt`

- [ ] Extract shared MockMvc/setup/helpers into dedicated integration-test support.
- [ ] Split the security endpoint suite into feature-focused classes.
- [ ] Preserve coverage for auth, two-factor, passkey, protection, and profile flows.

### Task 5: Document the review and verify the result

**Files:**
- Create: `docs/reviews/backend-refactoring-review.md`
- Modify: `README.md`
- Modify: `docs/contracts/*`
- Modify: `openapi/openapi.yaml`

- [ ] Document the main issues found, the changes applied, recurring anti-patterns, and recommended AGENTS rules.
- [ ] Update repo docs only where refactoring changes observable behavior or documentation drift is discovered.
- [ ] Run the broadest relevant verification commands and report any environment blockers precisely.
