# OpenAPI Contract

OpenAPI is the machine-readable HTTP API contract for this backend repository.

- Runtime schema: `/v3/api-docs`
- Runtime YAML schema: `/v3/api-docs.yaml`
- Committed export: `openapi/openapi.yaml`
- Human-readable companion docs: `docs/contracts/*.md`

## Ownership

- Backend owns both the OpenAPI schema and the markdown contract docs.
- Frontend should use OpenAPI for generated types and API clients.
- Frontend should use `docs/contracts/*.md` for auth flow, session cookie handling, CSRF notes, and behavior details that are easier to explain in prose.

## Local Usage

Fetch the runtime JSON schema locally:

```bash
curl http://localhost:8080/v3/api-docs
```

Fetch the runtime YAML schema locally:

```bash
curl http://localhost:8080/v3/api-docs.yaml
```

Open Swagger UI locally:

```text
http://localhost:8080/swagger-ui/index.html
```

## Refreshing The Committed Export

1. Start PostgreSQL.

   ```bash
   docker compose up -d
   ```

2. Start the backend.

   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local'
   ```

3. Refresh the committed OpenAPI export.

   ```bash
   mkdir -p openapi
   curl -s http://localhost:8080/v3/api-docs.yaml > openapi/openapi.yaml
   ```

4. Stop temporary local services when finished.

## Contract Rules

- API changes must update both OpenAPI and `docs/contracts/*.md` in the same task.
- Do not invent endpoints in OpenAPI that are not implemented in backend code.
- If something is planned but not implemented, keep it out of OpenAPI and mark it clearly as planned in markdown only.
- If markdown docs and OpenAPI diverge, fix the backend contract sources before merging.
