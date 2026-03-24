# Backend Refactoring Review

## Summary

This pass refactored the backend toward a cleaner modular-monolith shape with clearer domain boundaries, thinner controllers, and more predictable file structure. The work stayed intentionally pragmatic: behavior was preserved unless a structural change clearly improved maintainability or consistency.

Change classification: `Non-breaking`.

Public HTTP contracts were not intentionally changed in this pass, so `docs/contracts/*` and `openapi/openapi.yaml` did not require shape updates.

## Key Problems Found

### Weak domain boundaries

- Business flows were starting to spread across controllers, service classes, and cross-cutting helpers without a clear domain-oriented entry point.
- The old `abuse` area bundled rate limiting, cooldown, and captcha concerns under a vague package name that did not read as a coherent module.

### Overgrown and noisy captcha implementation

- `TurnstileCaptchaVerificationService` mixed transport, parsing, validation, and logging decisions in one place.
- Logging was too verbose for a production path that is expected to reject invalid requests routinely.

### Unpredictable file boundaries

- Several files contained multiple unrelated helper/value classes, which made navigation harder and reduced discoverability.
- Test support and fake implementations were drifting into combined files instead of having obvious single responsibilities.

### Oversized integration tests

- `SecurityEndpointsTest` had grown into a multi-flow integration blob. That made failures slower to triage and increased the cost of maintaining auth-related changes.

### Repeated local infrastructure creation

- JSON serialization logic relied on repeated local mapper creation instead of a shared application-level bean.
- That pattern risks configuration drift between production paths and test paths.

### Inconsistent cross-module error handling

- Error DTO ownership was not clearly centralized, and profile code was depending on auth web error types.

## Changes Applied

### Domain-oriented structure

- Introduced a first-class `protection` domain for captcha, cooldown, and rate limiting.
- Moved request protection orchestration into [`ProtectionService`](/Users/topilov/.codex/worktrees/b543/spring-playground/src/main/kotlin/me/topilov/springplayground/protection/application/ProtectionService.kt).
- Kept the design pragmatic: domains are visible at the top level, while technical layering is used only inside a domain where it improves clarity.

### Turnstile refactor

- Split the Turnstile flow into distinct responsibilities:
  - HTTP exchange in [`TurnstileSiteverifyClient`](/Users/topilov/.codex/worktrees/b543/spring-playground/src/main/kotlin/me/topilov/springplayground/protection/captcha/TurnstileSiteverifyClient.kt)
  - validation rules in [`TurnstileResponseValidator`](/Users/topilov/.codex/worktrees/b543/spring-playground/src/main/kotlin/me/topilov/springplayground/protection/captcha/TurnstileResponseValidator.kt)
  - orchestration in [`TurnstileCaptchaVerificationService`](/Users/topilov/.codex/worktrees/b543/spring-playground/src/main/kotlin/me/topilov/springplayground/protection/captcha/TurnstileCaptchaVerificationService.kt)
- Reduced logging to concise production-level warnings for actual rejection and transport failure cases.
- Normalized invalid-token handling early to keep control flow direct and readable.

### Thinner controllers and clearer application services

- Extracted orchestration out of controllers into focused application services for auth and profile flows.
- Added dedicated services for registration, email verification, password reset, session login, profile query/update, and email change.
- Controllers now mostly translate HTTP requests into application calls and return DTOs.

### Shared infrastructure via DI

- Added shared mapper configuration in [`ObjectMapperConfiguration`](/Users/topilov/.codex/worktrees/b543/spring-playground/src/main/kotlin/me/topilov/springplayground/common/infrastructure/config/ObjectMapperConfiguration.kt).
- Replaced repeated local mapper creation with injected `ObjectMapper` dependencies in Redis-backed stores, passkey services, and captcha integration code.

### One main class per file cleanup

- Split multi-declaration files so result types, ceremony state objects, and pending-token models live in separately named files.
- Test doubles were also split into named files to make test setup easier to understand.

### Test suite refactor

- Replaced the oversized `SecurityEndpointsTest` with smaller integration suites grouped by responsibility:
  - [`PublicAndSessionIntegrationTest`](/Users/topilov/.codex/worktrees/b543/spring-playground/src/test/kotlin/me/topilov/springplayground/PublicAndSessionIntegrationTest.kt)
  - [`TwoFactorIntegrationTest`](/Users/topilov/.codex/worktrees/b543/spring-playground/src/test/kotlin/me/topilov/springplayground/TwoFactorIntegrationTest.kt)
  - [`PasskeyIntegrationTest`](/Users/topilov/.codex/worktrees/b543/spring-playground/src/test/kotlin/me/topilov/springplayground/PasskeyIntegrationTest.kt)
  - [`AuthRecoveryIntegrationTest`](/Users/topilov/.codex/worktrees/b543/spring-playground/src/test/kotlin/me/topilov/springplayground/AuthRecoveryIntegrationTest.kt)
  - [`ProfileAccountIntegrationTest`](/Users/topilov/.codex/worktrees/b543/spring-playground/src/test/kotlin/me/topilov/springplayground/ProfileAccountIntegrationTest.kt)
- Added shared test support in [`SecurityIntegrationTestSupport`](/Users/topilov/.codex/worktrees/b543/spring-playground/src/test/kotlin/me/topilov/springplayground/support/SecurityIntegrationTestSupport.kt) to reduce duplicated bootstrapping.
- Added focused protection-domain tests and separate test doubles for captcha/rate-limit/cooldown behavior.

### Error handling cleanup

- Added a shared error DTO in [`ErrorResponse`](/Users/topilov/.codex/worktrees/b543/spring-playground/src/main/kotlin/me/topilov/springplayground/common/web/ErrorResponse.kt).
- Moved protection-specific exception handling into [`ProtectionExceptionHandler`](/Users/topilov/.codex/worktrees/b543/spring-playground/src/main/kotlin/me/topilov/springplayground/protection/web/ProtectionExceptionHandler.kt) instead of leaking it through auth web concerns.

## Recurring Anti-Patterns

- Multiple helper/value classes placed in a single source file without a strong reason.
- Large integration test classes that accumulate unrelated flows over time.
- Logging every branch of a validation flow rather than logging only actionable production events.
- Local creation of infrastructure objects such as `ObjectMapper` inside application services and stores.
- Cross-module coupling through web-layer DTOs and error models.
- Vague package naming such as `abuse` for concerns that are actually part of a clearer protection/security boundary.

## Recommendations For Future `AGENTS.md` Rules

- Prefer top-level domain packages first. Only add intra-domain `application`, `domain`, `infrastructure`, or `web` layers when they improve readability.
- Keep one main class or one closely related declaration per file. Small sealed hierarchies are acceptable; mixed helpers are not.
- Do not create local `ObjectMapper`, `WebClient`, or other infrastructure instances inside business services when a shared bean should own configuration.
- Split integration tests by user flow or feature once a test file starts covering multiple unrelated endpoints or behaviors.
- Keep controllers transport-focused. Validation and orchestration should live in application services.
- For external integrations, separate transport, response mapping, and business validation instead of combining them in one service.
- Treat logging volume as a production concern. Avoid request-path debug narration in normal validation flows.
- Add new shared utilities to a clear module such as `common` only when they are genuinely cross-domain, not merely convenient.

## Notes On Remaining Intentional Pragmatism

- This refactor did not force every existing auth/profile package into a fully academic DDD layout. Repository, DTO, and mapper packages still exist where they remain clear and low-friction.
- No extra ports/adapters layer was introduced for straightforward internal flows.
- No public API contract changes were made purely for internal cleanup.

## Follow-Up Refinement

The next architecture pass added two more durable guardrails:

- Repository guidance in `AGENTS.md` now reflects the actual post-refactor architecture, including anti-patterns, package defaults, pragmatic exceptions, and a review checklist.
- Session-establishing auth flows now follow the same application-layer pattern:
  - password login through `SessionAuthenticationService`
  - 2FA completion through `TwoFactorLoginApplicationService`
  - passkey login through `PasskeyLoginApplicationService`

Intentional debt still left in place:

- The `protection` domain continues to bind configuration from the `app.abuse` prefix for compatibility. This should be treated as a separate migration task, not incidental cleanup.
