# Auth API Contract

Machine-readable contract: `openapi/openapi.yaml` and runtime `/v3/api-docs`.

Change note for this task: `Breaking` because newly registered users must verify email before `POST /api/auth/login` succeeds.
Change note for this task: `Non-breaking` because the forgot/reset password HTTP contract is unchanged while reset tokens now live in Redis.
Change note for this task: `Non-breaking` because passkeys add new registration, management, and login endpoints without changing the existing password-login contract.

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

- `400 Bad Request` when request validation fails.
- `409 Conflict` when the username or email is already used.

Conflict response body example:

```json
{
  "error": "Username 'demo' is already in use"
}
```

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

- `400 Bad Request` when the email field is blank or malformed.
- No user-specific error is exposed. Current implementation returns the same accepted response for existing and missing emails.

**curl**

```bash
curl -i \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com"}' \
  http://localhost:8080/api/auth/forgot-password
```

**Notes**

- When the account exists, backend stores a one-time reset token in Redis with the configured TTL and attempts to send a reset email.
- The reset email link points to `APP_PUBLIC_BASE_URL + APP_RESET_PASSWORD_PATH` with the token in the `token` query parameter.
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

- `400 Bad Request` when request validation fails.
- `400 Bad Request` when the reset token is invalid or expired.

Token error response body example:

```json
{
  "error": "Password reset token is invalid or expired"
}
```

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
- Reset tokens are server-managed, one-time, and are no longer persisted in PostgreSQL.

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

- `400 Bad Request` with the framework default error body when required fields are blank.
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
- Default non-local configuration also marks the session cookie `Secure`; the `local` and `test` profiles disable `Secure` so HTTP development and tests still work.
- CSRF is currently disabled, so no CSRF token is required for login or follow-up API calls.
- Frontend should send login and protected requests with credentials enabled, for example `fetch(..., { credentials: 'include' })`, so the browser stores and resends `JSESSIONID`.
- Frontend should use OpenAPI for generated request and response types, then follow this markdown doc for session-flow behavior details.

## Passkey Payload Notes

- Passkey request and response bodies wrap browser-generated WebAuthn JSON.
- The `publicKey` object returned by the backend is already the flattened browser options object. Frontend should read `response.publicKey.challenge`, not `response.publicKey.publicKey.challenge`.
- The `publicKey` object returned by the backend is intended to be passed to `navigator.credentials.create()` or `navigator.credentials.get()` after the frontend decodes Base64URL-encoded binary members into `Uint8Array` values.
- The `credential` object sent back to the backend should be the frontend's JSON serialization of the browser `PublicKeyCredential` result.
- Backend treats the WebAuthn payload as protocol data, verifies it server-side, and never exposes stored public keys or other internal credential material in management responses.

## POST /api/auth/passkeys/register/options

**Purpose**

Start passkey registration for the currently authenticated user.

**Authentication**

Valid `JSESSIONID` session required.

**Request Body**

```json
{
  "nickname": "MacBook Touch ID"
}
```

Current request shape:

```json
{
  "nickname": "string, optional, max 100 chars when provided"
}
```

**Response Body**

```json
{
  "ceremonyId": "registration-opaque-id",
  "publicKey": {
    "challenge": "base64url-challenge"
  }
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- `401 Unauthorized` when the session cookie is missing or invalid.

**curl**

```bash
curl -i \
  -b cookies.txt \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"MacBook Touch ID"}' \
  http://localhost:8080/api/auth/passkeys/register/options
```

**Notes**

- Backend stores a short-lived Redis ceremony record keyed by `ceremonyId`.
- The registration ceremony is user-bound; the finish step must be completed by the same authenticated user.
- Ceremony state expires automatically and fails closed when missing or expired.

## POST /api/auth/passkeys/register/verify

**Purpose**

Finish passkey registration for the currently authenticated user and persist the credential.

**Authentication**

Valid `JSESSIONID` session required.

**Request Body**

```json
{
  "ceremonyId": "registration-opaque-id",
  "credential": {
    "id": "browser-generated-id",
    "rawId": "base64url-raw-id",
    "response": {
      "attestationObject": "base64url-attestation-object",
      "clientDataJSON": "base64url-client-data-json"
    },
    "type": "public-key"
  },
  "nickname": "MacBook Touch ID"
}
```

Current request shape:

```json
{
  "ceremonyId": "string, required, must not be blank",
  "credential": "object, required, browser-generated WebAuthn registration payload",
  "nickname": "string, optional, max 100 chars when provided"
}
```

**Response Body**

```json
{
  "id": 10,
  "name": "MacBook Touch ID",
  "createdAt": "2026-03-21T10:15:30Z",
  "deviceHint": "platform",
  "transports": ["internal"]
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- `400 Bad Request` when the ceremony is missing, expired, user-mismatched, or the WebAuthn payload is invalid.
- `401 Unauthorized` when the session cookie is missing or invalid.
- `409 Conflict` when the credential is already registered.

**curl**

```bash
curl -i \
  -b cookies.txt \
  -H 'Content-Type: application/json' \
  -d '{"ceremonyId":"registration-opaque-id","credential":{"id":"...","rawId":"...","response":{"attestationObject":"...","clientDataJSON":"..."},"type":"public-key"},"nickname":"MacBook Touch ID"}' \
  http://localhost:8080/api/auth/passkeys/register/verify
```

**Notes**

- Successful verification persists the passkey in PostgreSQL.
- Successful verification invalidates the ceremony so it cannot be reused.
- Duplicate credential registration is rejected explicitly with `409 Conflict`.

## GET /api/auth/passkeys

**Purpose**

List the current authenticated user's passkeys.

**Authentication**

Valid `JSESSIONID` session required.

**Request Body**

No request body.

**Response Body**

```json
[
  {
    "id": 10,
    "name": "Work Laptop",
    "createdAt": "2026-03-21T10:15:30Z",
    "lastUsedAt": "2026-03-21T12:05:00Z",
    "deviceHint": "platform",
    "transports": ["internal"]
  }
]
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- `401 Unauthorized` when the session cookie is missing or invalid.

**curl**

```bash
curl -i \
  -b cookies.txt \
  http://localhost:8080/api/auth/passkeys
```

**Notes**

- Only passkeys owned by the current user are returned.
- `lastUsedAt` is omitted until the passkey is successfully used for login.

## PATCH /api/auth/passkeys/{id}

**Purpose**

Rename one of the current authenticated user's passkeys.

**Authentication**

Valid `JSESSIONID` session required.

**Request Body**

```json
{
  "name": "Work Laptop"
}
```

Current request shape:

```json
{
  "name": "string, required, must not be blank, max 100 chars"
}
```

**Response Body**

```json
{
  "id": 10,
  "name": "Work Laptop",
  "createdAt": "2026-03-21T10:15:30Z",
  "deviceHint": "platform",
  "transports": ["internal"]
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- `400 Bad Request` when validation fails.
- `401 Unauthorized` when the session cookie is missing or invalid.
- `404 Not Found` when the passkey does not exist for the current user.

**curl**

```bash
curl -i \
  -b cookies.txt \
  -H 'Content-Type: application/json' \
  -X PATCH \
  -d '{"name":"Work Laptop"}' \
  http://localhost:8080/api/auth/passkeys/10
```

**Notes**

- Users may rename only their own credentials.

## DELETE /api/auth/passkeys/{id}

**Purpose**

Delete one of the current authenticated user's passkeys.

**Authentication**

Valid `JSESSIONID` session required.

**Request Body**

No request body.

**Response Body**

No response body.

**Typical Success Status**

- `204 No Content`

**Typical Error Statuses**

- `401 Unauthorized` when the session cookie is missing or invalid.
- `404 Not Found` when the passkey does not exist for the current user.

**curl**

```bash
curl -i \
  -b cookies.txt \
  -X DELETE \
  http://localhost:8080/api/auth/passkeys/10
```

**Notes**

- Users may delete only their own credentials.

## POST /api/auth/passkey-login/options

**Purpose**

Start an unauthenticated passkey login ceremony.

**Authentication**

No prior authentication required.

**Request Body**

```json
{}
```

Current request shape:

```json
{
  "usernameOrEmail": "string, optional, reserved for future login hints"
}
```

**Response Body**

```json
{
  "ceremonyId": "authentication-opaque-id",
  "publicKey": {
    "challenge": "base64url-challenge"
  }
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- No user-specific error is exposed at the options step.

**curl**

```bash
curl -i \
  -H 'Content-Type: application/json' \
  -d '{}' \
  http://localhost:8080/api/auth/passkey-login/options
```

**Notes**

- Backend stores a short-lived Redis authentication ceremony keyed by `ceremonyId`.
- The current implementation is discoverable-credential oriented, so the login options request body is empty.

## POST /api/auth/passkey-login/verify

**Purpose**

Finish passkey login and create the normal authenticated session.

**Authentication**

No prior authentication required.

**Request Body**

```json
{
  "ceremonyId": "authentication-opaque-id",
  "credential": {
    "id": "browser-generated-id",
    "rawId": "base64url-raw-id",
    "response": {
      "authenticatorData": "base64url-authenticator-data",
      "clientDataJSON": "base64url-client-data-json",
      "signature": "base64url-signature"
    },
    "type": "public-key"
  }
}
```

Current request shape:

```json
{
  "ceremonyId": "string, required, must not be blank",
  "credential": "object, required, browser-generated WebAuthn assertion payload"
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

- `400 Bad Request` when the ceremony is missing or expired.
- `401 Unauthorized` when the assertion is invalid or login is not allowed.

**curl**

```bash
curl -i \
  -c cookies.txt \
  -H 'Content-Type: application/json' \
  -d '{"ceremonyId":"authentication-opaque-id","credential":{"id":"...","rawId":"...","response":{"authenticatorData":"...","clientDataJSON":"...","signature":"..."},"type":"public-key"}}' \
  http://localhost:8080/api/auth/passkey-login/verify
```

**Notes**

- Successful passkey login returns the exact same authenticated response body shape as password login.
- Successful passkey login establishes the normal `JSESSIONID` session through the same backend session-login path used by password login.
- Successful login updates `lastUsedAt` for the credential and invalidates the one-time ceremony state.

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
