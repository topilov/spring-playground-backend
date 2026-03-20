# Email Verification Design

## Summary

Add email verification to registration. New accounts stay unverified until the frontend submits a token from the verification email to the backend. Login is blocked for unverified users.

## Decisions

- Persist the verification state in PostgreSQL as `auth_user.email_verified`.
- Store verification tokens in Redis with TTL instead of a relational token table.
- Keep password reset storage unchanged.
- Send a verification email after registration instead of the previous welcome-only email.
- Add a public resend endpoint that does not leak whether an account exists.
- Return `401 Unauthorized` with a machine-readable error code when login is rejected because the email is not verified.

## API

### `POST /api/auth/register`

- Creates the user and profile.
- Sets `emailVerified = false`.
- Sends a verification email with a frontend URL containing the raw token.
- Does not create a session.

### `POST /api/auth/verify-email`

- Public endpoint.
- Request body: `{ "token": "..." }`.
- Looks up the token in Redis.
- Marks the user email as verified in PostgreSQL.
- Invalidates the token after successful use.

### `POST /api/auth/resend-verification-email`

- Public endpoint.
- Request body: `{ "email": "user@example.com" }`.
- Returns the same accepted response for existing, missing, and already verified accounts.
- For an existing unverified account, issues a fresh Redis token and sends a new verification email.

### `POST /api/auth/login`

- Rejects unverified accounts with `401 Unauthorized`.
- Error body includes a stable machine-readable code for the frontend CTA.

## Redis Model

- Token key stores the user id and expires automatically via TTL.
- User-scoped key stores the latest active token so resend can invalidate the previous one before issuing a new token.
- Successful verification deletes both keys.

## Notes

- This is a `Breaking` API behavior change because a newly registered user can no longer log in before confirming email.
- `docs/contracts/auth.md` and `openapi/openapi.yaml` must be updated in the same task.
