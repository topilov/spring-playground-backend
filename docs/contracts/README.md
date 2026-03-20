# API Contract Docs

`docs/contracts/*` are the canonical HTTP API contract docs for this backend repository.

- Backend updates these files whenever the auth, profile, or public API changes.
- Frontend consumes these docs from the separate frontend repository.
- Backend owns the contract. Frontend should not invent request or response shapes independently.
- OpenAPI lives alongside these docs as the machine-readable contract at runtime and in `openapi/openapi.yaml`.
- If code and `docs/contracts/*` disagree, treat that as backend work to resolve immediately.

## What Every Contract Doc Must Include

- endpoint
- auth requirement
- request shape
- response shape
- error cases
- notes about cookies, session, or CSRF when relevant
- example requests and responses

## What Every Endpoint Entry Must Include

- method and path
- purpose
- authentication requirement
- request body if applicable
- response body
- typical success status
- typical error statuses
- `curl` example
- notes about session cookie or CSRF where relevant

## Maintenance Rules

- Update contract docs in the same task as the backend API change.
- Update `openapi/openapi.yaml` in the same task as the backend API change.
- Keep examples concrete and JSON-based.
- Mark contract changes as `Breaking` or `Non-breaking` in the task summary, PR summary, or contract update notes.
- Prefer faithful documentation of the current implementation over aspirational wording.
- If something is planned but not implemented, label it clearly as planned and not implemented.
- Frontend should use OpenAPI for generated types and clients, and these markdown docs for auth, cookie, session, and behavior notes.

## Current Contract Files

- `auth.md`
- `auth-api.md` compatibility alias pointing to `auth.md`
- `profile.md`
- `public.md`
