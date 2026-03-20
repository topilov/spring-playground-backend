# Auth API Contract

Machine-readable contract: `openapi/openapi.yaml` and runtime `/v3/api-docs`.

Change note for this task: `Breaking` because newly registered users must verify email before `POST /api/auth/login` succeeds.

## POST /api/auth/register

**Purpose**

Create a new user account and default profile, then send an email verification link when delivery succeeds.

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
- Registration stores the new account as email-unverified.
- Registration does not create an authenticated session.
- Verification email delivery is attempted after successful persistence.
- The verification link targets the frontend route and includes a one-time token in the `token` query parameter.

## POST /api/auth/verify-email

**Purpose**

Confirm a user's email address using the token that was sent after registration or resend.

**Authentication**

No prior authentication required.

**Request Body**

```json
{
  "token": "token-from-email"
}
```

Current request shape:

```json
{
  "token": "string, required, must not be blank"
}
```

**Response Body**

```json
{
  "verified": true
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- `400 Bad Request` when the verification token is invalid or expired.

**curl**

```bash
curl -i \
  -H 'Content-Type: application/json' \
  -d '{"token":"token-from-email"}' \
  http://localhost:8080/api/auth/verify-email
```

**Notes**

- Successful verification marks the stored account email as verified.
- Successful verification invalidates the one-time token, so it cannot be reused.

## POST /api/auth/resend-verification-email

**Purpose**

Accept an email address and send a fresh verification link when the account exists and is not yet verified.

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

- No user-specific error is exposed. Current implementation returns the same accepted response for existing, missing, and already verified emails.

**curl**

```bash
curl -i \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com"}' \
  http://localhost:8080/api/auth/resend-verification-email
```

**Notes**

- When the account exists and is still unverified, backend stores a fresh one-time verification token in Redis and attempts to send a new verification email.
- When the account does not exist or is already verified, backend still returns the same accepted response.

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
- `401 Unauthorized` with a JSON body when the credentials are valid but the email is not verified:

```json
{
  "error": "Email is not verified",
  "code": "EMAIL_NOT_VERIFIED"
}
```

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
- Successful login requires a verified email address.
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
