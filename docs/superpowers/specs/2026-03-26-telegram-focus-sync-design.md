# Telegram Focus Sync Design

## Summary

Add a production-oriented backend feature that lets an authenticated site user connect their personal Telegram account, configure emoji mappings for supported iPhone Focus modes, generate a personal automation token for iOS Shortcuts, and have the backend update their Telegram emoji status whenever iOS sends focus activation changes.

The feature must:

- authenticate the user’s Telegram account through the site using TDLight Java
- persist the Telegram session server-side for future automation use
- persist backend-owned focus state so the effective status can be recalculated safely
- let the user manage one active automation token stored as a hash only
- let the user configure per-mode emoji-status mappings with sensible defaults
- expose a minimal token-authenticated automation endpoint for iOS Shortcuts
- restore a configured default or clear the emoji status when no focus mode is active

## Goals

- Support a backend-driven Telegram login flow using phone number, login code, and optional Telegram 2FA password.
- Persist one connected Telegram account per backend user.
- Persist one automation token per backend user, hashed at rest.
- Support viewing and updating Telegram connection state, automation-token state, default emoji status, and focus-mode mappings through authenticated profile endpoints.
- Accept minimal iOS Shortcut requests containing only `mode` and `active`.
- Recompute the effective focus mode on every update instead of trusting clients to send full state.
- Apply Telegram emoji status updates through a dedicated TDLight-backed integration layer.
- Return stable error codes for product-meaningful failures.

## Non-Goals

- Supporting multiple Telegram accounts per backend user.
- Supporting Telegram account registration from this backend.
- Supporting arbitrary user-defined focus mode names beyond the fixed supported set.
- Building frontend UI in this repository.
- Using a Telegram bot instead of a user-authenticated Telegram session.
- Preserving Telegram’s pre-existing custom status implicitly when focus ends without storing it explicitly.

## Existing Context

The project already has:

- session-based backend authentication with `JSESSIONID`
- thin controllers with orchestration in application services
- JPA-backed durable state in PostgreSQL
- Redis-backed short-lived stores for challenge/token flows
- explicit DTOs and stable error responses
- OpenAPI and markdown contract docs as backend-owned API sources of truth

The new feature fits best as a dedicated top-level `telegram` domain rather than expanding `profile` or `auth` directly.

## External Constraints

### TDLight and Telegram auth flow

TDLight Java follows Telegram’s normal authorization-state flow, which aligns with the chosen site UX:

- start with phone number
- submit login code
- submit Telegram password when the account has 2FA enabled

The backend should model pending auth state explicitly inside the `telegram` domain instead of leaking it into generic auth code.

### Emoji status capability constraints

Telegram emoji status updates are capability-dependent:

- setting a custom emoji status for a user account is a premium capability
- clearing the emoji status resets to Telegram’s default badge
- Telegram does not automatically restore some previous custom status unless the backend stores and reapplies a chosen default

Relevant references:

- [TDLight Java](https://github.com/tdlight-team/tdlight-java)
- [TDLib `setEmojiStatus`](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1set_emoji_status.html)
- [Telegram emoji status behavior](https://core.telegram.org/api/emoji-status)

## Supported Focus Modes

The backend supports exactly these modes:

- `personal`
- `airplane`
- `do_not_disturb`
- `reduce_interruptions`
- `sleep`

These values should be represented by a Kotlin enum and persisted as stable string values.

## Approaches Considered

### 1. Ephemeral Telegram auth and direct status updates only

Authenticate Telegram only long enough to perform one update and require reconnects later.

Pros:

- less durable backend state

Cons:

- fails the automation requirement
- unsuitable for iOS Shortcuts
- poor user experience

### 2. Telegram bot-driven status management

Have a bot update user emoji status after bot permission setup.

Pros:

- avoids storing a user TDLight session

Cons:

- does not match the chosen connect-your-account-through-the-site flow
- changes the product model
- requires a different Telegram permission flow

### 3. Persistent per-user TDLight session with backend-owned focus state

Store a Telegram session per connected user, keep focus state and mapping inside the backend, and expose a small automation endpoint authenticated by a per-user token.

Pros:

- matches the desired UX
- keeps iOS Shortcut payloads small
- allows the backend to derive effective state safely
- fits the repo’s architecture

Cons:

- requires durable TDLight session storage and some extra operational care

## Recommended Approach

Use a dedicated top-level `telegram` domain with:

- durable PostgreSQL records for Telegram account linkage, focus state, mappings, and automation token metadata
- a persistent TDLight session directory per connected user
- encrypted storage of the TDLib database key used for that session
- application services that orchestrate Telegram auth completion, focus-state recalculation, token handling, and emoji sync
- a minimal token-authenticated automation endpoint with protection-level rate limiting

## Domain Structure

Create a new top-level `telegram` package with:

- `telegram.web`
- `telegram.application`
- `telegram.domain`
- `telegram.infrastructure`

Responsibilities:

### `telegram.web`

- authenticated profile endpoints for Telegram connection, focus settings, and automation token management
- unauthenticated but token-authenticated automation endpoint
- explicit request/response DTOs
- Telegram-specific exception handling and stable error-code mapping

### `telegram.application`

- orchestration-heavy flows only
- Telegram connection start and completion
- focus settings retrieval and update
- automation token create, regenerate, revoke
- token-authenticated focus update processing and sync

### `telegram.domain`

- JPA entities
- focus mode enum and priority rules
- mapping/default-resolution logic
- stable Telegram feature exceptions

### `telegram.infrastructure`

- Spring Data repositories
- TDLight client/session management
- encryption helper for TDLib database keys
- Telegram-specific configuration properties

## Persistence Model

## `telegram_account_connection`

One row per backend user.

Fields:

- `id`
- `user_id` unique FK to `auth_user`
- `telegram_user_id`
- `telegram_phone_number`
- `telegram_username`
- `telegram_display_name`
- `connection_status`
- `premium`
- `session_directory_key`
- `session_database_key_ciphertext`
- `default_emoji_status_document_id`
- `last_sync_error_code`
- `last_sync_error_message`
- `last_synced_at`
- `created_at`
- `updated_at`

Notes:

- `default_emoji_status_document_id` is the explicit no-focus fallback status.
- If this field is null, no-focus means clear the emoji status.
- `connection_status` should distinguish at least `DISCONNECTED`, `PENDING_CODE`, `PENDING_PASSWORD`, and `CONNECTED`.

## `telegram_focus_state`

One row per user and supported mode.

Fields:

- `id`
- `user_id` FK to `auth_user`
- `focus_mode`
- `active`
- `last_activated_at`
- `updated_at`

Constraints:

- unique on `(user_id, focus_mode)`

Purpose:

- backend-owned state for safe effective-mode recalculation

## `telegram_focus_mapping`

One row per user and supported mode.

Fields:

- `id`
- `user_id` FK to `auth_user`
- `focus_mode`
- `emoji_status_document_id`
- `created_at`
- `updated_at`

Constraints:

- unique on `(user_id, focus_mode)`

Purpose:

- stores user overrides only
- absence of a row means use backend defaults

## `telegram_automation_token`

One row per user.

Fields:

- `id`
- `user_id` unique FK to `auth_user`
- `token_hash`
- `token_hint`
- `created_at`
- `last_used_at`
- `revoked_at`

Rules:

- only one active token per user
- raw token is never stored
- raw token is returned only on create/regenerate

## Pending Telegram Auth State

Pending Telegram authorization state should remain inside the `telegram` domain and should not use generic app auth abstractions.

The pending state is short-lived and not part of the durable connected-account model. Redis is a good fit because:

- auth steps are short-lived
- values are opaque and domain-specific
- the repo already uses Redis for challenge-style flows

Add a small `TelegramPendingAuthStore` abstraction with a Redis implementation storing:

- pending auth id
- backend user id
- current authorization step
- TDLight client/session key reference
- phone number
- expirations

Pending auth should expire automatically after a short TTL such as 10 minutes.

## Telegram Session Storage

Each connected user gets a dedicated TDLight session directory under a configured session root such as:

- `app.telegram.session-root`

The backend stores only the logical directory key in PostgreSQL, not the absolute path.

For each connection:

- generate a random session directory key
- generate a random TDLib database key
- encrypt the database key with an app-level AES key from configuration
- persist the encrypted database key

At runtime the infrastructure layer decrypts the database key only when creating or reopening the TDLight client for that user.

## Focus Resolution Strategy

When multiple focus modes are active, use a deterministic fixed priority:

1. `sleep`
2. `do_not_disturb`
3. `reduce_interruptions`
4. `airplane`
5. `personal`

If two candidates could tie in future changes, use the most recent `last_activated_at` as the tiebreaker.

This strategy must be implemented in one focused resolver so the same behavior is used by:

- automation updates
- connection-state summaries
- future resync jobs if added later

## Default Emoji Mapping

The backend should ship sensible defaults for supported modes through configuration, for example:

- `app.telegram.default-focus-mappings.personal`
- `app.telegram.default-focus-mappings.airplane`
- `app.telegram.default-focus-mappings.do-not-disturb`
- `app.telegram.default-focus-mappings.reduce-interruptions`
- `app.telegram.default-focus-mappings.sleep`

User overrides take precedence over these defaults.

The backend must not require every user to configure mappings before the feature works.

## API Design

All authenticated endpoints require the normal `JSESSIONID` session cookie.

### `GET /api/profile/me/telegram`

Purpose:

- return Telegram connection summary, premium capability, token metadata, effective focus state, default status, and resolved mappings

Example response:

```json
{
  "connected": true,
  "connectionStatus": "CONNECTED",
  "telegramUser": {
    "id": 123456789,
    "phoneNumber": "+15551234567",
    "username": "demo_user",
    "displayName": "Demo User",
    "premium": true
  },
  "pendingAuth": null,
  "automationToken": {
    "present": true,
    "tokenHint": "tgf_9K2...",
    "createdAt": "2026-03-26T10:15:30Z",
    "lastUsedAt": "2026-03-26T11:00:00Z"
  },
  "defaultEmojiStatusDocumentId": "5373141891321699086",
  "effectiveFocusMode": "sleep",
  "resolvedEmojiMappings": {
    "personal": "5368324170671202286",
    "airplane": "5368324170671202290",
    "do_not_disturb": "5368324170671202295",
    "reduce_interruptions": "5368324170671202300",
    "sleep": "5368324170671202305"
  },
  "activeFocusModes": [
    "sleep"
  ]
}
```

### `POST /api/profile/me/telegram/connect/start`

Request:

```json
{
  "phoneNumber": "+15551234567"
}
```

Behavior:

- create or replace pending auth state for the current user
- initialize TDLight authorization
- request Telegram to send a login code
- return a pending auth id and `nextStep`

Example response:

```json
{
  "pendingAuthId": "telegram-auth-opaque-id",
  "nextStep": "CODE"
}
```

### `POST /api/profile/me/telegram/connect/confirm-code`

Request:

```json
{
  "pendingAuthId": "telegram-auth-opaque-id",
  "code": "12345"
}
```

Responses:

- success with `CONNECTED`
- success with `nextStep = PASSWORD` when Telegram 2FA is required

### `POST /api/profile/me/telegram/connect/confirm-password`

Request:

```json
{
  "pendingAuthId": "telegram-auth-opaque-id",
  "password": "telegram-2fa-password"
}
```

Behavior:

- complete Telegram authorization
- persist the durable account-connection record
- capture the current Telegram emoji status as the initial default no-focus status when one is present

### `DELETE /api/profile/me/telegram/connect`

Behavior:

- disconnect Telegram and close session access
- keep focus preferences and focus-state rows intact
- do not implicitly wipe focus mappings or automation token

### `PUT /api/profile/me/telegram/focus-settings`

Request:

```json
{
  "defaultEmojiStatusDocumentId": "5373141891321699086",
  "mappings": {
    "personal": "5368324170671202286",
    "airplane": "5368324170671202290",
    "do_not_disturb": "5368324170671202295",
    "reduce_interruptions": "5368324170671202300",
    "sleep": "5368324170671202305"
  }
}
```

Behavior:

- update the user’s default no-focus status
- upsert explicit per-mode overrides
- null value removes an override and falls back to backend defaults

### `POST /api/profile/me/telegram/automation-token`

Behavior:

- create the initial token only when none is active
- return the raw token once

Example response:

```json
{
  "token": "tgf_very_long_random_token",
  "tokenHint": "tgf_very..."
}
```

### `POST /api/profile/me/telegram/automation-token/regenerate`

Behavior:

- replace the existing token hash
- return the new raw token once

### `DELETE /api/profile/me/telegram/automation-token`

Behavior:

- revoke the active token
- subsequent automation requests fail until a new token is created

### `POST /api/telegram/focus-updates`

Authentication:

- bearer token only, using the personal automation token

Request:

```json
{
  "mode": "sleep",
  "active": true
}
```

Behavior:

- authenticate by token hash lookup
- rate-limit by user and token
- update the stored state for the mode
- recalculate the effective mode
- resolve the resulting emoji status
- attempt Telegram sync
- update `last_used_at`

Success response:

```json
{
  "applied": true,
  "effectiveFocusMode": "sleep",
  "appliedEmojiStatusDocumentId": "5368324170671202305"
}
```

This endpoint should stay deliberately minimal for iOS Shortcuts.

## Security and Token Handling

### Automation tokens

- generate at least 32 random bytes
- encode as URL-safe base64 or similar
- store only a SHA-256 hash
- store a short human hint for UI display
- compare using the hash result from normalized input
- raw token is returned only on create/regenerate

### Authenticated profile endpoints

- remain session-cookie protected

### Automation endpoint

- should not rely on session auth
- should accept `Authorization: Bearer <token>`
- should be explicitly permitted in Spring Security and protected by a dedicated authentication filter/interceptor or controller-level token service

### Rate limiting

The automation endpoint should be minimal, secure, and rate-limited.

Add a new protection flow such as `telegram-focus-update` with reasonable defaults, for example:

- request limit around 60 per minute per token/user
- no captcha

The identifier should be derived from the owning user id or token hash prefix after successful token resolution.

## Error Model

Stable error codes matter for this feature. Add Telegram-specific domain exceptions and map them to stable `ErrorResponse` codes such as:

- `TELEGRAM_PENDING_AUTH_NOT_FOUND`
- `TELEGRAM_AUTH_CODE_REQUIRED`
- `TELEGRAM_AUTH_PASSWORD_REQUIRED`
- `TELEGRAM_AUTH_FAILED`
- `TELEGRAM_ACCOUNT_ALREADY_CONNECTED`
- `TELEGRAM_NOT_CONNECTED`
- `TELEGRAM_PREMIUM_REQUIRED`
- `TELEGRAM_EMOJI_MAPPING_INVALID`
- `TELEGRAM_AUTOMATION_TOKEN_INVALID`
- `TELEGRAM_AUTOMATION_TOKEN_REVOKED`
- `TELEGRAM_SYNC_FAILED`

Status guidance:

- `400` for malformed requests or invalid step transitions
- `401` for missing/invalid automation token
- `404` for missing Telegram connection or pending auth when appropriate
- `409` for meaningful product conflicts such as non-premium emoji sync attempts or account-link conflicts
- `429` for protection throttling

## Orchestration Flows

### Telegram connection flow

1. Authenticated user submits phone number.
2. Backend creates pending auth state and initializes TDLight auth.
3. User submits code.
4. Backend either:
   - completes connection, or
   - moves to password-required state.
5. If needed, user submits password.
6. Backend persists the durable connection, premium capability, account summary, and initial default status.

### Focus update flow

1. iOS Shortcut sends `mode` and `active`.
2. Backend authenticates the token.
3. Backend rate-limits the request.
4. Backend updates stored focus state for the given mode.
5. Backend recalculates effective active mode.
6. Backend resolves the target emoji status from user override or defaults.
7. Backend updates Telegram via TDLight.
8. Backend records sync success or failure metadata.

## Testing Strategy

Add focused coverage at three levels:

### Unit tests

- focus priority resolution
- mapping/default fallback resolution
- automation token hashing and regeneration semantics
- Telegram application-service orchestration with fake Telegram client behavior

### Integration tests

- authenticated connect/start and step progression
- settings retrieval and update
- automation token create/regenerate/revoke
- token-authenticated focus updates
- persistence of focus state across multiple updates
- no-focus reset behavior
- disconnect preserving focus preferences

### Infrastructure tests

- Redis pending-auth store semantics
- Telegram automation token persistence rules
- TDLight adapter behavior behind a fake/test implementation where direct Telegram integration is impractical in tests

## Operational Notes

- TDLight requires native dependencies and a persistent session directory. The backend config and README must document the required runtime setup.
- Session directories should not be treated as transient temp files.
- The implementation should expose enough sync-error detail for operators and the UI without leaking sensitive Telegram auth data.

## Contract Classification

This is a **Non-breaking** API addition.

It adds new endpoints, new persistence, and new docs without changing existing request or response contracts.
