# AGENTS.md

This repository is the backend source of truth for the HTTP API contract and for the server-side architecture behind that contract. Codex and human contributors should use this file as the default guidance for future work.

## Repository Role

- This is the backend repository.
- Backend-owned HTTP contracts live in:
  - `docs/contracts/*` for human-readable docs
  - `openapi/openapi.yaml` and runtime `/v3/api-docs` for the machine-readable contract
- The separate frontend repository must consume these docs and must not invent request, response, error, or auth shapes independently.

## Architectural Direction

- Kotlin + Spring Boot pragmatic modular monolith.
- Organize code by domain first, not by global technical layer.
- Current top-level domains:
  - `auth`
  - `profile`
  - `protection`
  - `mail`
  - `publicapi`
  - `common`
- Cross-cutting app wiring may also live in:
  - `config`
- `auth` owns password login, session establishment, email verification, password reset, two-factor flows, and passkey flows.
- `profile` owns authenticated profile and account-management flows.
- `protection` owns captcha verification, cooldowns, request throttling, and stable protection errors.
- `mail` owns outbound email delivery and public URL construction for email links.
- `publicapi` owns unauthenticated public endpoints that do not belong to another domain.
- `common` is for truly cross-domain primitives only. It must not become a dumping ground for convenience helpers.
- `config` is for application-wide bootstrapping such as security, CORS, OpenAPI, and shared framework configuration. It should not absorb domain business logic.

## Target Package Shape

Use these packages when they add clarity:

- `web`
  - controllers
  - request/response DTOs when a domain already keeps HTTP models nearby
  - exception handlers owned by that domain
- `application`
  - orchestration-heavy use cases
  - transaction and workflow coordination
  - session establishment coordination
  - protection/rate-limit entry points into domain services
- `domain`
  - entities
  - domain state
  - domain-specific invariants that are not transport concerns
- `infrastructure`
  - persistence adapters
  - Redis stores
  - external HTTP clients
  - domain-owned config classes when the config is specific to that domain

Pragmatic note:
- Not every domain needs every layer.
- Simple domains may only need `web`, `domain`, and `repository`.
- Existing `repository`, `mapper`, `reset`, `verification`, or `passkey` subpackages may stay in place when they are already clear and low-friction.

## Class Responsibility Defaults

- Controllers should be thin.
- Controllers translate HTTP into application calls and return DTOs.
- Controllers should not contain multi-step business workflows.
- Application services should own orchestration-heavy flows.
- Application services may depend on repositories, focused domain services, protection, mail, and session services.
- Domain or lower-level services should stay focused on one responsibility:
  - challenge creation and consumption
  - passkey ceremony persistence
  - TOTP verification
  - backup code generation or consumption
  - session creation
  - external API transport
- Infrastructure classes should not make business-policy decisions unless that policy is intrinsic to the adapter itself.
- DTOs should stay explicit. Do not leak JPA entities or internal models through web responses.

## Application Layer Rules

- Add an `application` or use-case class when a flow coordinates multiple concerns, especially:
  - HTTP request inputs
  - protection checks
  - transaction boundaries
  - challenge state
  - session establishment
  - mail sending
  - cross-domain orchestration
- Keep session establishment unified through `SessionLoginService` for:
  - password login
  - 2FA login completion
  - passkey login
- Prefer focused application services such as:
  - registration
  - email verification
  - password reset
  - session authentication
  - two-factor login completion
  - passkey login
- Do not create use-case wrappers for trivial getters, simple queries, or straightforward one-repository CRUD unless the wrapper adds real clarity.

## File Boundaries

- Keep files predictable and focused.
- Prefer one main class per file.
- Small closely related declarations are acceptable when they clearly belong together, but avoid mixed helper bags.
- Avoid multiple unrelated helper classes or data holders in one file.
- If a file becomes hard to scan or contains multiple responsibilities, split it while you are working in that area.

## Testing Rules

- Avoid oversized test classes.
- Split integration tests by feature or flow once a test file covers multiple unrelated behaviors.
- Shared integration test setup belongs in dedicated support classes, not in giant endpoint suites.
- When changing behavior or refactoring orchestration-heavy code, add focused regression coverage before or alongside the implementation.

## Shared Infrastructure Rules

- Prefer shared Spring-managed infrastructure beans over repeated local instantiation.
- Do not create local `ObjectMapper` instances in application or infrastructure code.
- Do not hide HTTP clients, crypto helpers, or serializers inside arbitrary services when a shared bean or focused adapter should own configuration.

## Anti-Patterns To Avoid

- Flat global layering such as `controller/`, `service/`, `repository/` across the whole repository.
- Controllers that perform business orchestration directly.
- Use-case classes that only forward one call without adding a boundary or clarifying a workflow.
- Multiple unrelated helper classes in one source file.
- Oversized tests that accumulate unrelated flows.
- Cross-domain reuse through another domain's web DTOs or error models.
- Repeated local infrastructure creation such as `jacksonObjectMapper()`.
- Moving domain-specific code into `common` just because it is shared by two callers.
- Renaming or restructuring code purely for style when it does not improve clarity.

## Pragmatic Exceptions

- A simple query or tiny CRUD update may stay in a compact service without a dedicated use-case class.
- A domain does not need a full `web/application/domain/infrastructure` split if that would add ceremony without clearer boundaries.
- Existing package shapes may stay in place when they are already understandable and the current task does not benefit from moving them.
- Repository interfaces may remain outside `infrastructure` when that matches the current domain layout and keeps the code easy to navigate.
- Small response mappers can stay close to the owning domain instead of being wrapped in extra abstractions.

## Intentional Debt And Compatibility Notes

- The code domain is now `protection`, but configuration still uses the `app.abuse` property prefix for compatibility.
- Treat that prefix as intentional compatibility debt unless a task explicitly includes config migration, rollout planning, and documentation updates.

## Ownership Rules

- Backend owns:
  - endpoint methods and paths
  - request and response DTO shapes
  - error statuses and documented error bodies
  - authentication and session requirements
- Frontend must follow `docs/contracts/*` and OpenAPI.
- If frontend needs a new field or different shape, change the backend contract in this repository first.

## When Changing The API

- Update the relevant `docs/contracts/*` file in the same task.
- Update OpenAPI in the same task.
- Mark the change as `Breaking` or `Non-breaking` in the task summary, PR summary, or contract update notes.
- Update tests to match the documented contract.
- Include example JSON requests and responses.
- Do not ship undocumented response shape changes.

## Definition Of Done For API Changes

- Build and tests pass.
- Contract docs are updated.
- OpenAPI is updated.
- `curl` examples still reflect reality.
- Auth requirements, request shapes, response shapes, and error cases are documented.

## Review Checklist

- Are domain boundaries still explicit?
- Is the controller thin?
- Is orchestration-heavy logic in an application service where it belongs?
- Are file boundaries still predictable?
- Did this change avoid new cross-domain leakage?
- Did it avoid adding domain-specific code to `common`?
- Are docs and OpenAPI updated if public behavior changed?
- Were the broadest relevant tests run?

## Verification Rule

- After making changes, always run the relevant tests before considering the task complete.
- If the full intended test suite cannot run in the current environment, state exactly what was run, what could not be run, and why.

## Local Runbook

- Before running integration tests or validating health-dependent behavior, make sure Docker Desktop is running.
- Local infrastructure for this repository is defined in `compose.yaml`.
- Start local dependencies with:
  - `docker compose up -d`
- This repository currently expects local PostgreSQL and Redis for realistic backend startup and health checks.
- Local email development should use Mailpit from `compose.yaml` instead of an external SMTP provider when possible.
- The local backend profile is defined in `src/main/resources/application-local.yml`.
- Start the backend locally with:
  - `./gradlew bootRun --args='--spring.profiles.active=local'`
- For local email flows, prefer these env values:
  - `MAIL_HOST=localhost`
  - `MAIL_PORT=1025`
  - `MAIL_SMTP_AUTH=false`
  - `MAIL_SMTP_STARTTLS=false`
  - `MAIL_SMTP_SSL_ENABLE=false`
- Mailpit web UI is available at:
  - `http://localhost:8025`
- When validating runtime behavior, verify the app directly instead of relying only on static reasoning:
  - `curl -i http://127.0.0.1:8080/actuator/health`
  - `curl -i http://127.0.0.1:8080/api/public/ping`
- When validating mail flows locally, inspect messages in Mailpit and open the generated verification or reset links from the Mailpit UI.
- For API contract work, refresh and inspect runtime OpenAPI from the running app:
  - `curl http://localhost:8080/v3/api-docs`
  - `curl http://localhost:8080/v3/api-docs.yaml`

## Test Execution Policy

- After changes, run tests before calling the task complete. For backend changes, prefer the broadest relevant verification, not the narrowest possible command.
- Default expectation for normal backend changes is:
  - `./gradlew test`
- Finish substantial backend work with:
  - `./gradlew build`
- If only a smaller scope is appropriate during iteration, still finish with the broadest relevant commands before claiming completion.
- If runtime behavior is under discussion, verify it in a running app when practical, especially for:
  - actuator health
  - auth/session flows
  - CORS behavior
  - OpenAPI endpoints
- If tests fail, do not stop at the first stack trace. Identify whether the failure is:
  - application logic
  - configuration
  - Flyway/migration ordering
  - missing local infrastructure such as Docker, PostgreSQL, or Redis
- If a full test run cannot be executed, explicitly state:
  - the exact command that was run
  - the exact command that could not be run
  - the blocking reason
  - whether the blocker is code-related or environment-related

## Codex Guidance

- Follow this `AGENTS.md` for future tasks in this repository.
- If code and `docs/contracts/*` diverge, bring them back into sync before calling the task complete.
