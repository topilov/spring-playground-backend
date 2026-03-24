# Backend Architecture

## Summary

This repository is a pragmatic modular monolith. The primary organizing principle is domain ownership, not a repository-wide technical split.

## Top-Level Domains

- `auth`
  - owns registration, login, logout, email verification, password reset, two-factor auth, and passkeys
  - owns unified session establishment through `SessionLoginService`
- `profile`
  - owns authenticated profile and account-management behavior
- `protection`
  - owns captcha validation, request throttling, cooldowns, and stable protection errors
- `mail`
  - owns outbound email delivery and public URL building for mail-driven flows
- `publicapi`
  - owns public endpoints that are not better modeled inside another domain
- `common`
  - owns only genuinely cross-domain infrastructure and generic web primitives
- `config`
  - owns application-wide bootstrapping such as security, CORS, OpenAPI, and shared framework wiring

## Layering Approach

The codebase uses layering where it adds clarity:

- `web`
  - controllers, HTTP DTOs, and domain-owned exception handlers
- `application`
  - orchestration-heavy use cases
  - protection entry points
  - transaction and workflow coordination
  - session-establishing auth flows
- `domain`
  - entities and domain state
- `infrastructure`
  - external integrations, Redis stores, and domain-owned config or transport code

This is intentionally pragmatic:

- small query or CRUD services may stay compact without a dedicated use-case class
- not every domain needs every layer
- existing packages such as `repository`, `mapper`, `verification`, or `reset` may remain when they are already clear

## Application Layer Guidance

Use explicit application services when a flow coordinates multiple responsibilities. In this repository that especially applies to:

- password login
- two-factor login completion
- passkey login
- registration
- password reset
- email verification and resend flows
- profile email-change workflows

Do not add use-case classes just to satisfy a pattern. If a service is already small, focused, and easy to read, keep it simple.

## Session Establishment Rule

All successful interactive authentication flows should establish the session through `SessionLoginService`:

- password login
- two-factor login completion
- passkey login

This keeps session invalidation, principal construction, and response shape consistent across auth mechanisms.

## Boundaries To Protect

- Controllers stay thin and should not own business workflows.
- Cross-domain sharing should happen through explicit services or shared infrastructure, not by importing another domain's web DTOs or error models.
- `common` must not accumulate business helpers from `auth`, `profile`, or `protection`.
- Shared infrastructure such as `ObjectMapper` should come from Spring-managed beans, not repeated local instantiation.

## Intentional Compatibility Debt

- The `protection` domain still binds configuration from the `app.abuse` property prefix.
- This is intentional compatibility debt left in place to avoid an operational/config migration during the structural refactor.
- Do not rename that prefix casually. Treat it as a separate compatibility task requiring rollout planning and documentation updates.
