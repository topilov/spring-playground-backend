# Telegram Focus Sync API Contract

Machine-readable contract: `openapi/openapi.yaml` and runtime `/v3/api-docs`.

Change classification: `Breaking`

## Feature Summary

This backend feature lets an authenticated user:

- connect one Telegram account through a backend-driven phone/code/password flow
- manage one personal automation token for iOS Shortcuts
- manage user-defined Telegram mode resources
- keep a separate `defaultEmojiStatusDocumentId` for the no-active-mode state
- send minimal token-authenticated mode updates to the backend

Important breaking change:

- the backend no longer owns fixed mode names like `personal`, `airplane`, or `sleep`
- clients must create mode resources first and then send those exact user-defined strings back to `/api/telegram/focus-updates`
- only one mode can be active at a time

If no mode is active, the backend applies the user’s explicit `defaultEmojiStatusDocumentId` when configured. Otherwise it clears the Telegram emoji status.

## GET /api/profile/me/telegram

Returns Telegram connection status, token summary, default no-focus status, the currently active mode, and the user’s stored modes.

Response example:

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
  "activeFocusMode": "work",
  "modes": [
    {
      "mode": "work",
      "emojiStatusDocumentId": "1001"
    },
    {
      "mode": "gym",
      "emojiStatusDocumentId": "1007"
    }
  ]
}
```

## POST /api/profile/me/telegram/connect/start

Begins a backend-driven Telegram authorization flow by phone number.

Request:

```json
{
  "phoneNumber": "+15551234567"
}
```

## POST /api/profile/me/telegram/connect/confirm-code

Submits the Telegram login code for a pending auth flow.

## POST /api/profile/me/telegram/connect/confirm-password

Submits the Telegram account password when Telegram 2FA is enabled.

## DELETE /api/profile/me/telegram/connect

Disconnects the active Telegram account for the current user without deleting stored mode resources or default emoji status.

## PUT /api/profile/me/telegram/focus-settings

Updates only the explicit no-active-mode emoji status.

Request:

```json
{
  "defaultEmojiStatusDocumentId": "7000"
}
```

Response: same shape as `GET /api/profile/me/telegram`.

## GET /api/profile/me/telegram/modes

Returns the user’s stored mode resources.

Response:

```json
{
  "modes": [
    {
      "mode": "work",
      "emojiStatusDocumentId": "1001"
    }
  ]
}
```

## POST /api/profile/me/telegram/modes

Creates a user-defined mode resource.

Request:

```json
{
  "mode": "work",
  "emojiStatusDocumentId": "1001"
}
```

Success:

```json
{
  "mode": "work",
  "emojiStatusDocumentId": "1001"
}
```

Typical errors:

- `400 Bad Request` with code `TELEGRAM_MODE_INVALID`
- `409 Conflict` with code `TELEGRAM_MODE_ALREADY_EXISTS`

## PATCH /api/profile/me/telegram/modes/{mode}

Updates an existing mode resource.

Request:

```json
{
  "newMode": "deep_work",
  "emojiStatusDocumentId": "1002"
}
```

Typical errors:

- `400 Bad Request` with code `TELEGRAM_MODE_INVALID`
- `404 Not Found` with code `TELEGRAM_MODE_NOT_FOUND`
- `409 Conflict` with code `TELEGRAM_MODE_ALREADY_EXISTS`

## DELETE /api/profile/me/telegram/modes/{mode}

Deletes an existing mode resource.

If the deleted mode was active, the backend clears the active mode association.

Typical errors:

- `404 Not Found` with code `TELEGRAM_MODE_NOT_FOUND`

## POST /api/profile/me/telegram/automation-token

Creates the initial personal automation token.

## POST /api/profile/me/telegram/automation-token/regenerate

Replaces the active Telegram automation token and returns the new raw token once.

## DELETE /api/profile/me/telegram/automation-token

Revokes the active Telegram automation token.

## POST /api/telegram/focus-updates

Requires `Authorization: Bearer <token>` with the personal automation token.

Request:

```json
{
  "mode": "work",
  "active": true
}
```

Behavior:

- `active=true` activates the named user-defined mode and applies its `emojiStatusDocumentId`
- `active=false` clears the active mode only when the named mode is currently active
- when no mode is active, the backend applies `defaultEmojiStatusDocumentId`

Success response:

```json
{
  "applied": true,
  "activeFocusMode": "work",
  "appliedEmojiStatusDocumentId": "1001"
}
```

When deactivating the active mode:

```json
{
  "applied": true,
  "appliedEmojiStatusDocumentId": "7000"
}
```

Typical errors:

- `401 Unauthorized` with code `TELEGRAM_AUTOMATION_TOKEN_INVALID`
- `404 Not Found` with code `TELEGRAM_MODE_NOT_FOUND` when activating an unknown mode
- `404 Not Found` with code `TELEGRAM_NOT_CONNECTED`
- `409 Conflict` with code `TELEGRAM_PREMIUM_REQUIRED`
- `502 Bad Gateway` with code `TELEGRAM_SYNC_FAILED`

## curl examples

Create a mode:

```bash
curl -i \
  -b cookies.txt \
  -H 'Content-Type: application/json' \
  -d '{"mode":"work","emojiStatusDocumentId":"1001"}' \
  http://localhost:8080/api/profile/me/telegram/modes
```

Apply a mode update:

```bash
curl -i \
  -H 'Authorization: Bearer tgf_example' \
  -H 'Content-Type: application/json' \
  -d '{"mode":"work","active":true}' \
  http://localhost:8080/api/telegram/focus-updates
```
