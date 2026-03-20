# AGENTS.md

This repository is the backend source of truth for the HTTP API contract. Codex and human contributors should follow this guidance for all future work in this repository.

## Repository Role

- This is the backend repository.
- Backend-owned HTTP contracts live in:
  - `docs/contracts/*` for human-readable docs
  - `openapi/openapi.yaml` and runtime `/v3/api-docs` for the machine-readable contract
- The separate frontend repository must consume these docs and must not invent request, response, error, or auth shapes independently.

## Architectural Context

- Kotlin + Spring Boot modular monolith.
- Current modules: `auth`, `profile`, `publicapi`.
- Authentication is session-based.
- Protected endpoints rely on the server-managed `JSESSIONID` session cookie.

## Ownership Rules

- Backend owns:
  - endpoint methods and paths
  - request and response DTO shapes
  - error statuses and documented error bodies
  - authentication and session requirements
- Frontend must follow `docs/contracts/*` and OpenAPI.
- If frontend needs a new field or different shape, change the backend contract in this repository first.

## When Changing the API

- Update the relevant `docs/contracts/*` file in the same task.
- Update OpenAPI in the same task.
- Mark the change as `Breaking` or `Non-breaking` in the task summary, PR summary, or contract update notes.
- Update tests to match the documented contract.
- Include example JSON requests and responses.
- Do not ship undocumented response shape changes.

## Coding Guidance

- Prefer explicit DTOs.
- Keep controllers thin.
- Keep business logic in services.
- Keep auth and session behavior explicit.
- Avoid undocumented response shape changes.
- If a response relies on framework-default error handling, document that clearly instead of implying a custom error envelope.

## Definition Of Done For API Changes

- Build and tests pass.
- Contract docs are updated.
- OpenAPI is updated.
- `curl` examples still reflect reality.
- Auth requirements, request shapes, response shapes, and error cases are documented.

## Verification Rule

- After making changes, always run the relevant tests before considering the task complete.
- If the full intended test suite cannot run in the current environment, state exactly what was run, what could not be run, and why.

## Codex Guidance

- Codex should follow this `AGENTS.md` for future tasks in this repository.
- If code and `docs/contracts/*` diverge, bring them back into sync before calling the task complete.
