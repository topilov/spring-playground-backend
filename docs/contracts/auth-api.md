# Auth API Contract

This file exists as a compatibility alias for integrations that still reference `docs/contracts/auth-api.md`.

The canonical auth contract lives in [auth.md](./auth.md).

That canonical document includes the current password, email-verification, password-reset, and passkey endpoints:

- `POST /api/auth/register`
- `POST /api/auth/verify-email`
- `POST /api/auth/resend-verification-email`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `POST /api/auth/login`
- `GET /api/auth/2fa/status`
- `POST /api/auth/2fa/setup/start`
- `POST /api/auth/2fa/setup/confirm`
- `POST /api/auth/2fa/backup-codes/regenerate`
- `POST /api/auth/2fa/disable`
- `POST /api/auth/2fa/login/verify`
- `POST /api/auth/2fa/login/verify-backup-code`
- `POST /api/auth/passkeys/register/options`
- `POST /api/auth/passkeys/register/verify`
- `GET /api/auth/passkeys`
- `PATCH /api/auth/passkeys/{id}`
- `DELETE /api/auth/passkeys/{id}`
- `POST /api/auth/passkey-login/options`
- `POST /api/auth/passkey-login/verify`
- `POST /api/auth/logout`

Use `auth.md` as the source of truth for request and response shapes, auth requirements, notes about `JSESSIONID`, and passkey/WebAuthn payload handling.
