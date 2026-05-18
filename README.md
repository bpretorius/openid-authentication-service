Please find the Spring Authorisation Server documentation here: https://docs.spring.io/spring-authorization-server/reference/getting-started.html

## OpenFGA Authorization Split

This platform now follows a clear responsibility split:

- Spring Authorization Server + external IDPs manage authentication
- OpenFGA manages roles and permissions dynamically (authorization)

Use `OPENFGA_AUTHORIZATION_MODEL.md` for the runtime flow, sample OpenFGA model, tuple examples, and integration guidance.

## 📚 Multi-Tenancy Architecture Documentation

**👈 New to this codebase?** Start here: [ARCHITECTURE_DOCUMENTATION_INDEX.md](./ARCHITECTURE_DOCUMENTATION_INDEX.md)
- Navigation guide for all documentation
- Choose your reading path based on your role
- Quick reference by concept

### Your Question Answered

**How is the client request associated to tenant and registration_id?**
→ See [ANSWER_TO_YOUR_QUESTION.md](./ANSWER_TO_YOUR_QUESTION.md) (direct answer in 3 pages)

### Documentation Files

This application implements multi-tenant external IDP (Identity Provider) support with internal OAuth2 client management.

Choose your starting point based on your needs:

#### 🎯 Direct Answer
- **[ANSWER_TO_YOUR_QUESTION.md](./ANSWER_TO_YOUR_QUESTION.md)** - Direct answers to your specific question, database mapping, what gets stored where

#### 🚀 Getting Started
- **[OPENFGA_AUTHORIZATION_MODEL.md](./OPENFGA_AUTHORIZATION_MODEL.md)** - Authentication vs authorization split and OpenFGA tuple/check model
- **[TENANT_AND_CLIENT_QUICK_REFERENCE.md](./TENANT_AND_CLIENT_QUICK_REFERENCE.md)** - Simplified explanations, real-world examples, 5-10 min read
- **[VISUAL_ARCHITECTURE_DIAGRAMS.md](./VISUAL_ARCHITECTURE_DIAGRAMS.md)** - ASCII diagrams showing layers, OAuth flow, token flow, database relationships

#### 💻 Implementation Details
- **[CODE_IMPLEMENTATION_REFERENCE.md](./CODE_IMPLEMENTATION_REFERENCE.md)** - Exact file locations, code snippets, repository implementations, trace from request to database

#### 📖 Complete Architecture
- **[MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md](./MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md)** - Full deep-dive covering all concepts, examples with multiple organizations, enhancement opportunities

### Quick Summary

| Concept | Stored In | Your Request | Notes |
|---------|-----------|--------------|-------|
| **Tenant** | `customer_idp_config.tenant_id` | ❌ Not specified | Organization boundary (hotel_chain_1, airline_x) |
| **Registration ID** | `customer_idp_config.registration_id` | ❌ Not specified | External provider name (google, azure) |
| **Client ID** | `client.clientId` | ✅ "demo-client" | Your internal app identifier |
| **Multi-Tenancy** | Implicit via IDP selection | Shared across tenants | Same app serves all customers via different IDPs |

## IMPORTANT CLASSES AND CONCEPTS:
-----------------
client:
-------
This is the user store for 
- natural persons(Authorization Code grant type),
- machine-to-machine communication server clients (Client Credentials grant type)
- registered Apps that are authorised to request tokens. 

- Relevant columns:
- provider: whether the user is an internally registered user or the provider name of an external IDP
- mfa_enabled: whether a client is enabled for MFA
- client_authentication_methods: the methods that they client can request authentication, e.g. client_secret_jwt,client_secret_basic,client_secret_post. FYI: none means all
- authorization_grant_types: grant types that the client can request a token using, e.g.: client_credentials, refresh_token,authorization_code
- scopes: user roles and permissions

customer_idp_config:
--------------------
Registered external federated IDP's, e.g. Azure, Google, or an external organisations own IDP etc..)

jwk_keys:
---------
Security token to generate encrypted tokens.

authorization:
--------------
THe actual issues authorisation tokens.  There is a backend task that deletes these tokens after 24 hours to clean up the db.

authorization_consent:
----------------------
Not used, but this is the consent when requesting access from an external federated idp, e.g. request access to username, email, etc...


IMPORTANT CLASSES:
------------------

SecurityConfig.class
--------------------
This class contains the configuration for accessing the API endpoints and for managing the OAuth2
Security config for both UserNameAndPassword and Federated IDP 
(i.e. trusted registered external IDP's e.g. Azure, Google, or an external organisations own IDP etc..)

AuthorizationServerConfig.class
-------------------------------
This is where you configure the the JWT token that is returned.

MFAHandler
----------
If MFA(Multi Factor Authentication) is enabled for a client, 
then this class handles the registration and MFA authorisation flow

RELEVANT OAuth 2.1 ENDPOINTS:
-----------------------------
- http://<host>:<port>/.well-known/openid-configuration
- http://<host>:<port>/oauth2/authorize			Used to initiate the Authorization Code Flow (for SPA, web apps, etc.)
- http://<host>:<port>/oauth2/token				Used to exchange the authorization code for an access token
- http://<host>:<port>/userinfo					Returns user claims for authenticated users (typically for OIDC) FYI: You must implement and expose this endpoint manually using a controller if you're using Spring Authorization Server.
- http://<host>:<port>/connect/logout			Supports OpenID Connect logout (if implemented)
- http://<host>:<port>/oauth2/revoke			Allows clients to revoke access or refresh tokens

LOCALHOST HTTPS (LOCAL PROFILE):
-------------------------------
`application-local.yml` is configured to run the auth service with TLS on port `8443`.

Generate a local PKCS12 keystore:

```bash
mkdir -p certs/local
keytool -genkeypair \
  -alias localhost \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore certs/local/localhost.p12 \
  -validity 3650 \
  -storepass changeit \
  -keypass changeit \
  -dname "CN=localhost, OU=Dev, O=Local, L=Cape Town, ST=WC, C=ZA" \
  -ext SAN=DNS:localhost,IP:127.0.0.1
```

Run locally:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Test endpoint:

`https://localhost:8443/.well-known/openid-configuration`

LOCAL GOOGLE SSO TROUBLESHOOTING:
-------------------------------
If Google login fails locally with `This browser or app may not be secure`, test in Safari first.

Notes:
- In local development, browser context matters. Embedded IDE/webview browser sessions are often rejected.
- Prefer opening login links in a full external browser.
- Keep Google OAuth redirect URIs exactly aligned with the active profile.

Profile/redirect URI mapping:
- `local` (HTTPS): `https://localhost:8443/login/oauth2/code/google`
- `local,local-http` (HTTP): `http://localhost:8081/login/oauth2/code/google`

Run commands:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local,local-http
```

Quick validation:
- Start the service.
- Open `/sso` and enter `google` as the registration id.
- Confirm redirect reaches Google with the expected `redirect_uri` for the active profile.

DEFAULT URL CHECKS:
-------------------
Use these checks after startup to confirm local routing and OIDC metadata are working.

HTTP profile checks (`local,local-http`):

```bash
curl -i http://localhost:8081/
curl -i http://localhost:8081/login
curl -i http://localhost:8081/.well-known/openid-configuration
curl -i "http://localhost:8081/oauth2/authorization?registration_id=google"
```

HTTPS profile checks (`local`):

```bash
curl -k -i https://localhost:8443/
curl -k -i https://localhost:8443/login
curl -k -i https://localhost:8443/.well-known/openid-configuration
curl -k -i "https://localhost:8443/oauth2/authorization?registration_id=google"
```

Expected behavior:
- `/` returns `302` redirect to `/login`.
- `/login` returns `200` and renders the login page.
- `/.well-known/openid-configuration` returns `200` JSON metadata.
- `/oauth2/authorization?registration_id=google` returns `302` to `https://accounts.google.com/...`.

