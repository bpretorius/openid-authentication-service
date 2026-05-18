# Your Question Answered: Tenant, Registration ID, and Client Association

> Authorization update: Spring Auth/IDP handles authentication, and OpenFGA handles roles and permissions dynamically. See `OPENFGA_AUTHORIZATION_MODEL.md`.

## Direct Answer to Your Question

You asked:
> "This is a sample of the rest request to create a user, how is this associated to a tenent and registration id, is the registration id the external idp provider?"

### Short Answer

Your request creates an **internal OAuth2 client application**, NOT a tenant or external IDP configuration.

```json
{
  "provider": "internal",           ← ✅ This is an INTERNAL app
  "clientId": "demo-client",        ← ✅ App identifier (stored in `client` table)
  "clientSecret": "demo-secret",    ← ✅ App's secret (bcrypt encoded in DB)
  ...
}

❌ Does NOT specify:
  - tenant_id (not tenant-specific)
  - registration_id (not an external IDP provider)
```

### Relationship to Tenants and Registration IDs

| Field | Associated To | How |
|-------|----------------|-----|
| `clientId: "demo-client"` | Internal Client | Stored directly in `client` table |
| `provider: "internal"` | Type Marker | Indicates this is an app (not external IDP) |
| **NOT** tenant-specific | Shared across all tenants | Same app can serve multiple customers |
| **NOT** registration ID | Different concept entirely | External IDP provider name (google, azure) |

### Is Registration ID the External IDP Provider?

**YES, EXACTLY.**

```
registration_id = External IDP Provider Name

Examples:
  "google"    → Google OAuth/OIDC provider
  "azure"     → Microsoft Azure AD provider
  "okta"      → Okta OIDC provider
  "custom"    → Your own OIDC endpoint

Location: Stored in customer_idp_config.registration_id
```

---

## How They All Relate

### Three Different Concepts

```
1. TENANT (Organizational Boundary)
   └─ Stored in: customer_idp_config.tenant_id
   └─ Example: "hotel_chain_1", "airline_x"
   └─ Purpose: Isolate each organization's IDP configs

2. REGISTRATION ID (External Provider Name)
   └─ Stored in: customer_idp_config.registration_id
   └─ Example: "google", "azure"
   └─ Purpose: Which OAuth provider to redirect to

3. INTERNAL CLIENT (Your App)
   └─ Stored in: client.clientId
   └─ Example: "demo-client", "hotel-reservation-spa"
   └─ Purpose: Application requesting tokens
```

### NO Direct Association

Your client request is **NOT** associated to tenant or registration_id in the schema:

```
client table
├─ clientId: "demo-client"         ← Your request stores this
├─ clientSecret: "..."
├─ scopes: "..."
├─ redirectUris: "..."
│
❌ NO tenant_id column
❌ NO registration_id column
│
→ Multi-tenancy is IMPLICIT (derived from IDP selection, not a column)
```

---

## Practical Example: How They Work Together

### Setup Phase (Admin Configuration)

**Step 1: Register External IDPs (different per tenant)**

```bash
# Hotel Chain 1 uses Google
POST /api/clients/idp
{
  "tenant_id": "hotel_chain_1",      ← TENANT
  "registration_id": "google",       ← REGISTRATION ID
  "client_id": "chain1-google.apps.googleusercontent.com",
  "client_secret": "..."
}

# Hotel Chain 2 uses Azure
POST /api/clients/idp
{
  "tenant_id": "hotel_chain_2",
  "registration_id": "azure",
  "client_id": "chain2.onmicrosoft.com",
  "client_secret": "..."
}
```

**Step 2: Register Internal Client (shared across tenants)**

```bash
# Hotel Reservation SPA (registered ONCE, used by both tenants)
POST /api/clients
{
  "provider": "internal",
  "clientId": "hotel-reservation-spa",    ← YOUR REQUEST
  "clientSecret": "secret-xyz",
  "redirectUris": ["http://localhost:3000/callback"],
  ...
}
```

### Runtime Phase (User Login)

**Hotel Chain 1 User:**
1. User @ hotel-spa clicks "Sign In"
2. Frontend: `POST /sso?sso_registration_id=google`
3. Backend queries: `customer_idp_config WHERE registration_id='google' AND tenant_id='hotel_chain_1'`
4. System redirects to **Google** (because tenant 1 uses Google)
5. After Google auth, token issued to **hotel-reservation-spa** client
6. User authenticated at Hotel Reservation App

**Hotel Chain 2 User:**
1. User @ hotel-spa clicks "Sign In"
2. Frontend: `POST /sso?sso_registration_id=azure`
3. Backend queries: `customer_idp_config WHERE registration_id='azure' AND tenant_id='hotel_chain_2'`
4. System redirects to **Azure** (because tenant 2 uses Azure)
5. After Azure auth, token issued to **hotel-reservation-spa** client (SAME app)
6. User authenticated at Hotel Reservation App

---

## Database Mapping

### Your Request → `client` Table

```json
{
  "provider": "internal",           → client.provider = "internal"
  "clientId": "demo-client",        → client.clientId = "demo-client" (UNIQUE)
  "clientSecret": "demo-secret",    → client.clientSecret = "[bcrypt]"
  "clientName": "Demo Client",      → client.clientName = "Demo Client"
  "aud": ["hotel_reservation"],     → client.clientSettings.resource_indicator = "hotel_reservation"
  "mfaEnabled": false,              → client.mfaEnabled = false
  "requireProofKey": true,          → client.clientSettings.require-proof-key = true
  "requireAuthorizationConsent": false,  → client.clientSettings.require-authorization-consent = false
  "authenticationMethods": [...],   → client.clientAuthenticationMethods = "..."
  "grantTypes": [...],              → client.authorizationGrantTypes = "..."
  "scopes": [...],                  → client.scopes = "..."
  "roles": [...],                   → client.clientSettings.roles = "..."
  "redirectUris": [...],            → client.redirectUris = "..."
  "postLogoutRedirectUris": [...]   → client.postLogoutRedirectUris = "..."
}

SQL Result:
INSERT INTO client (
  id=UUID,
  provider='internal',
  clientId='demo-client',
  ...
)

❌ NOT stored in customer_idp_config (that's for external IDPs only)
```

### External IDP Config → `customer_idp_config` Table

```json
{
  "tenant_id": "hotel_chain_1",
  "registration_id": "google",
  "client_id": "chain1-google.apps.googleusercontent.com",  ← External provider's OAuth client
  "client_secret": "[encrypted secret]",                     ← External provider's secret
  "issuer_uri": "https://accounts.google.com",
  "authorization_uri": "https://accounts.google.com/o/oauth2/v2/auth",
  ...
}

SQL Result:
INSERT INTO customer_idp_config (
  tenant_id='hotel_chain_1',
  registration_id='google',
  client_id='chain1-google.apps.googleusercontent.com',
  ...
)

❌ Your client request does NOT create this
```

---

## Key Differences

### Internal Client (Your Request)

```
Table: client
Purpose: Represents an application (SPA, mobile app, backend service)
Scope: Shared across tenants
Fields:
  ✓ clientId: unique app identifier
  ✓ scopes: permissions
  ✓ redirectUris: where to return after auth
  ✓ authenticationMethods: how app authenticates
  ✗ tenant_id: NOT present
  ✗ registration_id: NOT present
```

### External IDP Configuration

```
Table: customer_idp_config
Purpose: Represents a tenant's external OAuth provider
Scope: Per-tenant configuration
Fields:
  ✓ tenant_id: which organization
  ✓ registration_id: which provider (google, azure, etc)
  ✓ client_id: THE PROVIDER'S OAuth client ID
  ✓ client_secret: THE PROVIDER'S OAuth secret
  ✓ endpoints: issuer_uri, authorization_uri, token_uri, etc
  ✗ scopes (handled by provider)
  ✗ redirectUris (handled by provider)
```

---

## What Happens When You Create Your Client

```
POST /api/clients
{
  "provider": "internal",
  "clientId": "demo-client",
  ...
}

↓

Spring Authorization Server:
  1. Validates request
  2. Encodes client secret with Bcrypt
  3. Persists to client table
  4. Caches in Redis
  5. Returns 204 No Content

Result:
  ✓ "demo-client" is now registered as an internal OAuth2 app
  ✓ Can receive tokens from any tenant
  ✓ Requests from ANY user (Google auth, Azure auth, internal auth) can request a token for this client
  ✓ NOT tied to specific tenant
  ✓ NOT tied to specific external IDP
```

---

## Multi-Tenancy: How It Actually Works

The current system achieves multi-tenancy through **implicit tenant context**, not explicit schema relationships:

### Implicit Tenant Context Flow

```
                WITHOUT tenant_id in client table
                
User selects organization/IDP during login
            ↓
Auth server fetches IDP config with tenant context
            ↓
IDP determines which external provider to use
            ↓
After federated auth, user's identity carries tenant context
            ↓
Token issued to internal client (hotel-reservation-spa)
            ↓
User's identity claims implicitly tied to tenant
```

### Example: Two Tenants, One App

```
Database:

customer_idp_config:
├─ ID=1, tenant_id='hotel_chain_1', registration_id='google'
└─ ID=2, tenant_id='hotel_chain_2', registration_id='azure'

client:
└─ clientId='hotel-reservation-spa'

Runtime:

Hotel Chain 1 user → Logs in → google IDP → hotel-reservation-spa token
Hotel Chain 2 user → Logs in → azure IDP → hotel-reservation-spa token

SAME CLIENT, DIFFERENT TENANTS (implicit via IDP selection)
```

---

## If You Wanted Hard Tenant Isolation

To make tenants explicit in the schema:

```sql
-- ALTER client table
ALTER TABLE client ADD COLUMN tenant_id VARCHAR(100);

-- Then register app PER TENANT
INSERT INTO client (clientId, tenant_id, ...)
VALUES ('hotel-reservation-spa-hc1', 'hotel_chain_1', ...)

INSERT INTO client (clientId, tenant_id, ...)
VALUES ('hotel-reservation-spa-hc2', 'hotel_chain_2', ...)

-- Enforce at lookup time
SELECT * FROM client 
WHERE clientId = 'X' 
AND tenant_id = 'Y'
```

This would give you:
- ✓ Hard tenant isolation at the schema level
- ✓ Different settings per tenant
- ✗ More management overhead (register app per tenant)
- ✗ Less flexibility (can't easily share app definition)

---

## Your Request in Context

### What You Created

An **internal OAuth2 client application** named "demo-client" that:
- Can authenticate requests received via any external IDP (Google, Azure, etc)
- Can issue tokens to SPAs, mobile apps, or backend services
- Has scopes: ["read:hotel_reservation", "create:hotel_reservation"]
- Requires: client_secret_basic authentication
- Supports: authorization_code and refresh_token grants
- Redirects usersto: http://localhost:3000/callback after auth
- Has MFA: disabled, but can be enabled

### What's Missing (and OK)

Your request does NOT specify:
- `tenant_id`: Not needed - app is shared
- `registration_id`: Not needed - external IDPs are registered separately
- Which external IDP to use: Handled by client selection during login

---

## Documentation Structure

For deeper understanding, open these files in order:

1. **TENANT_AND_CLIENT_QUICK_REFERENCE.md** - Simplified explanation + examples
2. **VISUAL_ARCHITECTURE_DIAGRAMS.md** - Flow diagrams + layer breakdown
3. **MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md** - Full architecture + edge cases
4. **CODE_IMPLEMENTATION_REFERENCE.md** - Code locations + implementations

---

## TL;DR

**Your question:**
> "How is [client request] associated to a tenant and registration id, is the registration id the external idp provider?"

**Answer:**
1. **NOT associated to tenant** - internal clients are shared
2. **NOT associated to registration_id** - that's for external IDPs
3. **YES, registration_id IS the external IDP provider** - "google", "azure", etc
4. **Multi-tenancy achieved through** - implicit tenant context via IDP selection
5. **Your client goes into** - `client` table with `clientId="demo-client"`
6. **External IDP config goes into** - `customer_idp_config` table with `registration_id="google"` and `tenant_id="hotel_chain_1"`

**Next steps:**
- If you want to understand the flow: Read VISUAL_ARCHITECTURE_DIAGRAMS.md
- If you want to understand the code: Read CODE_IMPLEMENTATION_REFERENCE.md
- If you want to understand when to use what: Read MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md

