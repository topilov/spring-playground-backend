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

## POST /api/profile/me/username

**Purpose**

Update the current authenticated user's username.

**Authentication**

Requires a valid authenticated session cookie from `POST /api/auth/login`.

**Request Body**

```json
{
  "username": "new-demo"
}
```

Current request shape:

```json
{
  "username": "string, required, must not be blank, max 64 chars"
}
```

**Response Body**

```json
{
  "id": 1,
  "userId": 1,
  "username": "new-demo",
  "email": "demo@example.com",
  "role": "USER",
  "displayName": "Demo User",
  "bio": "Session-backed example profile"
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- `400 Bad Request` when request validation fails.
- `401 Unauthorized` with an empty body when the session cookie is missing or invalid.
- `404 Not Found` if the authenticated user does not have a linked profile.
- `409 Conflict` when the requested username is already in use.

**curl**

```bash
curl -i \
  -b cookies.txt \
  -H 'Content-Type: application/json' \
  -X POST \
  -d '{"username":"new-demo"}' \
  http://localhost:8080/api/profile/me/username
```

**Notes**

- Session cookie required.
- No CSRF token required.
- Backend trims leading and trailing whitespace from `username` before checking uniqueness and persisting.

## POST /api/profile/me/password

**Purpose**

Change the current authenticated user's password.

**Authentication**

Requires a valid authenticated session cookie from `POST /api/auth/login`.

**Request Body**

```json
{
  "currentPassword": "demo-password",
  "newPassword": "updated-demo-password"
}
```

Current request shape:

```json
{
  "currentPassword": "string, required",
  "newPassword": "string, required, min 8 chars, max 100 chars"
}
```

**Response Body**

```json
{
  "changed": true
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- `400 Bad Request` when request validation fails or `currentPassword` is incorrect.
- `401 Unauthorized` with an empty body when the session cookie is missing or invalid.
- `404 Not Found` if the authenticated user does not have a linked profile.

Observed error body example for an incorrect current password:

```json
{
  "error": "Current password is incorrect"
}
```

**curl**

```bash
curl -i \
  -b cookies.txt \
  -H 'Content-Type: application/json' \
  -X POST \
  -d '{"currentPassword":"demo-password","newPassword":"updated-demo-password"}' \
  http://localhost:8080/api/profile/me/password
```

**Notes**

- Session cookie required.
- No CSRF token required.
- Password changes take effect for subsequent logins immediately.

## POST /api/profile/me/email/change-request

**Purpose**

Start a new email-change flow for the current authenticated user.

**Authentication**

Requires a valid authenticated session cookie from `POST /api/auth/login`.

**Request Body**

```json
{
  "newEmail": "new-demo@example.com"
}
```

Current request shape:

```json
{
  "newEmail": "string, required, valid email, max 255 chars"
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

- `400 Bad Request` when request validation fails or `newEmail` matches the current email.
- `401 Unauthorized` with an empty body when the session cookie is missing or invalid.
- `404 Not Found` if the authenticated user does not have a linked profile.
- `409 Conflict` when the requested email is already in use.

Observed error body example when the new email matches the current email:

```json
{
  "error": "New email must be different from the current email"
}
```

**curl**

```bash
curl -i \
  -b cookies.txt \
  -H 'Content-Type: application/json' \
  -X POST \
  -d '{"newEmail":"new-demo@example.com"}' \
  http://localhost:8080/api/profile/me/email/change-request
```

**Notes**

- Session cookie required.
- No CSRF token required.
- Backend lowercases `newEmail` before uniqueness checks and email delivery.
- The verification mail is sent to the new email address.
- The verification link targets a dedicated frontend email-change route and includes the one-time token in the `token` query parameter.
- Pending email-change tokens are one-time use.
- Starting a new email-change request invalidates every older pending email-change token for the same user.
- The current login session remains valid while the user is waiting to verify the new email.

## POST /api/profile/me/email/verify

**Purpose**

Confirm a pending email change using a one-time token sent to the new email address.

**Authentication**

No authenticated session is required. The one-time token is sufficient.

**Request Body**

```json
{
  "token": "one-time-email-change-token"
}
```

Current request shape:

```json
{
  "token": "string, required"
}
```

**Response Body**

```json
{
  "id": 1,
  "userId": 1,
  "username": "demo",
  "email": "new-demo@example.com",
  "role": "USER",
  "displayName": "Demo User",
  "bio": "Session-backed example profile"
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- `400 Bad Request` when the token is invalid, expired, already used, or request validation fails.
- `404 Not Found` if the linked profile no longer exists.
- `409 Conflict` when the pending new email was claimed by another account before verification.

Observed error body example for an invalid or already-used token:

```json
{
  "error": "Email change token is invalid or expired"
}
```

**curl**

```bash
curl -i \
  -H 'Content-Type: application/json' \
  -X POST \
  -d '{"token":"one-time-email-change-token"}' \
  http://localhost:8080/api/profile/me/email/verify
```

**Notes**

- No session cookie required.
- The token is one-time use.
- If a newer email-change request was started for the same user, every older token becomes invalid.
- After successful verification, login by the old email stops working and login by the new email starts working immediately.
