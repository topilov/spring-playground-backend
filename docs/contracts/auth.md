# Auth API Contract

Machine-readable contract: `openapi/openapi.yaml` and runtime `/v3/api-docs`.

## POST /api/auth/register

**Purpose**

Create a new user account and default profile, then send a welcome email when delivery succeeds.

**Authentication**

No prior authentication required.

**Request Body**

```json
{
  "username": "new-user",
  "email": "new-user@example.com",
  "password": "very-secret-password"
}
```

Current request shape:

```json
{
  "username": "string, required, must not be blank, max 64 chars",
  "email": "string, required, must be a valid email, max 255 chars",
  "password": "string, required, must not be blank, min 8 chars, max 100 chars"
}
```

**Response Body**

```json
{
  "userId": 2,
  "username": "new-user",
  "email": "new-user@example.com"
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- `409 Conflict` when the username or email is already used.

**curl**

```bash
curl -i \
  -H 'Content-Type: application/json' \
  -d '{"username":"new-user","email":"new-user@example.com","password":"very-secret-password"}' \
  http://localhost:8080/api/auth/register
```

**Notes**

- Registration creates the auth user and a default profile.
- Registration does not create an authenticated session.
- Welcome email delivery is attempted after successful persistence.

## POST /api/auth/forgot-password

**Purpose**

Accept an email address and issue a password reset token when the account exists.

**Authentication**

No prior authentication required.

**Request Body**

```json
{
  "email": "demo@example.com"
}
```

Current request shape:

```json
{
  "email": "string, required, must be a valid email, max 255 chars"
}
```

**Response Body**

```json
{
  "accepted": true
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- No user-specific error is exposed. Current implementation returns the same accepted response for existing and missing emails.

**curl**

```bash
curl -i \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com"}' \
  http://localhost:8080/api/auth/forgot-password
```

**Notes**

- When the account exists, backend stores a reset token and attempts to send a reset email.
- When the account does not exist, backend still returns the same accepted response.

## POST /api/auth/reset-password

**Purpose**

Reset the password for an account using a valid reset token.

**Authentication**

No prior authentication required.

**Request Body**

```json
{
  "token": "token-from-email",
  "newPassword": "new-very-secret-password"
}
```

Current request shape:

```json
{
  "token": "string, required, must not be blank",
  "newPassword": "string, required, must not be blank, min 8 chars, max 100 chars"
}
```

**Response Body**

```json
{
  "reset": true
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- `400 Bad Request` when the reset token is invalid or expired.

**curl**

```bash
curl -i \
  -H 'Content-Type: application/json' \
  -d '{"token":"token-from-email","newPassword":"new-very-secret-password"}' \
  http://localhost:8080/api/auth/reset-password
```

**Notes**

- Successful reset invalidates all active reset tokens for the user.
- Successful reset updates the stored password hash and does not auto-login the user.

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
- Browser-based cross-origin requests from configured frontend origins are allowed. The default local development origin is `http://localhost:4173`.
- CSRF is currently disabled, so no CSRF token is required for login or follow-up API calls.
- Frontend should send login and protected requests with credentials enabled, for example `fetch(..., { credentials: 'include' })`, so the browser stores and resends `JSESSIONID`.
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
