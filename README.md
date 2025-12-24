# Onfilm

## Authentication
- Access tokens are sent **only** via `Authorization: Bearer <token>` headers.
- Refresh tokens are stored in an HttpOnly cookie named `refresh_token` scoped to `/auth`.
- Browsers automatically include the refresh cookie for `/auth/*` requests; JavaScript cannot read it.
- Refresh token rotation is enforced: each `/auth/refresh` revokes the old token and issues a new one.
- The refresh cookie is `Secure` in `prod` profile and non-secure in `dev`.
