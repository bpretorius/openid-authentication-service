# Bruno collection - OB Authentication Service

## What is included

- OAuth token request (`client_credentials`)
- Client management endpoints under `/api/clients`
- IDP config management endpoints under `/api/clients/idp`

## Quick start

1. Open the `bruno/ob-authentication-service` folder in Bruno.
2. Select environment: `local`.
3. Run `Auth/01 Token - Client Credentials`.
4. Copy `access_token` from response into `accessToken` env var.
5. Run requests in `Clients` and `IDP` folders.

## Notes

- Protected `/api/clients/**` endpoints require authenticated access from Spring Security.
- Runtime business authorization (resource-level roles/permissions) is handled by OpenFGA.
- `Create` and `Update` requests contain sample payloads; adjust values for your setup.

