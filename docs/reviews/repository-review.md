# Repository Review

## Executive Summary

This repository is compact, understandable, and already follows a sensible modular-monolith shape for a small backend: controllers are thin, services hold the business logic, DTOs are explicit, Flyway owns the schema, and the HTTP contract is documented in both markdown and OpenAPI.

The highest-value issues are around production hardening rather than basic structure. The most notable remaining risk is that the application uses session cookies while CSRF protection is disabled, which is not a safe default for a browser-facing system. There is also a readiness risk from building on milestone-era infrastructure (`Spring Boot 4.1.0-M3`, Java 25) rather than a stable LTS stack.

This review pass applied a few non-breaking, high-confidence improvements directly:

- Marked session cookies `Secure` by default outside `local` and `test`.
- Added database-level case-insensitive uniqueness enforcement for usernames and emails.
- Added defensive duplicate-key translation in registration so DB-enforced conflicts still map to `409 Conflict`.
- Tightened OpenAPI and auth contract docs so they no longer advertise phantom error responses.

Change classification for the applied fixes: `Non-breaking`.

## What Is Already Good

- Package boundaries are simple and readable: `auth`, `profile`, `publicapi`, `config`, `mail`, `shared`.
- Controllers stay narrow and mostly delegate to services.
- Contract ownership is clear and reinforced by repo docs.
- JPA usage is restrained and understandable.
- Flyway is in charge of schema creation and seeded demo data.
- Tests cover the main happy-path auth and profile flows and exercise the HTTP layer rather than only unit logic.
- OpenAPI and markdown contract docs already exist, which is a strong habit for backend-owned APIs.

## Critical Issues

- CSRF is disabled while authentication is session-cookie based in [SecurityConfig.kt](/Users/topilov/.codex/worktrees/31b2/spring-playground/src/main/kotlin/me/topilov/springplayground/config/SecurityConfig.kt#L28). That is an unsafe production posture for browser clients because authenticated cross-site state-changing requests are not protected.

## Important Issues

- The build targets a milestone stack in [build.gradle.kts](/Users/topilov/.codex/worktrees/31b2/spring-playground/build.gradle.kts#L4) and [build.gradle.kts](/Users/topilov/.codex/worktrees/31b2/spring-playground/build.gradle.kts#L16). `Spring Boot 4.1.0-M3` plus Java 25 increases upgrade and ecosystem risk versus a stable Boot/LTS Java baseline.
- Full verification currently depends on Docker-backed Testcontainers. In environments without Docker access, the integration suite cannot execute, which weakens developer ergonomics and CI portability.
- Error handling is still mixed between framework-default bodies (`ApiErrorResponse`) and application-defined bodies (`SimpleErrorResponse`). The contract is clearer than before, but the API surface is not yet fully standardized.
- Session lifecycle hardening is still limited. Password reset invalidates reset tokens, but existing authenticated sessions are not tracked and revoked.

## Nice-To-Have Improvements

- Add a focused contract regression test that exports or snapshots the OpenAPI document so committed `openapi/openapi.yaml` drift is caught automatically.
- Consider validating configuration properties such as mail sender and public base URL at startup for faster misconfiguration feedback.
- Reduce repeated MockMvc setup across integration tests with a shared test helper or base class if the test suite grows further.

## Architectural Observations

- The current module boundaries are appropriate for the repository size. There is no immediate need for extra abstraction layers or more modules.
- `mail` is cleanly separated behind `EmailService`, which keeps auth flows from depending directly on template/rendering details.
- `shared` is still intentionally small, which is healthy. Avoid turning it into a dumping ground as the project grows.

## Security Observations

- Applied now: non-local session cookies are `Secure` by default via [application.yml](/Users/topilov/.codex/worktrees/31b2/spring-playground/src/main/resources/application.yml#L39), with local/test overrides in [application-local.yml](/Users/topilov/.codex/worktrees/31b2/spring-playground/src/main/resources/application-local.yml#L6) and [application-test.yml](/Users/topilov/.codex/worktrees/31b2/spring-playground/src/main/resources/application-test.yml#L15).
- Applied now: case-insensitive uniqueness is now enforced in the database through [V5__enforce_case_insensitive_auth_identity_uniqueness.sql](/Users/topilov/.codex/worktrees/31b2/spring-playground/src/main/resources/db/migration/V5__enforce_case_insensitive_auth_identity_uniqueness.sql), closing an integrity gap between service checks and stored data.
- Applied now: registration translates database duplicate-key conflicts back into domain `409` responses in [AuthService.kt](/Users/topilov/.codex/worktrees/31b2/spring-playground/src/main/kotlin/me/topilov/springplayground/auth/service/AuthService.kt#L64), which is better than leaking a storage exception as a server error.
- Remaining: CSRF is the main unresolved security gap.

## Testing Observations

- Existing tests cover core auth/profile flows well enough for a small project.
- Added tests in [SecurityEndpointsTest.kt](/Users/topilov/.codex/worktrees/31b2/spring-playground/src/test/kotlin/me/topilov/springplayground/SecurityEndpointsTest.kt) for two valuable regressions:
  - OpenAPI should not advertise nonexistent `400`/`409` responses on unaffected endpoints.
  - The database should reject case-variant duplicate identities.
- The suite is still heavily integration-oriented. That is acceptable here, but it makes Docker availability a hard prerequisite.

## Recommended Next Steps

1. Enable CSRF protection in a frontend-compatible way and update the contract docs at the same time.
2. Decide whether this project should stay on milestone-era dependencies or move to a stable Spring Boot + LTS Java baseline before production use.
3. Standardize error response shapes across validation, auth, and domain failures to reduce client ambiguity.
4. Add an automated OpenAPI export/check step so the committed schema cannot silently drift from the runtime document.
