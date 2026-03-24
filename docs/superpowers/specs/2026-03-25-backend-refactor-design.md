# Backend Refactor Design

## Summary

Refactor the backend toward a clearer pragmatic modular monolith without changing the HTTP contract unless a correction is clearly justified.

## Direction

- Keep the repository domain-oriented.
- Promote `abuse` into a clearer `protection` domain.
- Split overloaded application services into smaller flow-focused services.
- Keep controllers thin and DTO-driven.
- Inject shared infrastructure such as `ObjectMapper` instead of creating local instances.
- Break up oversized tests and multi-declaration source files.

## Package Strategy

- `auth`
  - `application`
  - `domain`
  - `infrastructure`
  - `web`
  - subdomains: `passkey`, `twofactor`
- `profile`
  - `application`
  - `domain`
  - `infrastructure`
  - `web`
- `protection`
  - `application`
  - `captcha`
  - `infrastructure`
  - `web`
- `mail`
- `publicapi`
- `common`

## Key Refactors

- Replace the current all-in-one Turnstile service with a small orchestration service plus dedicated client and validation logic.
- Split `AuthService` by use case: registration, verification, password reset, and session login/logout.
- Split `ProfileService` by responsibility: profile query/update, credential changes, and email-change flow.
- Extract shared integration-test support from the oversized security endpoint suite and replace it with focused test classes.

## Constraints

- Preserve current request/response/error shapes unless a change is explicitly justified and documented.
- Keep OpenAPI and markdown contracts in sync with any observable behavior changes.
- Favor fewer, clearer classes over abstraction-heavy layering.
