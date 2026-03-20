# Passkey WebAuthn Design

## Summary

Add production-oriented WebAuthn passkey support to the existing session-based auth backend.

The feature must support two flows:

- authenticated users registering and managing multiple passkeys
- unauthenticated users signing in with a passkey and receiving the normal `JSESSIONID` session

The implementation should stay aligned with the current architecture: thin controllers, service-owned orchestration, JPA for durable auth data, Redis for short-lived ceremony state, explicit DTOs, Flyway migrations, and updated backend-owned API contracts.

## Goals

- Support WebAuthn registration for already authenticated users.
- Support WebAuthn assertion-based login for unauthenticated users.
- Persist passkey credentials in PostgreSQL with enough data for authentication and profile management.
- Support listing, renaming, and deleting passkeys for the current user.
- Preserve the existing session-based authentication model.
- Keep the implementation production-shaped and horizontally scalable.

## Non-Goals

- Replacing password login.
- Introducing a second auth model such as JWT or stateless bearer tokens.
- Building frontend WebAuthn UI logic in this repository.
- Attestation trust policy customization beyond a sane, production-leaning default.
- Enterprise device inventory or rich audit/event history.

## Existing Context

The project already has:

- session-backed login at `POST /api/auth/login`
- manual session establishment inside `AuthService`
- protected endpoints authorized through Spring Security and `JSESSIONID`
- JPA-backed `auth_user` and `user_profile`
- Redis-backed short-lived token flows for email verification and password reset
- contract docs in `docs/contracts/*` and machine-readable OpenAPI in `openapi/openapi.yaml`

This makes passkeys a good fit as an extension of the current `auth` module rather than a separate subsystem.

## Approaches Considered

### 1. Yubico `java-webauthn-server` with Redis ceremony state

Use a mature server-side WebAuthn library for registration and assertion verification, persist credentials in PostgreSQL, and keep ceremony state in Redis behind a small abstraction.

Pros:

- protocol validation stays inside a mature library
- clean fit with current JPA + Redis architecture
- supports horizontal scaling
- keeps controller and service code focused on orchestration

Cons:

- adds a new dependency and some adapter code around library models

### 2. WebAuthn4J with more custom orchestration

This is viable, but it typically pushes more low-level validation and ceremony wiring into application code.

Pros:

- mature ecosystem

Cons:

- more implementation complexity in this codebase
- weaker fit for a small, explicit auth module

### 3. Mostly custom protocol handling

Rejected.

This would create unnecessary security and maintenance risk and would not match the requirement to use a mature server-side WebAuthn library.

## Recommended Approach

Use Yubico `java-webauthn-server` as the WebAuthn core, add a dedicated `auth/passkey` slice for protocol and persistence logic, store ceremony state in Redis with a short TTL through a `PasskeyCeremonyStore` abstraction, and bridge successful passkey login into the same session-establishment path that password login already uses.

## Architecture

### Web Layer

Keep passkey endpoints under the auth area:

- `POST /api/auth/passkeys/register/options`
- `POST /api/auth/passkeys/register/verify`
- `GET /api/auth/passkeys`
- `PATCH /api/auth/passkeys/{id}`
- `DELETE /api/auth/passkeys/{id}`
- `POST /api/auth/passkey-login/options`
- `POST /api/auth/passkey-login/verify`

Controllers remain thin:

- validate DTOs
- resolve authenticated principal where required
- delegate to dedicated passkey services

### Service Layer

Introduce passkey-focused services under `auth/passkey/service`, likely split into:

- `PasskeyRegistrationService`
- `PasskeyAuthenticationService`
- `PasskeyManagementService`
- a shared session helper or a small extraction inside `AuthService` for creating the authenticated Spring Security session

Responsibilities:

- generating WebAuthn creation/assertion options
- persisting and consuming ceremony state
- verifying WebAuthn responses through the Yubico library
- persisting and updating passkey credentials
- enforcing ownership checks for management endpoints
- updating `lastUsedAt` on successful assertion
- creating the normal authenticated server-side session on successful passkey login

### Persistence Layer

Add a dedicated JPA entity and repository for passkeys, for example:

- `PasskeyCredential`
- `PasskeyCredentialRepository`

The entity belongs to an `AuthUser` and stores both authenticator material required for future assertions and user-facing metadata required by the profile UI.

### Ceremony State Layer

Add a small abstraction:

- `PasskeyCeremonyStore`

Runtime implementation:

- `RedisPasskeyCeremonyStore`

Test helper:

- `InMemoryPasskeyCeremonyStore`

The store should keep registration and authentication ceremonies separate by type and key namespace and should support:

- save pending ceremony
- load pending ceremony
- invalidate ceremony

The TTL should default to about 5 minutes and be configurable through application properties.

## API Design

### Authenticated Registration

#### `POST /api/auth/passkeys/register/options`

Authentication:

- required

Request body:

```json
{
  "nickname": "MacBook Touch ID"
}
```

Notes:

- `nickname` is optional; backend may default to a generated name such as `Passkey 1`

Behavior:

- load current user and their existing credential ids
- build WebAuthn registration options using the configured relying party
- persist a short-lived registration ceremony record in Redis keyed to the current user
- return public-key credential creation options plus a backend-generated `ceremonyId`

Suggested response shape:

```json
{
  "ceremonyId": "registration-opaque-id",
  "publicKey": {
    "rp": {
      "id": "localhost",
      "name": "Spring Playground"
    },
    "user": {
      "id": "base64url-user-handle",
      "name": "demo",
      "displayName": "demo"
    },
    "challenge": "base64url-challenge",
    "pubKeyCredParams": [
      { "type": "public-key", "alg": -7 },
      { "type": "public-key", "alg": -257 }
    ]
  }
}
```

#### `POST /api/auth/passkeys/register/verify`

Authentication:

- required

Request body:

- `ceremonyId`
- browser-provided WebAuthn attestation result
- optional final nickname if the frontend collects it here instead of in `options`

Behavior:

- load pending registration ceremony from Redis
- verify ownership by matching the authenticated user to the stored ceremony
- call Yubico registration verification
- persist a new passkey credential row
- invalidate the stored ceremony after successful verification
- return the created passkey summary

Failure cases:

- `400` for invalid or expired ceremony state
- `400` for malformed WebAuthn payload
- `409` if the credential is already registered
- `401` if the session is missing

### Passkey Management

#### `GET /api/auth/passkeys`

Authentication:

- required

Behavior:

- return only the current user’s passkeys ordered by creation time descending

Response item shape:

```json
{
  "id": 10,
  "name": "MacBook Touch ID",
  "createdAt": "2026-03-21T10:15:30Z",
  "lastUsedAt": "2026-03-21T12:05:00Z",
  "deviceHint": "platform",
  "transports": ["internal"]
}
```

`deviceHint` should remain optional and derived only from data the authenticator or browser actually supplies. The backend should not invent detailed device descriptions.

#### `PATCH /api/auth/passkeys/{id}`

Authentication:

- required

Request body:

```json
{
  "name": "Work Laptop"
}
```

Behavior:

- rename only a passkey owned by the current user
- return the updated summary

Failure cases:

- `401` if not authenticated
- `404` if the passkey does not exist for the current user
- `400` if validation fails

#### `DELETE /api/auth/passkeys/{id}`

Authentication:

- required

Behavior:

- delete only a passkey owned by the current user
- return `204 No Content`

Failure cases:

- `401` if not authenticated
- `404` if the passkey does not exist for the current user

### Unauthenticated Passkey Login

#### `POST /api/auth/passkey-login/options`

Authentication:

- not required

Request body:

```json
{}
```

Optional future extension:

- a `usernameOrEmail` hint could be added later, but it is not required for a discoverable-credential first version

Behavior:

- create WebAuthn assertion options for login
- persist a short-lived authentication ceremony in Redis
- return the assertion options plus a backend-generated `ceremonyId`

Suggested response shape:

```json
{
  "ceremonyId": "authentication-opaque-id",
  "publicKey": {
    "challenge": "base64url-challenge",
    "timeout": 300000,
    "userVerification": "preferred"
  }
}
```

#### `POST /api/auth/passkey-login/verify`

Authentication:

- not required

Request body:

- `ceremonyId`
- browser-provided WebAuthn assertion result

Behavior:

- load pending authentication ceremony from Redis
- look up the credential by credential id from PostgreSQL
- verify the assertion through the Yubico library
- update the stored signature counter and `lastUsedAt`
- ensure the owning account is enabled and still satisfies the same login invariants as password login, including email verification
- create the normal authenticated Spring Security session and persist it through the existing `SecurityContextRepository`
- invalidate the ceremony after successful verification
- return the same authenticated response shape as password login

Failure cases:

- `400` for invalid or expired ceremony state
- `401` for invalid assertion or disallowed login
- `404` should not be used to leak whether a credential exists

## Data Model

Add a Flyway migration creating a dedicated passkey table.

Suggested schema:

```sql
CREATE TABLE auth_passkey_credential (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES auth_user (id) ON DELETE CASCADE,
    credential_id BYTEA NOT NULL UNIQUE,
    public_key_cose BYTEA NOT NULL,
    signature_count BIGINT NOT NULL,
    aaguid UUID,
    transports VARCHAR(255),
    nickname VARCHAR(100) NOT NULL,
    label_source VARCHAR(32) NOT NULL,
    authenticator_attachment VARCHAR(32),
    discoverable BOOLEAN,
    backup_eligible BOOLEAN,
    backup_state BOOLEAN,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_auth_passkey_credential_user_id
    ON auth_passkey_credential (user_id);
```

Implementation notes:

- `credential_id` and `public_key_cose` should be stored as raw bytes
- `transports` can be stored as a comma-separated string or JSON string if the codebase does not already use array columns
- `nickname` is the user-editable label shown in the profile UI
- `label_source` can distinguish between backend-generated defaults and user-provided names if useful
- `authenticator_attachment`, `discoverable`, `backup_eligible`, and `backup_state` are optional metadata fields that help produce small device hints without storing private material

The exact column set can be slightly adjusted to fit library outputs, but the table must at minimum support:

- credential lookup for login
- public key and signature counter storage
- ownership and management queries
- multi-passkey per user
- `createdAt` and `lastUsedAt`

## Redis Ceremony Model

Use separate key namespaces for each ceremony type:

- `auth:passkey:registration:<ceremonyId>`
- `auth:passkey:authentication:<ceremonyId>`

Each stored value should include only the minimum data needed to safely finish the ceremony, such as:

- ceremony id
- ceremony type
- user id for registration ceremonies
- request options or the serialized library request object
- requested nickname if collected in the options step
- created-at timestamp

Rules:

- default TTL around 5 minutes
- successful verification must delete the ceremony state
- explicit invalidation should be possible for abandoned or superseded ceremonies
- missing or expired state should fail closed

## Security Model

- Only authenticated users may start or finish registration or manage passkeys.
- Management operations must always filter by the authenticated user id.
- Passkey login must produce the same authenticated session model as password login.
- Existing password login remains unchanged.
- WebAuthn credential material such as stored public keys must never be returned by the API.
- Login errors should avoid unnecessary credential enumeration.
- The same email verification gate used by password login should apply to passkey login.
- Current repository behavior disables CSRF globally; passkey endpoints should stay consistent with that existing configuration rather than creating a one-off exception.

## Session Integration

The current password login code creates a Spring Security `Authentication`, places it in a new security context, and persists that context through `SecurityContextRepository`.

Passkey login should reuse that same session-establishment behavior instead of creating a parallel mechanism.

A small internal extraction is appropriate, for example:

- move the common session persistence logic into a shared helper inside `AuthService`
- or add a narrow `SessionLoginService` used by both password and passkey login

The key requirement is one canonical way to create the authenticated session.

## Configuration

Add passkey/WebAuthn properties, likely under `application.yml`, for:

- relying party id
- relying party name
- allowed origins
- ceremony TTL

Reasonable local defaults should support development at `localhost`.

Example shape:

```yaml
app:
  passkeys:
    rp-id: localhost
    rp-name: Spring Playground
    origins:
      - http://localhost:3000
      - http://localhost:4173
    ceremony-ttl: 5m
```

These values should feed both the WebAuthn relying party configuration and the Redis ceremony store TTL.

## Testing Strategy

### Integration Tests

Add end-to-end MockMvc coverage for:

- authenticated registration options and verify flow
- list/rename/delete passkeys
- unauthenticated login options and verify flow
- ownership enforcement for rename/delete
- session creation after successful passkey login
- invalid or expired ceremony handling

### Service Tests

Add focused tests for:

- ceremony store behavior
- passkey persistence mapping
- registration duplicate handling
- authentication updating signature counter and `lastUsedAt`
- management queries limited to current user

### WebAuthn Testing Scope

Full cryptographic browser ceremonies are too heavy to reproduce in every integration test.

Recommended test balance:

- unit/service tests around orchestration with mocked or fake WebAuthn verifier adapters
- integration tests for HTTP status codes, session behavior, ownership checks, and persistence wiring

The library integration should still be exercised enough to validate the chosen request/response mapping and persistence path with realistic test fixtures where practical.

## Documentation Impact

Update in the same task:

- `docs/contracts/auth.md` as the canonical auth contract
- `docs/contracts/auth-api.md` so the requested path exists and remains aligned
- `openapi/openapi.yaml`
- `README.md`

The contract docs must clearly explain:

- passkey registration flow for authenticated users
- passkey login flow for unauthenticated users
- that frontend forwards browser WebAuthn payloads to the backend
- that successful passkey login creates the same server-side session as password login

## Change Classification

This feature is `Non-breaking`.

It adds new endpoints and a new login method without changing the existing password-login contract.
