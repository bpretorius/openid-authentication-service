# Visual Architecture Diagrams

This document contains ASCII diagrams for understanding the multi-tenant architecture.

> Authorization update: keep authentication in Spring Auth + IDPs, and perform runtime authorization decisions in OpenFGA.

---

## Master Architecture Diagram

```
╔════════════════════════════════════════════════════════════════════════════════╗
║                  SPRING AUTHORIZATION SERVER (THIS APPLICATION)               ║
╚════════════════════════════════════════════════════════════════════════════════╝

┌──────────────────────────────────────────────────────────────────────────────┐
│ LAYER 1: EXTERNAL IDENTITY PROVIDERS (NOT IN DATABASE, External Services)   │
│                                                                              │
│  ┌─────────────────┐    ┌──────────────────┐    ┌────────────────┐        │
│  │   Google        │    │   Azure AD       │    │   Okta         │        │
│  │   OAuth 2.0     │    │   OIDC           │    │   OIDC         │        │
│  └─────────────────┘    └──────────────────┘    └────────────────┘        │
└──────────────────────────────────────────────────────────────────────────────┘
                                    ▲
                                    │
┌──────────────────────────────────────────────────────────────────────────────┐
│ LAYER 2: TENANT IDP CONFIGURATIONS (Database: customer_idp_config)          │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ Tenant: "hotel_chain_1"                                              │  │
│  │ Registration ID: "google"                                            │  │
│  │ External Client ID: "chain1-google.apps.googleusercontent.com"      │  │
│  │ External Client Secret: "[encrypted]"                               │  │
│  │ Authorization URI: "https://accounts.google.com/o/oauth2/v2/auth"  │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ Tenant: "hotel_chain_2"                                              │  │
│  │ Registration ID: "azure"                                             │  │
│  │ External Client ID: "chain2.onmicrosoft.com"                         │  │
│  │ External Client Secret: "[encrypted]"                               │  │
│  │ Authorization URI: "https://login.microsoftonline.com/.../oauth2"  │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ Tenant: "airline_x"                                                  │  │
│  │ Registration ID: "google"                                            │  │
│  │ External Client ID: "airline-x-google.apps.googleusercontent.com"  │  │
│  │ External Client Secret: "[encrypted]"                               │  │
│  │ Authorization URI: "https://accounts.google.com/o/oauth2/v2/auth"  │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ▼ Each row = (tenant_id, registration_id) pair pointing to external IDP    │
└──────────────────────────────────────────────────────────────────────────────┘
                                    ▲
                                    │ (Used during federated login)
                                    │
┌──────────────────────────────────────────────────────────────────────────────┐
│ LAYER 3: INTERNAL OAUTH2 APPLICATIONS (Database: client)                    │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ Application: "hotel-reservation-spa"                                 │  │
│  │ Client ID: "hotel-reservation-spa"                                  │  │
│  │ Client Name: "Hotel Reservation System"                              │  │
│  │ Redirect URIs: ["https://hotel-spa.example.com/oauth/callback"]   │  │
│  │ Scopes: ["read:hotel", "write:booking"]                             │  │
│  │ MFA Enabled: true                                                   │  │
│  │ Provider: "internal"                                                │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ Application: "mobile-app"                                            │  │
│  │ Client ID: "mobile-app"                                              │  │
│  │ Client Name: "Mobile App"                                            │  │
│  │ Redirect URIs: ["com.hotel://oauth/callback"]                        │  │
│  │ Scopes: ["read:hotel"]                                               │  │
│  │ Grant Types: ["authorization_code"]                                  │  │
│  │ Provider: "internal"                                                │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ▼ Applications can be used by ANY tenant (not tenant-specific)             │
└──────────────────────────────────────────────────────────────────────────────┘
                                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ LAYER 4: AUTHORIZATION & TOKENS (Database: authorization)                   │
│                                                                              │
│  When a user logs in via tenant-specific IDP:                               │
│  ├─ Token issued to the internal client (hotel-reservation-spa, etc)      │
│  │                                                                         │
│  └─ Token claims include:                                                 │
│     ├─ sub (subject): "user@hotel-chain-1.com"   ← from external IDP      │
│     ├─ aud (audience): "hotel-reservation-spa"   ← internal client        │
│     ├─ scope: "read:hotel write:booking"         ← client's scopes        │
│     └─ custom claims: (organization, tenant context, roles, etc)         │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Request Flow: Complete Walk-Through

```
USER (Hotel Guest) at hotel-spa.example.com
├─ Clicks "Sign In" Button
│
└─ Frontend redirects to: https://auth-server.example.com/login
   │
   └─ Shows Login Page with Organization Selection:
      ├─ Hotel Chain 1 (uses Google)
      ├─ Hotel Chain 2 (uses Azure)
      └─ Airline X (uses Google)
   
   User selects: "Hotel Chain 1"
   │
   └─ Frontend submits: POST /sso?sso_registration_id=google
      
      ↓↓↓ AUTH SERVER PROCESSING ↓↓↓
      
      ├─ LoginController.postSSO(registrationId="google")
      │
      ├─ Queries: customer_idp_config WHERE registration_id='google' AND tenant_id='hotel_chain_1'
      │
      ├─ Returns:
      │  {
      │    tenant_id: "hotel_chain_1",
      │    registration_id: "google",
      │    client_id: "chain1-google.apps.googleusercontent.com",
      │    client_secret: "[decrypted]",
      │    authorization_uri: "https://accounts.google.com/o/oauth2/v2/auth",
      │    ...
      │  }
      │
      ├─ Builds Spring ClientRegistration with Google's OAuth credentials
      │
      └─ Returns: 302 Redirect to /oauth2/authorization?registration_id=google
      
         ↓↓↓ SPRING OAUTH2 CLIENT ↓↓↓
         
         ├─ Constructs URL: https://accounts.google.com/o/oauth2/v2/auth
         │    ?client_id=chain1-google.apps.googleusercontent.com
         │    &redirect_uri=https://auth-server.example.com/login/oauth2/code/google
         │    &scope=openid+profile+email
         │    &response_type=code
         │    &state=STATE_VALUE
         │
         └─ 302 Redirect to Google
            
            ↓↓↓ GOOGLE'S OAUTH ↓↓↓
            
            ├─ Google shows login/consent screen
            │
            ├─ User signs in with their Google account
            │
            └─ Google redirects back to: 
               https://auth-server.example.com/login/oauth2/code/google
               ?code=AUTH_CODE&state=STATE_VALUE
               
               ↓↓↓ AUTHORIZATION CODE EXCHANGE ↓↓↓
               
               ├─ Spring extracts AUTH_CODE and STATE from URL
               │
               ├─ Spring validates STATE matches session
               │
               ├─ Spring calls Google token endpoint:
               │  POST https://oauth2.googleapis.com/token
               │  {
               │    code: AUTH_CODE,
               │    client_id: "chain1-google.apps.googleusercontent.com",
               │    client_secret: "[from customer_idp_config]",
               │    redirect_uri: "...",
               │    grant_type: "authorization_code"
               │  }
               │
               ├─ Google responds with:
               │  {
               │    id_token: "eyJhbGc...[JWT with user claims]...",
               │    access_token: "...",
               │    refresh_token: "..." (optional),
               │    expires_in: 3600
               │  }
               │
               ├─ Spring decodes ID Token (using key from Google's JWKS endpoint)
               │
               ├─ Verifies signature & issuer
               │
               ├─ Extracts user claims:
               │  {
               │    iss: "https://accounts.google.com",
               │    sub: "123456789",
               │    email: "user@hotel-chain.com",
               │    email_verified: true,
               │    name: "John Doe",
               │    picture: "https://...",
               │    aud: "chain1-google.apps.googleusercontent.com",
               │    ...
               │  }
               │
               └─ Creates Security Principal with user info
               
                  ↓↓↓ TOWER AUTHORIZATION ↓↓↓
                  
                  ├─ User is now authenticated
                  │
                  ├─ Checks if token should be issued to a client
                  │
                  ├─ If yes, looks up internal client:
                  │  SELECT * FROM client WHERE clientId = 'hotel-reservation-spa'
                  │
                  ├─ Client found:
                  │  {
                  │    clientId: "hotel-reservation-spa",
                  │    scopes: "read:hotel,write:booking",
                  │    redirectUris: "https://hotel-spa.example.com/oauth/callback",
                  │    ...
                  │  }
                  │
                  ├─ Checks redirect_uri is in allowed list
                  │
                  ├─ Generates authorization code (short-lived, ~5-10min)
                  │
                  ├─ Stores in authorization table
                  │
                  └─ 302 Redirect to:
                     https://hotel-spa.example.com/oauth/callback
                     ?code=AUTHORIZATION_CODE
                     &state=STATE_VALUE
                     
                     ↓↓↓ FRONT-END CLIENT ↓↓↓
                     
                     ├─ Frontend receives AUTHORIZATION_CODE
                     │
                     ├─ Frontend calls backend (hotel-spa backend):
                     │  POST /auth/token
                     │  {
                     │    code: AUTHORIZATION_CODE,
                     │    state: STATE_VALUE
                     │  }
                     │
                     ├─ Backend calls Auth Server's /oauth2/token:
                     │  POST https://auth-server.example.com/oauth2/token
                     │  {
                     │    grant_type: "authorization_code",
                     │    code: AUTHORIZATION_CODE,
                     │    client_id: "hotel-reservation-spa",
                     │    client_secret: "secret-xyz",
                     │    redirect_uri: "https://hotel-spa.example.com/oauth/callback"
                     │  }
                     │
                     ├─ Auth Server exchanges code for token:
                     │  ├─ Validates AUTHORIZATION_CODE exists and hasn't expired
                     │  ├─ Validates client_id matches
                     │  ├─ Validates redirect_uri matches
                     │  ├─ Validates client credentials
                     │  └─ Invalidates AUTHORIZATION_CODE (can't be reused)
                     │
                     ├─ Auth Server generates access token & refresh token:
                     │  {
                     │    access_token: "eyJhbGc...[JWT]...",
                     │    token_type: "Bearer",
                     │    expires_in: 3600,
                     │    refresh_token: "...",
                     │    scope: "read:hotel write:booking"
                     │  }
                     │
                     ├─ Stores token in authorization table
                     │
                     ├─ Returns tokens to backend
                     │
                     ├─ Backend stores access token in HTTP-Only cookie or session
                     │
                     └─ Frontend can now make authenticated requests:
                        GET /api/hotels
                        Authorization: Bearer eyJhbGc...[JWT]...
```

---

## Multi-Tenant Isolation Pattern

```
┌─────────────────────────────────────────────────────────────────┐
│                  SAME INTERNAL CLIENT                           │
│         (hotel-reservation-spa registered once)                 │
└─────────────────────────────────────────────────────────────────┘
                            ▲
                    ┌───────┴───────┐
                    │               │
        ┌───────────▼───────────┐  ┌───────────▼───────────┐
        │   Hotel Chain 1       │  │   Hotel Chain 2       │
        │   (Tenant ID: HC1)    │  │   (Tenant ID: HC2)    │
        │                       │  │                       │
        │ IDP Config:           │  │ IDP Config:           │
        │ ├─ reg_id: google     │  │ ├─ reg_id: azure      │
        │ ├─ client_id: gc1     │  │ ├─ client_id: ac2     │
        │ └─ endpoints: G       │  │ └─ endpoints: MS      │
        └───────────┬───────────┘  └───────────┬───────────┘
                    │ (IDP selection)         │ (IDP selection)
                    │                         │
        ┌───────────▼───────────────────────────▼───────────────┐
        │                                                       │
        │  OAuth Flow                                          │
        │  1. User selects organization/IDP                   │
        │  2. Auth server fetches IDP config from DB          │
        │  3. Redirects to Google/Azure with IDP creds        │
        │  4. After auth, returns to hotel-reservation-spa    │
        │  5. Token issued to hotel-reservation-spa client    │
        │     with user identity from Google/Azure            │
        │                                                       │
        └───────────────────────────────────────────────────────┘

KEY POINT:
==================
Both tenants use the SAME internal client ("hotel-reservation-spa"),
but each tenant has DIFFERENT IDP configurations.

User identity is SCOPED by which IDP they authenticate through,
not by a tenant_id column in the client table.

This is the current multi-tenancy model.

ALTERNATIVE (not currently implemented):
==================
Add tenant_id to client table → Register hotel-spa PER TENANT
├─ hotel-reservation-spa-hc1 (client_id) for Hotel Chain 1
└─ hotel-reservation-spa-hc2 (client_id) for Hotel Chain 2

Then enforce:
  SELECT client WHERE clientId=X AND tenantId=Y
```

---

## Database Relationships

```
┌─────────────────────────────────────────────────────────────────┐
│                    customer_idp_config                          │
│                  (Tenant IDP Configurations)                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  id (PK)             int                                        │
│  tenant_id           varchar(100)  ◄─── UNIQUE per org         │
│  registration_id     varchar(100)  ◄─── IDP provider name      │
│  client_id           varchar(255)  ◄─── EXTERNAL provider's ID │
│  client_secret       varchar(255)  ◄─── ENCRYPTED external pw  │
│  issuer_uri          varchar(255)                              │
│  authorization_uri   varchar(255)                              │
│  token_uri           varchar(255)                              │
│  jwk_set_uri         varchar(255)                              │
│  user_info_uri       varchar(255)                              │
│  user_name_attribute varchar(100)                              │
│  scope               varchar(255)  ◄─── "openid profile email" │
│  created_at          timestamp                                 │
│  updated_at          timestamp                                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                    │
                    │ (UNIQUE: registration_id per system)
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│                     client                                       │
│              (Internal OAuth2 Applications)                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  id (PK)                       varchar(255)  ◄─── UUID         │
│  provider                      varchar(255)  ◄─── "internal"   │
│  client_id (UNIQUE)            varchar(255)  ◄─── App ID       │
│  client_secret                 varchar(255)  ◄─── BCRYPT       │
│  client_name                   varchar(255)                    │
│  client_authentication_...     varchar(1000)                   │
│  authorization_grant_types     varchar(1000)                   │
│  redirect_uris                 varchar(1000)                   │
│  post_logout_redirect_uris     varchar(1000)                   │
│  scopes                        varchar(1000)                   │
│  client_settings               varchar(2000)  ◄─── JSON        │
│  token_settings                varchar(2000)  ◄─── JSON        │
│  mfa_enabled                   boolean                         │
│  mfa_secret                    varchar(255)  ◄─── ENCRYPTED    │
│  │                                                              │
│  ⚠️  NOTE: NO tenant_id column here!                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                    ▲
                    │ (FOREIGN KEY from authorization)
                    │
┌───────────────────┴─────────────────────────────────────────────┐
│                  authorization                                  │
│          (Issued Tokens & Auth Sessions)                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  id (PK)                varchar(255)                           │
│  registered_client_id   varchar(255)  ◄─── FK to client.id    │
│  principal_name         varchar(255)  ◄─── "user@..." from    │
│  authorization_grant_type  varchar(255)                        │
│  access_token_value     text          ◄─── JWT token issued   │
│  access_token_issued_at timestamp                              │
│  access_token_expires_at timestamp                             │
│  refresh_token_value    text                                   │
│  refresh_token_issued_at timestamp                             │
│  oidc_id_token_value    text          ◄─── ID token from org  │
│  oidc_id_token_claims   varchar(2000) ◄─── User claims        │
│                                                                 │
│  JOIN:                                                          │
│  client.id = authorization.registered_client_id                │
│  + Tenant context derived from IDP selection, not table col    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

RELATIONSHIP:
=============

NO DIRECT RELATIONSHIP between customer_idp_config ←→ client

They're independent tables that come together during login:

  1. User selects organization (implicit tenant context)
  2. System fetches IDP config from customer_idp_config
  3. User authenticates via IDP, gets user identity
  4. Token request specifies internal client_id
  5. System looks up client from client table
  6. Token issued to that client with federated user identity

The multi-tenancy is CONTEXTUAL (via IDP selection),
not RELATIONAL (no FK between tables).
```

---

## Token Flow Diagram

```
┌──────────────────────────────────────────────────────────────┐
│        AUTHORIZATION CODE FLOW (OAuth 2.0 / OIDC)           │
└──────────────────────────────────────────────────────────────┘

1. USER INITIATES LOGIN
   User @ hotel-spa.example.com clicks "Sign In"
   ↓
   Frontend picks IDP: "google" (from customer_idp_config.registration_id)
   ↓
   Frontend calls: POST /sso?sso_registration_id=google


2. AUTH SERVER REDIRECTS TO EXTERNAL IDP
   Spring lookups customer_idp_config where registration_id='google'
   ↓
   Builds: https://accounts.google.com/o/oauth2/v2/auth
             ?client_id=[from DB]
             &redirect_uri=https://auth-server/login/oauth2/code/google
             &scope=openid+profile+email
             &response_type=code
   ↓
   302 Redirect to Google


3. EXTERNAL IDP AUTHENTICATES USER
   User logs in @ Google
   ↓
   Google generates AUTHORIZATION_CODE
   ↓
   302 Redirect to: https://auth-server/login/oauth2/code/google?code=CODE&state=STATE


4. AUTH SERVER EXCHANGES CODE FOR TOKENS
   Spring receives AUTHORIZATION_CODE
   ↓
   POSTs to Google's token endpoint with:
   ├─ client_id (from customer_idp_config)
   ├─ client_secret (from DB, decrypted)
   ├─ code (received from Google)
   └─ redirect_uri (matches what was sent)
   ↓
   Google verifies and responds:
   ├─ id_token (JWT with user claims: sub, email, name, etc)
   ├─ access_token
   └─ refresh_token (maybe)


5. AUTH SERVER VALIDATES ID TOKEN & CREATES PRINCIPAL
   Spring validates JWT signature (using public key from Google's JWKS endpoint)
   ↓
   Verifies issuer, audience, expiry
   ↓
   Extracts user claims:
   ├─ sub: "123456789"
   ├─ email: "user@hotel-chain.com"
   ├─ email_verified: true
   ├─ name: "John Doe"
   └─ aud: (from Google config)
   ↓
   Creates SecurityContext with authenticated principal


6. AUTH SERVER ISSUES ITS OWN TOKEN (if needed)
   Client requests: POST /oauth2/token
                    {
                      grant_type: "authorization_code",
                      code: AUTHORIZATION_CODE,
                      client_id: "hotel-reservation-spa",
                      client_secret: "secret-xyz",
                      redirect_uri: "https://hotel-spa/callback"
                    }
   ↓
   Auth Server validates:
   ├─ AUTHORIZATION_CODE not expired, not already used
   ├─ client_id matches (lookup in client table)
   ├─ client_secret matches
   ├─ redirect_uri in allowed list
   └─ All checks pass
   ↓
   Auth Server generates JWT ACCESS TOKEN:
   {
     iss: "https://auth-server",
     aud: "hotel-reservation-spa",         ← from client_id lookup
     sub: "123456789",                     ← from federated IDP
     email: "user@hotel-chain.com",        ← from federated IDP
     scope: "read:hotel write:booking",    ← from client.scopes
     iat: 1234567890,
     exp: 1234571490,
     ...
   }
   ↓
   Stores token in authorization table:
   ├─ registered_client_id: (FK to client.id)
   ├─ principal_name: "user@hotel-chain.com"
   ├─ access_token_value: "[JWT]"
   ├─ access_token_issued_at: NOW
   └─ oidc_id_token_value: (optional, ID token from federated IDP)
   ↓
   Returns to client:
   {
     access_token: "[JWT]",
     token_type: "Bearer",
     expires_in: 3600,
     refresh_token: "[optional]",
     scope: "read:hotel write:booking"
   }


7. FRONTEND MAKES AUTHENTICATED REQUESTS
   Frontend stores access token (HTTP-Only cookie or memory)
   ↓
   GET /api/hotels
   Authorization: Bearer [JWT]
   ↓
   Backend validates JWT signature (using Auth Server's public key)
   ↓
   Extracts claims, verifies scope/exp, grants access
```

---

## Registration ID Resolution

```
SCENARIO: Multiple providers available, same name used by different tenants

Database:
┌─────────────────────────────────────────────────────────────┐
│ customer_idp_config                                         │
├─────┬──────────┬────────────┬──────────┬──────────────────┤
│ id  │ tenant_id│registration_id │ client_id  │ issuer_uri │
├─────┼──────────┼────────────┼──────────┼──────────────────┤
│ 1   │ hotel_c1 │ google     │ gc1.apps │ accounts.google  │
│ 2   │ hotel_c2 │ google     │ gc2.apps │ accounts.google  │
│ 3   │ airline_x│ google     │ gax.apps │ accounts.google  │
│ 4   │ hotel_c1 │ azure      │ ac1      │ login.microsft   │
└─────┴──────────┴────────────┴──────────┴──────────────────┘

LOOKUP SCENARIOS:

Scenario A: User at Hotel Chain 1 selects Google
                    ↓
POST /sso?sso_registration_id=google
(+ implicit tenant context: hotel_c1)
                    ↓
Query: SELECT * FROM customer_idp_config
       WHERE registration_id = 'google'
       AND tenant_id = 'hotel_c1'
                    ↓
Result: Row 1 → client_id=gc1.apps, issuer_uri=accounts.google
                    ↓
Redirect to Google with gc1.apps credentials

┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄

Scenario B: User at Hotel Chain 2 selects Google
                    ↓
POST /sso?sso_registration_id=google
(+ implicit tenant context: hotel_c2)
                    ↓
Query: SELECT * FROM customer_idp_config
       WHERE registration_id = 'google'
       AND tenant_id = 'hotel_c2'
                    ↓
Result: Row 2 → client_id=gc2.apps, issuer_uri=accounts.google
                    ↓
Redirect to Google with gc2.apps credentials (different OAuth app!)

CURRENT LIMITATION:
===================
DynamicIDPRegistrationRepository.findByRegistrationId(registrationId)
only queries: WHERE registration_id = 'google'

This works if registration_ids are UNIQUE per tenant.
If multiple tenants share the same provider name (like scenario above),
the query returns only the FIRST match.

FIX: Pass tenant_id + registration_id to query.
```

---

## For Your Client Request

```
Your Request:
POST /api/clients
{
  "provider": "internal",
  "clientId": "demo-client",
  "clientSecret": "demo-secret",
  ...
}

┌──────────────────────────────────────────────────────────────┐
│ WHERE DOES IT GO?                                            │
└──────────────────────────────────────────────────────────────┘

              REQUEST
                ↓
        ClientController
                ↓
        ClientService.save(ClientRequest, id)
                ↓
        Build RegisteredClient (Spring Security)
                ↓
        CommonService.toEntity(RegisteredClient)
                ↓
        Convert to JPA Entity (Client)
                ↓
        WriterClientRepository.save(Client)
                ↓
        INSERT INTO client (
          id=UUID,
          provider="internal",
          client_id="demo-client",
          client_secret="[bcrypt encoded]",
          ...
        )
                ↓
        Reload Redis cache with new client
                ↓
        Return 204 No Content


WHAT IT DOES NOT TOUCH:
- customer_idp_config table (external IDP configs)
- No registration_id field
- No tenant_id field
- No relation to specific external IDPs

WHAT IT ENABLES:
- This client can receive tokens from ANY tenant
- When users authenticate via different IDPs and request a token,
  this client_id can be the audience
- The client's redirect_uris must match where users return after auth
```

---

See the documentation files for more details:
- [TENANT_AND_CLIENT_QUICK_REFERENCE.md](./TENANT_AND_CLIENT_QUICK_REFERENCE.md)
- [MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md](./MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md)
- [CODE_IMPLEMENTATION_REFERENCE.md](./CODE_IMPLEMENTATION_REFERENCE.md)

