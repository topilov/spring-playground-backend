# Profile API Contract

Machine-readable contract: `openapi/openapi.yaml` and runtime `/v3/api-docs`.

## GET /api/profile/me

**Purpose**

Fetch the current authenticated user's profile.

**Authentication**

Requires a valid authenticated session cookie from `POST /api/auth/login`.

**Request Body**

No request body.

**Response Body**

```json
{
  "id": 1,
  "userId": 1,
  "username": "demo",
  "email": "demo@example.com",
  "role": "USER",
  "displayName": "Demo User",
  "bio": "Session-backed example profile"
}
```

Current response shape:

```json
{
  "id": "number",
  "userId": "number",
  "username": "string",
  "email": "string",
  "role": "string",
  "displayName": "string",
  "bio": "string"
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- `401 Unauthorized` with an empty body when the session cookie is missing or invalid.
- `404 Not Found` if the authenticated user does not have a linked profile. This is raised by backend code as `ProfileNotFoundException`. No custom error envelope is implemented for this case.

**curl**

```bash
curl -i \
  -b cookies.txt \
  http://localhost:8080/api/profile/me
```

**Notes**

- Session cookie required.
- No CSRF token required.
- Frontend should use OpenAPI for generated request and response types, then follow this markdown doc for session-flow behavior details.

## PUT /api/profile/me

**Purpose**

Update the current authenticated user's profile.

**Authentication**

Requires a valid authenticated session cookie from `POST /api/auth/login`.

**Request Body**

```json
{
  "displayName": "Updated Demo",
  "bio": "Updated from frontend"
}
```

Current request shape:

```json
{
  "displayName": "string, required, must not be blank, max 120 chars",
  "bio": "string, optional, defaults to empty string, max 600 chars"
}
```

**Response Body**

```json
{
  "id": 1,
  "userId": 1,
  "username": "demo",
  "email": "demo@example.com",
  "role": "USER",
  "displayName": "Updated Demo",
  "bio": "Updated from frontend"
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- `400 Bad Request` when request validation fails.
- `401 Unauthorized` with an empty body when the session cookie is missing or invalid.
- `404 Not Found` if the authenticated user does not have a linked profile.

Observed validation error body example:

```json
{
  "timestamp": "2026-03-20T06:22:13.782Z",
  "status": 400,
  "error": "Bad Request",
  "path": "/api/profile/me"
}
```

**curl**

```bash
curl -i \
  -b cookies.txt \
  -H 'Content-Type: application/json' \
  -X PUT \
  -d '{"displayName":"Updated Demo","bio":"Updated from frontend"}' \
  http://localhost:8080/api/profile/me
```

**Notes**

- Session cookie required.
- No CSRF token required.
- Backend trims leading and trailing whitespace from `displayName` and `bio` before persisting.
