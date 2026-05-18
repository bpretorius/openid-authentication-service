# Tenant, Registration ID, and Client Association - Quick Reference

> Authorization update: use Spring Auth/IDPs for identity and OpenFGA for dynamic role/permission checks.

## Your Question Answered

### Given Your Client Request:
```json
{
  "provider": "internal",
  "clientId": "demo-client",
  "clientSecret": "demo-secret",
  "clientName": "Demo Client",
  "aud": ["hotel_reservation"],
  ...
}
```

### Answers:

**Q: How is this associated to a tenant?**
- **A: NOT DIRECTLY.** This internal client is NOT explicitly tied to a tenant in the schema.
- Multi-tenancy is achieved by having **different external IDPs** (in `customer_idp_config`) pointing to different auth providers.
- When users log in via the `/sso` endpoint and select a `registration_id`, they implicitly select a tenant's IDP configuration.

**Q: Is the registration ID the external IDP provider?**
- **A: YES, effectively.** The `registration_id` identifies the external IDP provider (e.g., `"google"`, `"azure"`, `"okta"`).
- Each `registration_id` is mapped to a tenant in `customer_idp_config` via `tenant_id + registration_id` pairing.
- When a user initiates `/sso?sso_registration_id=google`, Spring looks up the Google IDP config.

---

## Entity Relationship Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  EXTERNAL IDP PROVIDER (e.g., Google, Azure)            │   │
│  │  (Not in database, but referenced)                      │   │
│  └─────────────────────────────────────────────────────────┘   │
│           │                                                     │
│           │ (OAuth2/OIDC protocol)                              │
│           │                                                     │
│           ▼                                                     │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  customer_idp_config                                   │   │
│  ├────────────────────────────────────────────────────────┤   │
│  │  id: integer                                           │   │
│  │  tenant_id: "hotel_chain_1"      ◄─── TENANT          │   │
│  │  registration_id: "google"       ◄─── PROVIDER NAME   │   │
│  │  client_id: "...apps.googleusercontent.com"           │   │
│  │  client_secret: "[encrypted]"                         │   │
│  │  issuer_uri: "https://accounts.google.com"            │   │
│  │  (+ other OAuth endpoints)                            │   │
│  └────────────────────────────────────────────────────────┘   │
│           │                                                     │
│           │ (Spring dynamically builds ClientRegistration)     │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  user selects IDP on login screen:                      │   │
│  │  "Sign in with Google" → POST /sso?sso_registration_id = │   │
│  │  "google"                                               │   │
│  └─────────────────────────────────────────────────────────┘   │
│           │                                                     │
│           │ (redirects to external IDP)                        │
│           │                                                     │
│           ▼                                                     │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  client (Internal OAuth2 Application)                  │   │
│  ├────────────────────────────────────────────────────────┤   │
│  │  id: "uuid"                                            │   │
│  │  provider: "internal"                                  │   │
│  │  clientId: "hotel-reservation-spa"                     │   │
│  │  clientSecret: "[bcrypt encoded]"                      │   │
│  │  redirectUris: ["https://hotel-spa.example.com/cb"] │   │
│  │  (+ scopes, auth methods, MFA settings)              │   │
│  └────────────────────────────────────────────────────────┘   │
│           │                                                     │
│           │ (after federated auth, user returns to this)       │
│           │                                                     │
│           ▼                                                     │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  authorization (Token issued by this Auth Server)     │   │
│  ├────────────────────────────────────────────────────────┤   │
│  │  id: "token-id"                                        │   │
│  │  registered_client_id: (foreign key to client.id)    │   │
│  │  principal_name: "user@example.com"                   │   │
│  │  access_token_value: "jwt-token"                      │   │
│  │  refresh_token_value: "refresh-jwt"                   │   │
│  │  oidc_id_token_claims: {...user claims...}           │   │
│  └────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## Data Flow: Step-by-Step

### Step 1: Admin Sets Up Tenant's External IDP

```
POST /api/clients/idp
{
  "tenant_id": "hotel_chain_1",              ◄── TENANT
  "registration_id": "google",               ◄── PROVIDER
  "client_id": "hotel-chain-1-google.apps.googleusercontent.com",  ◄── EXTERNAL PROVIDER'S CLIENT ID
  "client_secret": "xyz...[encrypted]",
  "issuer_uri": "https://accounts.google.com",
  "authorization_uri": "https://accounts.google.com/o/oauth2/v2/auth",
  ...
}

↓

DB: customer_idp_config
├─ tenant_id: "hotel_chain_1"
├─ registration_id: "google"
├─ client_id: "hotel-chain-1-google.apps.googleusercontent.com"
└─ ... (other OAuth endpoints)
```

### Step 2: Admin Registers Internal Client (SPA)

```
POST /api/clients
{
  "provider": "internal",                    ◄── MARKS AS INTERNAL APP
  "clientId": "hotel-reservation-spa",       ◄── THIS APP'S IDENTIFIER
  "clientSecret": "secret-xyz",
  "clientName": "Hotel Reservation System",
  "redirectUris": ["https://hotel-spa.example.com/oauth/callback"],
  ...
}

↓

DB: client
├─ provider: "internal"
├─ clientId: "hotel-reservation-spa"
├─ redirectUris: "https://hotel-spa.example.com/oauth/callback"
└─ ... (scopes, auth methods, MFA settings)

⚠️  NOTE: Does NOT contain tenant_id or registration_id
     The same SPA client can be used by multiple tenants
```

### Step 3: User Initiates Login

```
Hotel SPA Frontend:
  ├─ User clicks "Sign in with Google"
  └─ Frontend shows: Select Organization → "Hotel Chain 1"
  
  POST /login
  {
    "organization": "hotel_chain_1",
    "provider": "google"
  }
  
  ↓ (Backend processes)
  
  Gets organization context and IDP provider
  
  ↓
  
  POST /sso?sso_registration_id=google
  
  ↓
  
  DynamicIDPRegistrationRepository.findByRegistrationId("google")
  
  ↓ Queries:
  
  SELECT * FROM customer_idp_config 
  WHERE registration_id = 'google' 
  AND tenant_id = 'hotel_chain_1'
  
  ↓ Returns:
  
  {
    tenant_id: "hotel_chain_1",
    registration_id: "google",
    client_id: "hotel-chain-1-google.apps.googleusercontent.com",
    client_secret: "[decrypted]",
    authorization_uri: "https://accounts.google.com/o/oauth2/v2/auth",
    ...
  }
```

### Step 4: Spring Redirects to Google

```
Spring builds ClientRegistration:
  .registrationId("google")
  .clientId("hotel-chain-1-google.apps.googleusercontent.com")
  .clientSecret("[decrypted]")
  .redirectUri("http://localhost:8081/login/oauth2/code/google")
  .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")

↓

30x Redirect to:
https://accounts.google.com/o/oauth2/v2/auth
  ?client_id=hotel-chain-1-google.apps.googleusercontent.com
  &redirect_uri=http://localhost:8081/login/oauth2/code/google
  &scope=openid+profile+email
  &response_type=code
  &state=<state>

↓ (User signs in at Google)

↓

Google redirects back:
GET http://localhost:8081/login/oauth2/code/google?code=AUTH_CODE&state=<state>

↓ (Spring exchanges code for ID Token)

↓

Spring extracts user claims from ID Token:
  {
    sub: "123456",
    email: "user@hotel-chain.com",
    name: "John Doe",
    ...
  }
```

### Step 5: Spring Issues Its Own Token

```
Spring Authorization Server creates a principal for the user

↓

User is now authenticated

↓

If client (hotel-reservation-spa) is making a token request:

POST /oauth2/token
{
  grant_type: "authorization_code",
  code: "AUTH_CODE",
  client_id: "hotel-reservation-spa",
  client_secret: "secret-xyz",
  redirect_uri: "https://hotel-spa.example.com/oauth/callback"
}

↓

Spring looks up:
  Client by clientId = "hotel-reservation-spa"
  ↓ Queries client table (NOT customer_idp_config)

SELECT * FROM client WHERE clientId = 'hotel-reservation-spa'

✓ Found:
{
  provider: "internal",
  clientId: "hotel-reservation-spa",
  scopes: "read:hotel,write:booking",
  redirectUris: "https://hotel-spa.example.com/oauth/callback",
  ...
}

↓

Spring generates JWT Access Token:
  {
    iss: "https://auth-server.example.com",
    sub: "123456",
    aud: "hotel-reservation-spa",
    scope: "read:hotel write:booking",
    iat: 1678886400,
    exp: 1678890000,
    ...
  }

↓

Token inserted into authorization table:
  {
    registered_client_id: "hotel-reservation-spa" (FK)
    principal_name: "user@hotel-chain.com"
    access_token_value: "eyJhbGc..."
    access_token_issued_at: NOW
    access_token_expires_at: NOW + TTL
    ...
  }
```

---

## Simplified Answer: Three-Tier System

| Layer | Entity | Purpose | Scope |
|-------|--------|---------|-------|
| **External IDP** | `customer_idp_config` | Map tenant → Google/Azure/etc. | Per-tenant external auth provider |
| **Registration** | `registration_id` field | Provider name for redirect | Identifies which OAuth provider to use |
| **Internal App** | `client` | App requesting tokens | SPA/backend service (not tenant-specific) |

### Your Request:
- **`provider: "internal"`** → This is an app (SPA, backend), NOT an external IDP
- **`clientId: "demo-client"`** → Unique name for this app
- **NOT specifying tenant or registration_id** → This app is available to any tenant
- **`redirectUris`** → Where the SPA expects to receive the callback

### Mapping:

```
Your Request          →  DB Table    →  Used When
──────────────────────────────────────────────────
provider              →  client      →  Identifying app type
clientId              →  client      →  Looking up app credentials
aud                   →  client      →  Audience claim in JWT
mfaEnabled            →  client      →  MFA for this app
redirectUris          →  client      →  Redirect after federated login
roles, scopes         →  client      →  Permissions issued in token

────────────────────────────────────────────────────

Independent config not in your request:
registration_id      →  customer_idp_config  →  Selecting external IDP
tenant_id            →  customer_idp_config  →  Org boundary for external IDP
```

---

## Multi-Tenant Example: Two Organizations, One App

### Scenario
**Hotel Reservation SPA** is used by both **Hotel Chain 1** and **Hotel Chain 2**.

### Registration: ONE Internal Client

```bash
POST /api/clients
{
  "provider": "internal",
  "clientId": "hotel-reservation-spa",      ◄── Registered ONCE
  "clientName": "Hotel Reservation",
  "redirectUris": ["https://hotel-spa.example.com/oauth/callback"],
  ...
}
```

### Configuration: TWO Tenant IDPs

**Hotel Chain 1 uses Google:**
```bash
POST /api/clients/idp
{
  "tenant_id": "hotel_chain_1",
  "registration_id": "google",
  "client_id": "chain1-google.apps.googleusercontent.com",
  "client_secret": "...",
  ...
}
```

**Hotel Chain 2 uses Azure:**
```bash
POST /api/clients/idp
{
  "tenant_id": "hotel_chain_2",
  "registration_id": "azure",
  "client_id": "chain2-azure-client-id",
  "client_secret": "...",
  ...
}
```

### User Flow

**Hotel Chain 1 User:**
- Frontend: `POST /sso?sso_registration_id=google`
- Backend: Looks up `customer_idp_config` where `registration_id='google'` and `tenant_id='hotel_chain_1'`
- Redirects to Google, gets user, returns to same `hotel-reservation-spa` client

**Hotel Chain 2 User:**
- Frontend: `POST /sso?sso_registration_id=azure`
- Backend: Looks up `customer_idp_config` where `registration_id='azure'` and `tenant_id='hotel_chain_2'`
- Redirects to Azure, gets user, returns to same `hotel-reservation-spa` client

**Result:** Both users end up at the same SPA, but authenticated via different external IDPs.

---

## Current Limitation & Enhancement

### Current State
- **Internal clients** (`client` table) have NO `tenant_id` column
- **Tenant is implicit** in the IDP selection
- **All clients are shared** across all tenants (no hard isolation)

### For Hard Tenant Isolation
Add `tenant_id` to `client` table:
```sql
ALTER TABLE client ADD COLUMN tenant_id VARCHAR(100);

-- Then enforce:
SELECT * FROM client 
WHERE clientId = 'hotel-reservation-spa' 
AND tenant_id = 'hotel_chain_1'
```

This would allow:
- Hotel Reservation SPA v1 for Hotel Chain 1
- Hotel Reservation SPA v2 for Hotel Chain 2
- Different settings per tenant

---

## Bottom Line

Your request creates an **internal OAuth2 client application**, which:
- ✅ Can be used by any tenant
- ✅ Defines scopes, MFA settings, and redirect URIs for the app
- ✗ Does NOT specify which tenant owns it
- ✗ Is NOT the same as a tenant or external IDP provider

The **registration_id** is the external provider name (Google, Azure, etc.), selected separately via `/sso?sso_registration_id=X`.

The **tenant_id** comes from the IDP configuration and is implicit in the IDP selection.

See [MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md](./MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md) for the full architecture guide.

