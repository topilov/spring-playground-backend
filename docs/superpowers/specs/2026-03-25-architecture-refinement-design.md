# Architecture Refinement Design

## Summary

Strengthen the post-refactor backend architecture so future changes preserve the current domain-oriented shape without pushing the codebase into ceremony-heavy layering.

## Direction

- Keep the top-level structure domain-oriented: `auth`, `profile`, `protection`, `mail`, `publicapi`, `common`.
- Use `application` as the default home for orchestration-heavy flows that coordinate transport concerns, protection rules, challenge state, transactions, and session establishment.
- Keep simple CRUD/query paths lightweight when an existing service is already small and readable.
- Preserve unified session establishment through `SessionLoginService` for password login, passkey login, and 2FA completion.

## Architectural Decisions

### Repository guidance

- Rewrite `AGENTS.md` so it describes the current target architecture instead of the pre-refactor shape.
- Encode concrete defaults for package placement, file boundaries, infrastructure wiring, test size, and review expectations.
- Include pragmatic exceptions so contributors can avoid unnecessary use-case wrappers for trivial paths.

### Application layer refinement

- Keep the current `auth/application` services as the pattern for pragmatic use-case classes.
- Introduce application-layer entry points for:
  - 2FA login completion
  - passkey login start/finish
- Keep `PasskeyManagementService`, `ProfileQueryService`, and similar compact services lightweight.
- Leave lower-level mechanics in focused services:
  - challenge creation/consumption
  - TOTP or passkey verification
  - credential persistence
  - session creation

### Documentation

- Add an architecture note that explains the intended layering and the role of each top-level domain.
- Document intentional debt that remains for compatibility, especially the `app.abuse` configuration prefix that still backs the `protection` domain.

## Constraints

- Preserve public HTTP behavior unless a change is clearly justified.
- Do not introduce ports/adapters or use-case wrappers for straightforward getter/setter style code.
- Do not let `common` accumulate domain-specific helpers.
- Keep the result production-oriented and easy to navigate.
