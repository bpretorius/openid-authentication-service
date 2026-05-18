# Multi-Tenancy and Client Management Architecture

> Authorization update: Spring Auth + federated IDPs handle authentication, while OpenFGA is the source of truth for dynamic roles and permissions.

## Overview

This Spring Authorization Server implements multi-tenant external IDP (Identity Provider) support with internal OAuth2 client management. This document clarifies how **tenants**, **registration IDs**, and **internal clients** are associated.

---

## Three Key Concepts

### 1. **Tenant** (`customer_idp_config.tenant_id`)
A **tenant** is a logical organizational boundary representing a company or organization.

- **Stored in**: `customer_idp_config.tenant_id` (unique)
- **Purpose**: Isolates external IDP configurations per organization
- **Example**: `"hotel_chain_1"`, `"airline_x"`, `"bank_y"`
- **Responsibility**: Each tenant manages its own external OAuth2/OIDC provider configuration (Google, Azure, Okta, etc.)

### 2. **Registration ID** (`customer_idp_config.registration_id`)
A **registration ID** is the Spring Security OAuth2 client registration name for an external IDP provider within a tenant.

- **Stored in**: `customer_idp_config.registration_id` (unique across all tenants)
- **Format**: Usually provider name (kebab-case): `"google"`, `"azure"`, `"okta"`, etc.
- **Purpose**: Identifies which external IDP to redirect to during federated login
- **Spring Integration**: Used as `registration_id` parameter in OAuth2 authorization redirect: `/oauth2/authorization?registration_id=google`
- **NOT a tenant-specific ID**: It's a provider registration, but per-tenant configs can share the same `registration_id` if isolated by other means

### 3. **Internal Client** (`client.clientId`)
An **internal client** is an OAuth2 application registered in THIS authorization server (not an external IDP).

- **Stored in**: `client` table with fields like `clientId`, `clientSecret`, etc.
- **Purpose**: Represents a third-party application (SPA, mobile app, backend service) that wants to authenticate users
- **Relationship to Tenant/Registration ID**: **NONE DIRECT** – Internal clients are NOT inherently tied to tenants or registration IDs
- **Use Case**: A SPA like Hotel Reservation System registers once as an internal client; multiple tenants can authenticate users for this same SPA through different external IDPs

---

## Request Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          User Login Journey                             │
└─────────────────────────────────────────────────────────────────────────┘

1. SPA User clicks "Sign In with Google"
   ↓
   POST /sso?sso_registration_id=google
   (Selected external IDP to use for this org)
   ↓
2. Spring Auth Server looks up:
   CustomerIdpConfig.findByRegistrationId("google")
   ↓
3. Retrieves from DB:
   {
     id: 123,
     tenant_id: "hotel_chain_1",           ← Org boundary
     registration_id: "google",             ← External provider name
     client_id: "...apps.googleusercontent.com",   ← Google's OAuth credentials
     client_secret: "...[encrypted]...",
     issuer_uri: "https://accounts.google.com",
     authorization_uri: "https://accounts.google.com/o/oauth2/v2/auth",
     ... (other Google endpoints)
   }
   ↓
4. DynamicIDPRegistrationRepository builds Spring ClientRegistration:
   ClientRegistration.builder()
     .registrationId("google")
     .clientId("...apps.googleusercontent.com")
     .clientSecret("[decrypted]")
     .redirectUri(
       appConfig.getDefaultAuthCodeUrl() + "google"
       → "http://localhost:8081/login/oauth2/code/google"
     )
     .build()
   ↓
5. Spring redirects to Google:
   https://accounts.google.com/o/oauth2/v2/auth
     ?client_id=...apps.googleusercontent.com
     &redirect_uri=http://localhost:8081/login/oauth2/code/google
     &scope=openid+profile+email
     &response_type=code
   ↓
6. User authenticates at Google, Google redirects back:
   GET /login/oauth2/code/google?code=AUTH_CODE&state=STATE
   ↓
7. Spring exchanges AUTH_CODE for ID Token + Access Token from Google
   ↓
8. Spring extracts user info, creates Principal, returns to SPA callback
   ↓
9. SPA has authenticated user (tenant context is implicit via IDP config)

┌─────────────────────────────────────────────────────────────────────────┐
│ INTERNAL CLIENT is NOT used in the federated flow above:                 │
│ It only matters if this Auth Server itself issues tokens                │
│ (e.g., SPA → Auth Server → Access Token flow)                           │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Database Schema Relationships

### `customer_idp_config` (Tenant's External IDP Configuration)

```sql
CREATE TABLE customer_idp_config (
    id SERIAL PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL UNIQUE,              ← Org boundary (1:1)
    registration_id VARCHAR(100) NOT NULL UNIQUE,       ← IDP provider name (1:1)
    client_id VARCHAR(255) NOT NULL,                    ← EXTERNAL provider's OAuth ClientID
    client_secret VARCHAR(255) NOT NULL,                ← EXTERNAL provider's OAuth secret
    issuer_uri VARCHAR(255) NOT NULL,
    authorization_uri VARCHAR(255) NOT NULL,
    token_uri VARCHAR(255) NOT NULL,
    jwk_set_uri VARCHAR(255) NOT NULL,
    user_info_uri VARCHAR(255) NOT NULL,
    user_name_attribute VARCHAR(100) NOT NULL,
    scope VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

**Key Point**: These credentials are for EXTERNAL Google/Azure/Okta accounts, NOT internal users.

### `client` (Internal OAuth2 Client Registration)

```sql
CREATE TABLE client (
    id VARCHAR(255) NOT NULL,                           ← UUID
    provider VARCHAR(255) NOT NULL,                     ← "internal" or "external"
    client_id VARCHAR(255) NOT NULL UNIQUE,             ← Internal app identifier
    client_secret VARCHAR(255),                         ← Internal app secret
    client_name VARCHAR(255) NOT NULL,
    client_authentication_methods VARCHAR(1000),
    authorization_grant_types VARCHAR(1000),
    redirect_uris VARCHAR(1000),                        ← Where Auth Server redirects to SPA
    post_logout_redirect_uris VARCHAR(1000),
    scopes VARCHAR(1000),
    client_settings VARCHAR(2000),                      ← MFA, PKCE, consent settings
    token_settings VARCHAR(2000),                       ← Token TTL, refresh logic
    PRIMARY KEY (id)
);
```

**Key Point**: This is where SPAs/backend services register. NOT directly tied to `customer_idp_config`.

---

## How Does Multi-Tenancy Work?

### **Current Implementation: Tenant Isolation via IDP Config**

Multi-tenancy is achieved through **tenant-specific IDP configurations**, not through explicit tenant columns in the `client` table:

1. **Tenant Registration (Pre-requisite)**:
   ```bash
   POST /api/clients/idp
   {
     "tenant_id": "hotel_chain_1",
     "registration_id": "google",
     "client_id": "hotel-chain-1-google.apps.googleusercontent.com",
     "client_secret": "[encrypted]",
     "issuer_uri": "https://accounts.google.com",
     ...
   }
   ```
   Admin registers Hotel Chain 1's Google OAuth credentials.

2. **Internal Client Registration (Shared)**:
   ```bash
   POST /api/clients
   {
     "clientId": "hotel-reservation-app",
     "clientSecret": "...",
     "clientName": "Hotel Reservation SPA",
     "redirectUris": ["https://hotel-spa.example.com/callback"],
     ...
   }
   ```
   The Hotel Reservation SPA registers ONCE (not per tenant).

3. **Federated Login Flow**:
   - Hotel Chain 1 frontend: `POST /sso?sso_registration_id=google` (uses Google IDP)
   - Hotel Chain 2 frontend: `POST /sso?sso_registration_id=azure` (uses Azure IDP)
   - Both end up at the same `hotel-reservation-app` client, but authenticated via different IDPs
   - **Tenant context flows through the user identity** returned by the external IDP

4. **Token Issuance (If This Auth Server Issues Tokens)**:
   - Internal client `hotel-reservation-app` receives a request
   - Spring AuthServer issues a token with:
     - Subject (sub): User ID from Google/Azure
     - Scope, aud (audience), etc., per the `client` configuration
     - **Tenant context** must be extracted from the user's IDP claim or session

### **Multi-Tenant Extension Requirements**

For **true hard tenant isolation** in this codebase, you would need:

1. **Add `tenant_id` column to `client` table**:
   ```sql
   ALTER TABLE client ADD COLUMN tenant_id VARCHAR(100);
   ```
   Then each SPA is registered per-tenant.

2. **Filter clients by tenant**:
   ```java
   public Optional<Client> findByClientIdAndTenantId(String clientId, String tenantId)
   ```

3. **Enforce tenant context in authorization flow**:
   ```java
   // Extract tenant from request context (header, session, etc.)
   String tenantId = extractTenantContext(request);
   
   // Verify IDP belongs to tenant
   CustomerIdpConfig config = readerCustomerIdpRepository
       .findByRegistrationIdAndTenantId(registrationId, tenantId);
   ```

---

## Practical Example: Hotel Chain with Multiple IDPs

### Scenario
**Hotel Chain 1** wants to support both Google and Azure login.

### Step 1: Register External IDPs (Tenant Level)

**Google Registration**:
```bash
POST /api/clients/idp
{
  "tenant_id": "hotel_chain_1",
  "registration_id": "google",
  "client_id": "hotel-chain-1-google.apps.googleusercontent.com",
  "client_secret": "[encrypted secret]",
  "issuer_uri": "https://accounts.google.com",
  "authorization_uri": "https://accounts.google.com/o/oauth2/v2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "jwk_set_uri": "https://www.googleapis.com/oauth2/v3/certs",
  "user_info_uri": "https://openidconnect.googleapis.com/v1/userinfo",
  "user_name_attribute": "email",
  "scope": "openid,profile,email"
}
```

**Azure Registration**:
```bash
POST /api/clients/idp
{
  "tenant_id": "hotel_chain_1",
  "registration_id": "azure",
  "client_id": "hotel-chain-1-azure-client-id",
  "client_secret": "[encrypted secret]",
  "issuer_uri": "https://login.microsoftonline.com/tenant-id/v2.0",
  "authorization_uri": "https://login.microsoftonline.com/tenant-id/oauth2/v2.0/authorize",
  "token_uri": "https://login.microsoftonline.com/tenant-id/oauth2/v2.0/token",
  "jwk_set_uri": "https://login.microsoftonline.com/tenant-id/discovery/v2.0/keys",
  "user_info_uri": "https://graph.microsoft.com/v1.0/me",
  "user_name_attribute": "mail",
  "scope": "openid,profile,email"
}
```

### Step 2: Register Hotel Reservation SPA (Internal Client)

```bash
POST /api/clients
{
  "provider": "internal",
  "clientId": "hotel-reservation-spa",
  "clientSecret": "[secret]",
  "clientName": "Hotel Reservation System",
  "aud": ["hotel_reservation"],
  "mfaEnabled": true,
  "requireProofKey": true,
  "requireAuthorizationConsent": false,
  "authenticationMethods": ["client_secret_basic"],
  "grantTypes": ["authorization_code", "refresh_token"],
  "scopes": ["read:hotel", "write:booking"],
  "roles": ["guest", "admin"],
  "redirectUris": ["https://hotel-app.example.com/oauth/callback"],
  "postLogoutRedirectUris": ["https://hotel-app.example.com/logout"]
}
```

### Step 3: User Selects IDP on Login Screen

Hotel Reservation SPA shows:
- "Sign in with Google" → Sends: `POST /sso?sso_registration_id=google`
- "Sign in with Azure" → Sends: `POST /sso?sso_registration_id=azure`

**Both lead to the same internal client but through different external IDPs.**

### Step 4: Behind the Scenes

**For Google path**:
```java
String registrationId = "google";
CustomerIdpConfig googleConfig = 
  readerCustomerIdpRepository.findByRegistrationId("google");
// { tenant_id: "hotel_chain_1", client_id: "hotel-chain-1-google.apps.googleusercontent.com", ... }

ClientRegistration registration = ClientRegistration.withRegistrationId("google")
    .clientId(googleConfig.getClientId())
    .redirectUri("http://localhost:8081/login/oauth2/code/google")
    .build();

// Spring Security redirects to Google OAuth endpoint
return "redirect:/oauth2/authorization?registration_id=google";
```

**For Azure path**:
```java
String registrationId = "azure";
CustomerIdpConfig azureConfig = 
  readerCustomerIdpRepository.findByRegistrationId("azure");
// { tenant_id: "hotel_chain_1", client_id: "hotel-chain-1-azure-client-id", ... }

ClientRegistration registration = ClientRegistration.withRegistrationId("azure")
    .clientId(azureConfig.getClientId())
    .redirectUri("http://localhost:8081/login/oauth2/code/azure")
    .build();

return "redirect:/oauth2/authorization?registration_id=azure";
```

---

## Client Request ↔ Data Mapping

### Your Example Request:
```json
{
  "provider": "internal",           ← Marks as internal OAuth2 client (not external IDP)
  "clientId": "demo-client",        ← Unique identifier for this client application
  "clientSecret": "demo-secret",    ← This client's secret (encrypted in DB)
  "clientName": "Demo Client",      ← Display name
  "aud": ["hotel_reservation"],     ← Audience claim(s) in issued tokens
  "mfaEnabled": false,              ← MFA setting for this client
  "requireProofKey": true,          ← PKCE requirement
  "requireAuthorizationConsent": false,  ← Show consent screen?
  "authenticationMethods": ["client_secret_basic"],  ← How client authenticates
  "grantTypes": ["authorization_code", "refresh_token"],  ← Allowed flows
  "scopes": ["read:hotel_reservation", "create:hotel_reservation"],  ← Available scopes
  "roles": ["read:idp", "create:idp"],  ← Admin roles for this client
  "redirectUris": ["http://localhost:3000/callback"],  ← Where this client is redirected after auth
  "postLogoutRedirectUris": ["http://localhost:3000"]  ← Post-logout redirection
}
```

### Saved in `client` Table:
```sql
INSERT INTO client (
  id,                               ← UUID
  provider,                         ← "internal"
  client_id,                        ← "demo-client"
  client_secret,                    ← [bcrypt encoded]
  client_name,                      ← "Demo Client"
  redirect_uris,                    ← "http://localhost:3000/callback"
  post_logout_redirect_uris,        ← "http://localhost:3000"
  scopes,                           ← "read:hotel_reservation,create:hotel_reservation"
  client_settings,                  ← JSON: { mfaEnabled: false, roles: "read:idp,create:idp", ... }
  token_settings,                   ← JSON: { accessTokenTTL, refreshTokenTTL, ... }
  ...
)
```

**NOT stored in `customer_idp_config`** (that's for external IDPs only).

---

## Key Takeaways

| Concept | Purpose | Stored In | Scope |
|---------|---------|-----------|-------|
| **Tenant** | Organizational boundary | `customer_idp_config.tenant_id` | Isolates external IDP configs |
| **Registration ID** | External IDP provider name | `customer_idp_config.registration_id` | Identifies which OIDC provider to redirect to |
| **Internal Client** | Application using Auth Server | `client.clientId` | Represents third-party app (SPA, backend) |
| **Relationship** | ❌ Internal clients are NOT directly associated with tenants or registration IDs in current schema | — | Multi-tenancy is implicit through IDP selection |

### When You Create an Internal Client:
- **You ARE NOT** specifying a tenant or registration ID
- **You ARE** defining OAuth2 permission/scope boundaries for a third-party application
- **Tenant context** emerges during the login flow when users select which IDP to use

### When You Configure an External IDP:
- **You ARE** specifying which tenant this IDP belongs to (`tenant_id`)
- **You ARE** identifying the provider (`registration_id`)
- **You ARE** (implicitly) making it available for all internal clients to use

---

## Next Steps for Enhanced Multi-Tenancy

If you need hard tenant isolation where internal clients are also tenant-specific:

1. **Add `tenant_id` column** to `client` table
2. **Modify client lookup** to filter by both `client_id` AND `tenant_id`
3. **Add tenant header extraction** to authorization flow
4. **Enforce tenant matching** between client registration and requested IDP
5. **Add data access layer** filters to prevent cross-tenant access

See `MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md` (this file) for implementation details.

