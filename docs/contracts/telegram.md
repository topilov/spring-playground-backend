# Telegram Focus Sync API Contract

Machine-readable contract: `openapi/openapi.yaml` and runtime `/v3/api-docs`.

Change classification: `Non-breaking`

## Feature Summary

This backend feature lets an authenticated user:

- connect one Telegram account through a backend-driven phone/code/password flow
- manage one personal automation token for iOS Shortcuts
- configure Telegram emoji-status mappings for supported iPhone Focus modes
- send minimal token-authenticated focus updates to the backend

Supported focus modes:

- `personal`
- `airplane`
- `do_not_disturb`
- `reduce_interruptions`
- `sleep`

Priority resolution when multiple modes are active:

1. `sleep`
2. `do_not_disturb`
3. `reduce_interruptions`
4. `airplane`
5. `personal`

If no mode is active, the backend applies the user’s explicit `defaultEmojiStatusDocumentId` when configured. Otherwise it clears the Telegram emoji status.

## GET /api/profile/me/telegram

**Purpose**

Returns Telegram connection status, token summary, effective focus state, default no-focus status, resolved emoji mappings, and active focus modes.

**Authentication**

Requires a valid authenticated session cookie from `POST /api/auth/login`.

**Request Body**

No request body.

**Response Body**

```json
{
  "connected": true,
  "connectionStatus": "CONNECTED",
  "telegramUser": {
    "id": 900001,
    "phoneNumber": "+15551234567",
    "username": "telegram-1",
    "displayName": "Telegram User 1",
    "premium": true
  },
  "automationToken": {
    "present": true,
    "tokenHint": "tgf_abcd...",
    "createdAt": "2026-03-26T10:15:30Z",
    "lastUsedAt": "2026-03-26T11:00:00Z"
  },
  "defaultEmojiStatusDocumentId": "7000",
  "effectiveFocusMode": "sleep",
  "resolvedEmojiMappings": {
    "personal": "1001",
    "airplane": "1002",
    "do_not_disturb": "1003",
    "reduce_interruptions": "1004",
    "sleep": "1005"
  },
  "activeFocusModes": [
    "sleep"
  ]
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- `401 Unauthorized` with an empty body when the session cookie is missing or invalid.

**curl**

```bash
curl -i \
  -b cookies.txt \
  http://localhost:8080/api/profile/me/telegram
```

## POST /api/profile/me/telegram/connect/start

**Purpose**

Begins a backend-driven Telegram authorization flow by phone number.

**Authentication**

Requires a valid authenticated session cookie.

**Request Body**

```json
{
  "phoneNumber": "+15551234567"
}
```

**Response Body**

```json
{
  "connected": false,
  "pendingAuthId": "telegram-auth-opaque-id",
  "nextStep": "CODE"
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- `400 Bad Request` when validation fails.
- `401 Unauthorized` when the session is missing.

## POST /api/profile/me/telegram/connect/confirm-code

**Purpose**

Submits the Telegram login code for a pending auth flow.

**Authentication**

Requires a valid authenticated session cookie.

**Request Body**

```json
{
  "pendingAuthId": "telegram-auth-opaque-id",
  "code": "12345"
}
```

**Response Body**

Password-required step:

```json
{
  "connected": false,
  "pendingAuthId": "telegram-auth-opaque-id",
  "nextStep": "PASSWORD"
}
```

Connected step:

```json
{
  "connected": true,
  "connectionStatus": "CONNECTED",
  "telegramUser": {
    "id": 900001,
    "phoneNumber": "+15551234567",
    "username": "telegram-1",
    "displayName": "Telegram User 1",
    "premium": true
  }
}
```

**Typical Error Statuses**

- `400 Bad Request` with code `TELEGRAM_INVALID_AUTH_STEP` when the pending auth is in the wrong step.
- `404 Not Found` with code `TELEGRAM_PENDING_AUTH_NOT_FOUND` when the pending auth is missing or expired.
- `401 Unauthorized` when the session is missing.

## POST /api/profile/me/telegram/connect/confirm-password

**Purpose**

Submits the Telegram account password when Telegram 2FA is enabled.

**Authentication**

Requires a valid authenticated session cookie.

**Request Body**

```json
{
  "pendingAuthId": "telegram-auth-opaque-id",
  "password": "telegram-secret"
}
```

**Response Body**

```json
{
  "connected": true,
  "connectionStatus": "CONNECTED",
  "telegramUser": {
    "id": 900001,
    "phoneNumber": "+15551234567",
    "username": "telegram-1",
    "displayName": "Telegram User 1",
    "premium": true
  }
}
```

**Typical Error Statuses**

- `400 Bad Request` with code `TELEGRAM_INVALID_AUTH_STEP`.
- `404 Not Found` with code `TELEGRAM_PENDING_AUTH_NOT_FOUND`.
- `401 Unauthorized` when the session is missing.

## DELETE /api/profile/me/telegram/connect

**Purpose**

Disconnects the active Telegram account for the current user.

**Authentication**

Requires a valid authenticated session cookie.

**Behavior Notes**

- Disconnect does not implicitly wipe saved focus mappings.
- Disconnect does not implicitly wipe the explicit default no-focus status.
- Disconnect does not implicitly revoke the automation token.

**Typical Success Status**

- `204 No Content`

## PUT /api/profile/me/telegram/focus-settings

**Purpose**

Updates the explicit default no-focus emoji status and per-mode emoji-status overrides.

**Authentication**

Requires a valid authenticated session cookie.

**Request Body**

```json
{
  "defaultEmojiStatusDocumentId": "7000",
  "mappings": {
    "personal": "1001",
    "airplane": "1002",
    "do_not_disturb": "1003",
    "reduce_interruptions": "1004",
    "sleep": "1005"
  }
}
```

**Response Body**

Returns the same shape as `GET /api/profile/me/telegram`.

**Typical Success Status**

- `200 OK`

## POST /api/profile/me/telegram/automation-token

**Purpose**

Creates the initial personal automation token.

**Authentication**

Requires a valid authenticated session cookie and a connected Telegram account.

**Response Body**

```json
{
  "token": "tgf_very_long_random_token",
  "tokenHint": "tgf_very..."
}
```

**Notes**

- The raw token is returned only once.
- The backend stores only a hash of the token.
- Only one active automation token exists per user.

**Typical Error Statuses**

- `404 Not Found` with code `TELEGRAM_NOT_CONNECTED` when Telegram is not connected.
- `409 Conflict` when an active token already exists.

## POST /api/profile/me/telegram/automation-token/regenerate

**Purpose**

Replaces the current active automation token.

**Authentication**

Requires a valid authenticated session cookie and a connected Telegram account.

**Response Body**

```json
{
  "token": "tgf_new_long_random_token",
  "tokenHint": "tgf_new..."
}
```

**Notes**

- The previous raw token stops working immediately.
- The new raw token is returned only once.

## DELETE /api/profile/me/telegram/automation-token

**Purpose**

Revokes the current active automation token.

**Authentication**

Requires a valid authenticated session cookie.

**Typical Success Status**

- `204 No Content`

## POST /api/telegram/focus-updates

**Purpose**

Accepts minimal iOS Shortcut focus updates and synchronizes the effective Telegram emoji status.

**Authentication**

Requires `Authorization: Bearer <token>` with the personal automation token.

**Request Body**

```json
{
  "mode": "sleep",
  "active": true
}
```

**Response Body**

```json
{
  "applied": true,
  "effectiveFocusMode": "sleep",
  "appliedEmojiStatusDocumentId": "1005"
}
```

No-focus example:

```json
{
  "applied": true,
  "appliedEmojiStatusDocumentId": "7000"
}
```

**Typical Success Status**

- `200 OK`

**Typical Error Statuses**

- `401 Unauthorized` with code `TELEGRAM_AUTOMATION_TOKEN_INVALID` when the bearer token is missing, invalid, or revoked.
- `404 Not Found` with code `TELEGRAM_NOT_CONNECTED` when the owning user has no connected Telegram account.
- `409 Conflict` with code `TELEGRAM_PREMIUM_REQUIRED` when the connected Telegram account cannot set emoji status.
- `429 Too Many Requests` with code `RATE_LIMITED` when the automation endpoint is throttled.

**curl**

```bash
curl -i \
  -H 'Authorization: Bearer tgf_very_long_random_token' \
  -H 'Content-Type: application/json' \
  -d '{"mode":"sleep","active":true}' \
  http://localhost:8080/api/telegram/focus-updates
```

## Notes

- The automation endpoint is deliberately minimal and does not require captcha.
- The backend owns focus state. Shortcuts send only the changed mode and active flag.
- The backend recalculates the effective mode on every update.
- If no explicit per-mode mapping exists, the backend falls back to configured default mappings.
- If `defaultEmojiStatusDocumentId` is absent and no mode is active, the backend clears the Telegram emoji status.
