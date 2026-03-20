# Auth API Contract

Machine-readable contract: `openapi/openapi.yaml` and runtime `/v3/api-docs`.

## POST /api/auth/login

**Purpose**

Authenticate a user by username or email plus password, then establish a server-side session.

**Authentication**

No prior authentication required.

**Request Body**

```json
{
  "usernameOrEmail": "demo",
  "password": "demo-password"
}
```

Current request shape:

```json
{
  "usernameOrEmail": "string, required, must not be blank",
  "password": "string, required, must not be blank"
}
```

**Response Body**

```json
{
  "authenticated": true,
  "userId": 1,
  "username": "demo",
  "email": "demo@example.com",
  "role": "USER"
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- `401 Unauthorized` with an empty body when credentials are invalid.
- `401 Unauthorized` with an empty body for blank login fields in the current implementation.

**curl**

```bash
curl -i \
  -c cookies.txt \
  -H 'Content-Type: application/json' \
  -d '{"usernameOrEmail":"demo","password":"demo-password"}' \
  http://localhost:8080/api/auth/login
```

**Notes**

- Successful login sets the `JSESSIONID` session cookie.
- Current cookie behavior is session-based, `HttpOnly`, and `SameSite=Lax`.
- CSRF is currently disabled, so no CSRF token is required for login or follow-up API calls.
- Frontend should persist and resend the session cookie on protected requests.
- Frontend should use OpenAPI for generated request and response types, then follow this markdown doc for session-flow behavior details.

## POST /api/auth/logout

**Purpose**

Invalidate the current authenticated session.

**Authentication**

No prior authentication required to call the endpoint, but it is meaningful when a valid session cookie is present.

**Request Body**

No request body.

**Response Body**

No response body.

**Typical Success Status**

- `204 No Content`

**Typical Error Statuses**

- None expected in normal application flow.

**curl**

```bash
curl -i \
  -b cookies.txt \
  -X POST \
  http://localhost:8080/api/auth/logout
```

**Notes**

- Current implementation returns `204 No Content` even when there is no active session.
- CSRF is currently disabled, so no CSRF token is required.
