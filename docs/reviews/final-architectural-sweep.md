# Final Architectural Sweep

## Summary

This sweep checked the repository against the current architectural guidance in `AGENTS.md` and `docs/architecture/backend-architecture.md` after the recent structural refactor and follow-up application-layer refinement.

The codebase is now in a good place for future feature work. The domain-first modular-monolith shape is visible, the `protection` domain remains coherent, session-establishing auth flows are consistently coordinated through application services, and `common` has stayed narrow.

This pass applied only small, high-confidence cleanups.

Change classification: `Non-breaking`.

## What Was Inspected

- `AGENTS.md`
- `docs/architecture/backend-architecture.md`
- `docs/reviews/backend-refactoring-review.md`
- top-level production packages:
  - `auth`
  - `profile`
  - `protection`
  - `common`
  - `config`
  - `mail`
  - `publicapi`
- auth-adjacent flows with extra focus on:
  - passkey login and registration
  - two-factor login and setup
  - protection/captcha/rate-limit orchestration
- current integration and unit test layout

## What Is Structurally Sound

### Domain-first shape is holding

- The repository still reads as domain-oriented rather than globally layered.
- `auth`, `profile`, and `protection` are the primary business domains, with `mail`, `publicapi`, and `common` playing clear supporting roles.
- `common` is still narrow and has not turned into a convenience dumping ground.

### Application layer is being used selectively and correctly

- Password login orchestration lives in `SessionAuthenticationService`.
- 2FA login completion now lives in `TwoFactorLoginApplicationService`.
- Passkey login now lives in `PasskeyLoginApplicationService`.
- Session establishment remains unified through `SessionLoginService`.

### Protection domain remains coherent

- Captcha transport, response validation, and orchestration remain separated.
- Protection throttling and cooldown logic remain centralized in `ProtectionService`.
- Protection-specific errors stay owned by the protection domain.

### Controllers are thin enough

- `AuthController`, `TwoFactorController`, `PasskeyController`, and `ProfileController` are annotation-heavy, but the actual transport logic is still thin.
- There is no meaningful business orchestration left in controllers that obviously needs to move inward now.

### Test structure is materially improved

- The former giant security suite remains split into feature-oriented integration tests.
- New application-service tests exist for the latest auth orchestration seams.

## Small Fixes Applied In This Pass

### Removed leftover top-level `shared` package usage

- Moved cross-domain web error schema DTOs into `common.web`:
  - `ApiErrorResponse`
  - `SimpleErrorResponse`
- Updated controller and exception-handler imports accordingly.

Why this was worth fixing:

- `shared` no longer fit the documented architecture direction.
- These classes are cross-domain web primitives, so `common.web` is their natural home.
- This was a small consistency cleanup without changing API behavior.

### Clarified written architecture around `config`

- Updated `AGENTS.md` and `backend-architecture.md` to explicitly acknowledge the top-level `config` package.

Why this was worth fixing:

- The code already had a legitimate cross-cutting `config` package.
- The docs were slightly behind the actual repository shape.
- Clarifying that boundary reduces future accidental drift.

## Remaining Architectural Seams

### Passkey registration is still orchestration-heavy inside a `service` package

- `PasskeyRegistrationService` coordinates user lookup, WebAuthn registration, ceremony persistence, credential persistence, and response shaping.
- This is somewhat application-like, but it is still self-contained inside the passkey domain and does not currently coordinate protection or session establishment.
- It is acceptable to leave in place for now.

Watch for:

- additional cross-domain dependencies
- more branching or policy
- more controller-specific orchestration concerns

If that happens, promote it into a dedicated passkey application service.

### Two-factor setup/management remains in `TwoFactorManagementService`

- `TwoFactorManagementService` still owns setup/start/confirm/regenerate/disable orchestration.
- The class is coherent and focused on one subdomain, so it is acceptable today.
- If setup flows gain more protection, mail, audit, or session-related coordination, an explicit application boundary may become worthwhile.

### Some focused integration suites are still sizable

- `AuthRecoveryIntegrationTest`
- `PasskeyIntegrationTest`
- `TwoFactorIntegrationTest`

These are no longer giant multi-domain blobs, which is the important improvement. They are still feature-focused and readable enough today, but they should be split again if new subflows continue to accumulate.

### OpenAPI-heavy controllers remain large by line count

- `AuthController` and `ProfileController` are still large files because they carry substantial OpenAPI annotations.
- This is not an immediate architecture problem because transport logic remains thin.
- If readability degrades further, extract shared response schema constants or documentation helpers rather than moving business logic.

## Deferred Issues / Intentional Debts

- The `protection` domain still binds configuration from the `app.abuse` property prefix for compatibility.
- This remains intentional compatibility debt and should be handled only as an explicit migration task.

- `TurnstileSiteverifyClient` still owns its own `HttpClient` instance inside the adapter.
- This is acceptable today because it is a focused domain-owned external client, not a repeated local per-request instantiation pattern.
- If the repository grows more outbound HTTP integrations, consider introducing shared HTTP client configuration.

## Recommended Follow-Up Rules For `AGENTS.md`

No major new rules were needed beyond the clarifications applied in this pass.

The only practical guidance reinforced by this sweep is:

- Prefer `common.web` for genuinely shared HTTP schema/support types rather than reviving a vague top-level `shared` package.
- Treat `config` as application-wide framework wiring only, not as overflow storage for business logic.

## Final Assessment

The repository now matches the intended architecture closely enough that future work can proceed without another structural rewrite.

The remaining seams are mild and understandable:

- mostly packaging/ownership watchpoints
- not hidden behavioral risk
- not signs that the refactor failed

The codebase feels ready for future feature work as long as contributors continue following `AGENTS.md` and keep the application layer selective and pragmatic.
