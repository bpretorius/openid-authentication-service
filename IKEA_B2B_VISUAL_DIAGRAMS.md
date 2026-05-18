# IKEA B2B Architecture - Visual Diagrams and Flows

> Authorization update: diagrams should be interpreted as Spring handling authentication and OpenFGA handling dynamic permission decisions.

## System Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│                        IKEA (Primary Organization)                           │
│           Spring Authorization Server with B2B Resource Controls            │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    │               │               │
        ┌───────────▼────────┐  ┌───▼────────────┐  ┌──▼──────────────────┐
        │  Acme Mfg (B2B)    │  │ ProLog (B2B)   │  │ BuildRight (B2B)    │
        │  (Azure AD)        │  │ (Google Wksp)  │  │ (Okta)              │
        ├────────────────────┤  ├────────────────┤  ├─────────────────────┤
        │ Users:             │  │ Users:         │  │ Users:              │
        │ • alice@acme.com   │  │ • charlie@...  │  │ • eve@buildright.com│
        │ • bob@acme.com     │  │ • dave@...     │  │                     │
        │                    │  │                │  │                     │
        │ Registration ID:   │  │ Registration ID│  │ Registration ID:    │
        │ "acme_azure"       │  │ "prolog_google"   │ "buildright_okta"   │
        │                    │  │                │  │                     │
        │ Company ID: 1      │  │ Company ID: 2  │  │ Company ID: 3       │
        └────────┬───────────┘  └────┬───────────┘  └──────┬──────────────┘
                 │                   │                     │
                 │                   │                     │
                 └───────────────────┼─────────────────────┘
                                     │
                    ┌────────────────▼────────────────┐
                    │                                │
                    │  IKEA Resources (Tagged)       │
                    ├────────────────────────────────┤
                    │ Order #12345 → Company ID = 1  │ ← Acme only
                    │ Order #12346 → Company ID = 1  │ ← Acme only
                    │ Inventory #5678 → Company IDs  │ ← Acme + ProLog
                    │ Shipment #999 → Company IDs    │ ← All B2B companies
                    │ Invoice #111 → Company ID = 3  │ ← BuildRight only
                    └────────────────────────────────┘
```

---

## Database Schema Relationships

```
┌─────────────────────────────────────────────────────────────────┐
│                  IKEA Auth Server Database                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────┐         ┌──────────────────────────┐  │
│  │  customer_idp_config│         │  b2b_company            │  │
│  ├─────────────────────┤         ├──────────────────────────┤  │
│  │ id                  │◄──┐     │ id                       │  │
│  │ tenant_id           │   │     │ company_name = "Acme"    │  │
│  │ registration_id     │   │     │ company_key = "acme"     │  │
│  │ (IDP endpoints)     │   │     └───────┬──────────────────┘  │
│  └──────┬──────────────┘   │             │                     │
│         │                  │             │                     │
│         │                  │             │                     │
│         │     ┌────────────┘    ┌────────▼──────────────────┐  │
│         │     │                │ b2b_company_idp_mapping   │  │
│         │     │                ├───────────────────────────┤  │
│         │     │                │ b2b_company_id = 1        │  │
│         │     │                │ customer_idp_config_id=FK│  │
│         │     │                └───────────────────────────┘  │
│         │     │                                                │
│         └─────┼────────────────────────────────────────────┐  │
│               │                                            │  │
│         ┌─────▼──────────────┐  ┌──────────────────────────▼─┐│
│         │ b2b_user_company   │  │ b2b_resource_company_access
│         │ _mapping           │  ├────────────────────────────┤│
│         ├────────────────────┤  │ resource_id = "order-12345"││
│         │ federated_user_id  │  │ resource_type = "order"    ││
│         │ federated_email    │  │ b2b_company_id = 1         ││
│         │ idp_registration_id│  │ access_level = "READ"      ││
│         │ b2b_company_id = 1 │  └────────────────────────────┘│
│         │ user_roles         │                                │
│         └────────────────────┘                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

Relationships:
─────────────

1. customer_idp_config ──1:1──→ tenant_id (acme, prolog, buildright)
                            ↓
2. b2b_company_idp_mapping ──→ Links which company uses which IDP
                            ↓
3. b2b_company ────────────→ Company master data
                            ↓
4. b2b_user_company_mapping → Maps federated users to companies
                            ↓
5. b2b_resource_company_access → Controls which resources each company can access
```

---

## Complete Login & Access Flow

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    ALICE (alice@acme.com) Login Flow                       │
└────────────────────────────────────────────────────────────────────────────┘

STEP 1: Alice initiates login at IKEA portal
┌─────────────────────────────────────┐
│ IKEA Login Page                     │
│ ┌─────────────────────────────────┐ │
│ │ Select your company:            │ │
│ │ □ Acme Manufacturing            │ │ ← Alice clicks
│ │ □ ProLog Logistics              │ │
│ │ □ BuildRight Construction       │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
                │
                ▼
STEP 2: Frontend redirects to auth server with company selection
                │
        POST /sso?sso_registration_id=acme_azure
                │
                ▼
        ┌─────────────────────────────┐
        │ Spring Auth Server          │
        │ LoginController.postSSO()   │
        └─────────────────────────────┘
                │
                ├─ registrationId = "acme_azure"
                │
                ▼
        Query: SELECT * FROM customer_idp_config 
               WHERE registration_id='acme_azure'
                │
                ├─ Found: tenant_id='acme'
                │        client_id='acme-oauth-client'
                │        authorization_uri='https://login.microsoftonline.com/...'
                │
                ▼
        DynamicIDPRegistrationRepository builds ClientRegistration
                │
                ▼
        302 Redirect to Azure AD:
        https://login.microsoftonline.com/.../oauth2/v2.0/authorize
              ?client_id=acme-oauth-client
              &redirect_uri=https://ikea-auth.example.com/login/oauth2/code/acme_azure
              &scope=openid+profile+email
              &response_type=code

STEP 3: Alice authenticates at Azure AD
        ┌────────────────────────────────┐
        │ Microsoft Azure AD Login       │
        │ ┌──────────────────────────────┤
        │ │ Email: alice@acme.com        │
        │ │ Password: [••••••••]         │
        │ │ [Sign In]                    │
        │ └──────────────────────────────┤
        └────────────────────────────────┘
                │
                ├─ Azure validates credentials
                │
                ├─ Azure generates ID Token:
                │  {
                │    "iss": "https://login.microsoftonline.com/acme-tenant-id/v2.0",
                │    "aud": "acme-oauth-client",
                │    "sub": "acme-user-123",
                │    "email": "alice@acme.com",
                │    "email_verified": true,
                │    "given_name": "Alice",
                │    "family_name": "Smith",
                │    "iat": 1234567890,
                │    "exp": 1234571490
                │  }
                │
                ▼
        Azure redirects back:
        302 to: https://ikea-auth.example.com/login/oauth2/code/acme_azure
                  ?code=AUTH_CODE_VALUE
                  &state=STATE_VALUE

STEP 4: Spring Auth Server exchanges code for token
        ┌─────────────────────────────────────────────────┐
        │ Spring Security OAuth2Client                    │
        ├─────────────────────────────────────────────────┤
        │                                                 │
        │ POST https://login.microsoftonline.com/.../token
        │ {                                               │
        │   "grant_type": "authorization_code",           │
        │   "code": "AUTH_CODE_VALUE",                    │
        │   "client_id": "acme-oauth-client",             │
        │   "client_secret": "[from DB, decrypted]",      │
        │   "redirect_uri": "https://ikea-auth.../callback"
        │ }                                               │
        │                                                 │
        │ Azure responds:                                 │
        │ {                                               │
        │   "id_token": "[JWT from step 3]",              │
        │   "access_token": "[access token]",             │
        │   "token_type": "Bearer",                       │
        │   "expires_in": 3600                            │
        │ }                                               │
        │                                                 │
        └─────────────────────────────────────────────────┘
                │
                ├─ Spring decodes & validates ID Token signature
                │ (using public key from Azure's JWKS endpoint)
                │
                ├─ Extracts user claims:
                │  sub='acme-user-123'
                │  email='alice@acme.com'
                │
                ▼

STEP 5: Spring Auth Server enriches token with B2B context
        ┌────────────────────────────────────────────────────┐
        │ B2bContextService.extractB2bContext(...)           │
        ├────────────────────────────────────────────────────┤
        │                                                    │
        │ INPUT:                                             │
        │  ├─ federatedUserId='acme-user-123'  (from sub)   │
        │  ├─ federatedEmail='alice@acme.com'  (from email) │
        │  └─ registrationId='acme_azure'      (from token) │
        │                                                    │
        │ LOGIC:                                             │
        │ 1. Query: SELECT * FROM customer_idp_config       │
        │          WHERE registration_id='acme_azure'       │
        │    └─ Result: tenant_id='acme'                    │
        │                                                    │
        │ 2. Query: SELECT * FROM b2b_company               │
        │          WHERE company_key='acme'                 │
        │    └─ Result: id=1, company_name='Acme Mfg'     │
        │                                                    │
        │ 3. Query: SELECT * FROM b2b_user_company_mapping  │
        │          WHERE federated_user_id='acme-user-123'  │
        │            AND idp_registration_id='acme_azure'   │
        │    └─ Result: b2b_company_id=1, user_roles=...   │
        │                                                    │
        │ OUTPUT:                                            │
        │ {                                                  │
        │   "b2b_company_id": "1",                          │
        │   "b2b_company_key": "acme",                      │
        │   "b2b_company_name": "Acme Manufacturing",       │
        │   "b2b_user_roles": "supplier_user"               │
        │ }                                                  │
        │                                                    │
        └────────────────────────────────────────────────────┘
                │
                ▼

STEP 6: Spring issues JWT with B2B context
        ┌──────────────────────────────────────────────────────┐
        │ JWT Token (Access Token issued by IKEA Auth Server)  │
        ├──────────────────────────────────────────────────────┤
        │ {                                                    │
        │   "iss": "https://ikea-auth.example.com",            │
        │   "sub": "acme-user-123",                            │
        │   "aud": ["ikea_supplier_portal", "ikea_api"],       │
        │   "email": "alice@acme.com",                         │
        │   "name": "Alice Smith",                             │
        │                                                      │
        │   /* ← B2B Context (added by Spring) */              │
        │   "b2b_company_id": "1",                             │
        │   "b2b_company_key": "acme",                         │
        │   "b2b_company_name": "Acme Manufacturing",         │
        │   "b2b_user_roles": "supplier_user",                 │
        │                                                      │
        │   "scope": "openid profile email read:orders",       │
        │   "iat": 1234567890,                                 │
        │   "exp": 1234571490                                  │
        │ }                                                    │
        │ [Signed with IKEA Auth Server's private key]        │
        │                                                      │
        └──────────────────────────────────────────────────────┘
                │
                ▼
        
STEP 7: Token returned to IKEA SPA frontend
        ┌─────────────────────────────────┐
        │ Frontend                        │
        ├─────────────────────────────────┤
        │ Received JWT:                   │
        │ → Store in HTTP-Only cookie or │
        │   secure session storage        │
        │                                 │
        │ Alice is now authenticated      │
        │ & scoped to company_id=1        │
        └─────────────────────────────────┘
                │
                ▼

═══════════════════════════════════════════════════════════════════════════════

STEP 8: Alice tries to access an order
        ┌──────────────────────────────────┐
        │ IKEA Portal                      │
        │ [View Orders]                    │
        │ GET /api/resources/order-12345  │
        └──────────────────────────────────┘
                │
                ├─ Authorization header with JWT from step 6
                │
                ▼
        ┌─────────────────────────────────────────────────────┐
        │ B2bResourceController.getResource()                 │
        ├─────────────────────────────────────────────────────┤
        │                                                     │
        │ INPUT:                                              │
        │  ├─ resourceId = 'order-12345'                      │
        │  ├─ resourceType = 'order'                          │
        │  └─ JWT claims: b2b_company_id='1' (from token)    │
        │                                                     │
        │ AUTHORIZATION CHECK:                                │
        │ Call: canAccessResource(1, 'order-12345', 'order', 'READ')
        │                                                     │
        │   Query: SELECT * FROM b2b_resource_company_access │
        │          WHERE resource_id='order-12345'            │
        │            AND resource_type='order'                │
        │            AND b2b_company_id=1                     │
        │                                                     │
        │   Found: { access_level: 'READ' }                   │
        │   ✓ User's company can READ this resource           │
        │                                                     │
        │ RESULT: 200 OK                                      │
        │ {                                                   │
        │   "resource_id": "order-12345",                     │
        │   "order_number": "ORD-2024-001234",               │
        │   "company_id": 1,                                  │
        │   "items": [...],                                   │
        │   "status": "pending"                               │
        │ }                                                   │
        │                                                     │
        └─────────────────────────────────────────────────────┘
                │
                ▼

STEP 9: Alice tries to access a ProLog order (should fail)
        ┌──────────────────────────────────┐
        │ Alice (confused) tries:          │
        │ GET /api/resources/order-99999  │
        │ (This order is for ProLog)       │
        └──────────────────────────────────┘
                │
                ├─ Authorization header with JWT (b2b_company_id='1')
                │
                ▼
        ┌─────────────────────────────────────────────────────┐
        │ B2bResourceController.getResource()                 │
        ├─────────────────────────────────────────────────────┤
        │                                                     │
        │ INPUT:                                              │
        │  ├─ resourceId = 'order-99999'                      │
        │  ├─ resourceType = 'order'                          │
        │  └─ b2b_company_id = '1'  (from token)             │
        │                                                     │
        │ AUTHORIZATION CHECK:                                │
        │ Call: canAccessResource(1, 'order-99999', 'order', 'READ')
        │                                                     │
        │   Query: SELECT * FROM b2b_resource_company_access │
        │          WHERE resource_id='order-99999'            │
        │            AND resource_type='order'                │
        │            AND b2b_company_id=1                     │
        │                                                     │
        │   Result: Empty (no access)                         │
        │   ✗ User's company cannot access this resource      │
        │                                                     │
        │ RESULT: 403 Forbidden                               │
        │ {                                                   │
        │   "error": "Forbidden",                             │
        │   "message": "Your company does not have access     │
        │              to this resource"                      │
        │ }                                                   │
        │                                                     │
        └─────────────────────────────────────────────────────┘
                │
                ▼
        Alice's company (Acme) cannot access ProLog's orders ✓
```

---

## Multi-Company Parallel Access

```
┌──────────────────────────────────────────────────────────────────┐
│              Simultaneous Access by Different Companies           │
└──────────────────────────────────────────────────────────────────┘

T=00:00  Alice@Acme                    Charlie@ProLog
         ──────────────────────────────── 
         Logs in via Azure AD          Logs in via Google Workspace
         │                             │
         ├─ Gets JWT with              ├─ Gets JWT with
         │  b2b_company_id=1           │  b2b_company_id=2
         │  b2b_company_key=acme       │  b2b_company_key=prolog
         │                             │
         │                             │
T=00:05  Accesses Order #12345         Accesses Order #99999
         (Tagged for company 1)        (Tagged for company 2)
         │                             │
         ├─ Auth check:                ├─ Auth check:
         │  company_id=1               │  company_id=2
         │  resource=12345             │  resource=99999
         │  Found ✓                    │  Found ✓
         │  Response: 200 OK           │  Response: 200 OK
         │                             │
         │                             │
T=00:10  Tries to access Order #99999  Tries to access Order #12345
         (Tagged for company 2)        (Tagged for company 1)
         │                             │
         ├─ Auth check:                ├─ Auth check:
         │  company_id=1               │  company_id=2
         │  resource=99999             │  resource=12345
         │  NOT Found ✗                │  NOT Found ✗
         │  Response: 403 Forbidden    │  Response: 403 Forbidden
         │                             │
         ▼                             ▼
         ✓ Data isolated per company ✓
```

---

## Admin Tasks Flow

```
┌──────────────────────────────────┐
│ IKEA Admin Portal                │
└──────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 1. REGISTER NEW B2B COMPANY                                     │
└─────────────────────────────────────────────────────────────────┘

Admin enters:
  Company Name: "Acme Manufacturing"
  Company Key: "acme"
  Description: "Furniture components supplier"

        ▼
  
POST /api/b2b/company/register

        ▼

INSERT INTO b2b_company (company_name, company_key, description)
VALUES ('Acme Manufacturing', 'acme', ...)

        ▼

Database:
  b2b_company {
    id: 1
    company_name: "Acme Manufacturing"
    company_key: "acme"
  }

┌─────────────────────────────────────────────────────────────────┐
│ 2. SETUP EXTERNAL IDP FOR COMPANY                               │
└─────────────────────────────────────────────────────────────────┘

Admin enters or IKEA provides:
  Registration ID: "acme_azure"
  Client ID: "acme-oauth-client-id"
  Client Secret: "***[encrypted]***"
  Endpoints: [issuer, auth, token, jwks, userinfo URIs]

        ▼

POST /api/clients/idp

        ▼

INSERT INTO customer_idp_config (
  tenant_id = "acme",
  registration_id = "acme_azure",
  ...
)

        ▼

Then link company to IDP:

POST /api/b2b/company/1/link-idp
{
  "idp_registration_id": "acme_azure"
}

        ▼

INSERT INTO b2b_company_idp_mapping (
  b2b_company_id = 1,
  customer_idp_config_id = [ID from customer_idp_config]
)

        ▼

Database now knows: Acme ↔ Azure AD

┌─────────────────────────────────────────────────────────────────┐
│ 3. PROVISION COMPANY USERS                                      │
└─────────────────────────────────────────────────────────────────┘

Admin enters or auto-syncs via SCIM:
  User ID: "acme-user-123"
  Email: "alice@acme.com"
  Roles: "supplier_admin"

        ▼

POST /api/b2b/users/provision
{
  "b2b_company_id": 1,
  "users": [
    {
      "federated_user_id": "acme-user-123",
      "federated_email": "alice@acme.com",
      "idp_registration_id": "acme_azure",
      "user_roles": "supplier_admin"
    }
  ]
}

        ▼

INSERT INTO b2b_user_company_mapping (
  federated_user_id = "acme-user-123",
  federated_email = "alice@acme.com", 
  idp_registration_id = "acme_azure",
  b2b_company_id = 1,
  user_roles = "supplier_admin"
)

        ▼

Database now knows: Alice (acme-user-123) → Acme company

┌─────────────────────────────────────────────────────────────────┐
│ 4. TAG RESOURCES FOR COMPANY ACCESS                             │
└─────────────────────────────────────────────────────────────────┘

Admin/System selects resources and assigns:
  Resource: "order-12345"
  Type: "order"
  Company: "Acme" (id=1)
  Access Level: "READ"

        ▼

POST /api/resources/order-12345/assign-company
{
  "resource_type": "order",
  "b2b_company_id": 1,
  "access_level": "READ"
}

        ▼

INSERT INTO b2b_resource_company_access (
  resource_id = "order-12345",
  resource_type = "order",
  b2b_company_id = 1,
  access_level = "READ"
)

        ▼

Database now knows: Order #12345 is readable by Acme

┌─────────────────────────────────────────────────────────────────┐
│ 5. MONITOR ACCESS & AUDIT                                       │
└─────────────────────────────────────────────────────────────────┘

Admin views audit log:

SELECT * FROM b2b_access_audit_log
WHERE b2b_company_id = 1
ORDER BY timestamp DESC

        ▼

Results:
  ✓ alice@acme.com READ order-12345 at 2024-05-17 10:30:45
  ✓ alice@acme.com READ order-12345 at 2024-05-17 10:25:12
  ✗ bob@acme.com READ order-99999 DENIED at 2024-05-17 10:20:00

        ▼

Admin can investigate anomalies or approve access requests
```

---

## Summary Table: Mapping B2B Concepts

```
IKEA System Component → Database Table → Purpose
───────────────────────────────────────────────────────────────────

Company Registration     b2b_company
  ├─ Company Name ──────→ company_name
  ├─ Company Key ───────→ company_key
  └─ Description ───────→ description

Company ↔ IDP Link      b2b_company_idp_mapping
  ├─ Company ──────────→ b2b_company_id
  ├─ IDP Config ───────→ customer_idp_config_id
  └─ Status ───────────→ status

User Provisioning       b2b_user_company_mapping
  ├─ Federated ID ─────→ federated_user_id (from external IDP)
  ├─ Email ────────────→ federated_email
  ├─ Company ──────────→ b2b_company_id
  ├─ Roles ────────────→ user_roles
  └─ IDP ──────────────→ idp_registration_id

Resource Access         b2b_resource_company_access
  ├─ Resource ID ──────→ resource_id
  ├─ Resource Type ────→ resource_type (order, inventory, etc)
  ├─ Company ──────────→ b2b_company_id
  └─ Access Level ─────→ access_level (READ, WRITE, ADMIN)

Audit Trail             b2b_access_audit_log
  ├─ Company ──────────→ b2b_company_id
  ├─ User ─────────────→ federated_user_id
  ├─ Resource ─────────→ resource_id + resource_type
  ├─ Action ───────────→ action (READ, WRITE, DELETE)
  └─ Timestamp ────────→ timestamp
```

---

## Security Model

```
┌─────────────────────────────────────────────────────────────┐
│              B2B Security Layers                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ Layer 1: Authentication (External IDP)                     │
│ ├─ User authenticates with their company's IDP             │
│ ├─ (Azure AD, Google, Okta, etc.)                          │
│ └─ IDP issues ID token with user claims                    │
│                                                             │
│ Layer 2: Token Enrichment (Spring Auth Server)             │
│ ├─ Spring verifies external ID token signature             │
│ ├─ Maps IDP registration_id → company                      │
│ ├─ Looks up user in b2b_user_company_mapping               │
│ └─ Adds b2b_company_id, b2b_company_key to token           │
│                                                             │
│ Layer 3: API Authorization (Resource Controller)           │
│ ├─ Extract b2b_company_id from JWT claims                  │
│ ├─ Validate company exists and is ACTIVE                   │
│ └─ Check JWT signature using IKEA's public key             │
│                                                             │
│ Layer 4: Resource Access Control (Service Layer)           │
│ ├─ Query b2b_resource_company_access table                 │
│ ├─ Verify company has access to resource                   │
│ ├─ Verify access level matches operation (READ vs WRITE)   │
│ └─ Log access attempt for audit                            │
│                                                             │
│ Layer 5: Data Filtering (Query Level)                      │
│ ├─ WHERE b2b_company_id = extracted_company_id             │
│ ├─ Prevents accidental data leakage                        │
│ └─ Defense-in-depth principle                              │
│                                                             │
└─────────────────────────────────────────────────────────────┘

Access Denial Scenarios:
─────────────────────────

Scenario 1: Invalid Token
  Request: GET /api/resources/order-123
           Authorization: Bearer [EXPIRED_TOKEN]
  Result: 401 Unauthorized

Scenario 2: Unknown Company
  Request: Token has b2b_company_id=99999 (doesn't exist)
           GET /api/resources/order-123
  Result: 403 Forbidden (company not found)

Scenario 3: Suspended Company
  Request: Company status='SUSPENDED'
           GET /api/resources/order-123
  Result: 403 Forbidden (company is suspended)

Scenario 4: Resource Not Tagged for Company
  Request: order-123 not in b2b_resource_company_access
           for b2b_company_id=1
           GET /api/resources/order-123
  Result: 403 Forbidden (no access)

Scenario 5: Insufficient Access Level
  Request: b2b_resource_company_access.access_level='READ'
           PUT /api/resources/order-123 (requires WRITE)
  Result: 403 Forbidden (insufficient permissions)
```

---

## Deployment Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    Production Deployment                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────┐     ┌──────────────────────┐         │
│  │  IKEA SPA Frontend   │     │  Admin Dashboard     │         │
│  │  (React/Vue)         │     │  (Company/Resource   │         │
│  │  - Login UI          │     │   Management)        │         │
│  │  - Order Portal      │     │                      │         │
│  │  - Data Viewer       │     │  Admin-only routes   │         │
│  └──────┬───────────────┘     └──────┬───────────────┘         │
│         │ HTTPS                      │ HTTPS + MFA              │
│         │                            │                         │
│         └────────────┬────────────────┘                         │
│                      │                                          │
│                      ▼                                          │
│         ┌────────────────────────────┐                          │
│         │  Spring Auth Server        │                          │
│         │  (Port 8443 HTTPS)         │                          │
│         ├────────────────────────────┤                          │
│         │ • OAuth2/OIDC endpoints    │                          │
│         │ • Token issuance           │                          │
│         │ • B2B context enrichment   │                          │
│         │ • Resource access service  │                          │
│         │                            │                          │
│         │ Stateless (can scale horiz)│                          │
│         └────────────┬───────────────┘                          │
│                      │                                          │
│      ┌───────────────┼───────────────┐                          │
│      │               │               │                         │
│      ▼               ▼               ▼                         │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐                   │
│  │ Azure AD │   │ Google   │   │ Okta     │                   │
│  │ (Acme)   │   │ Workspace│   │ (BR)     │                   │
│  │          │   │ (ProLog) │   │          │                   │
│  └──────────┘   └──────────┘   └──────────┘                   │
│   (External)     (External)     (External)                    │
│                                                                 │
│      ┌──────────────────────────────────────┐                  │
│      │  PostgreSQL Database                 │                  │
│      │  (with read replicas for scaling)    │                  │
│      ├──────────────────────────────────────┤                  │
│      │ • customer_idp_config                │                  │
│      │ • b2b_company                        │                  │
│      │ • b2b_company_idp_mapping            │                  │
│      │ • b2b_user_company_mapping           │                  │
│      │ • b2b_resource_company_access        │                  │
│      │ • b2b_access_audit_log               │                  │
│      │ • authorization (tokens)             │                  │
│      │ • client (internal apps)             │                  │
│      └──────────────────────────────────────┘                  │
│                                                                 │
│      ┌──────────────────────────────────────┐                  │
│      │  Redis Cache                         │                  │
│      │  (Client configs, company metadata)  │                  │
│      └──────────────────────────────────────┘                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

High Availability:
──────────────────
• Multiple Spring Auth Server instances behind load balancer
• PostgreSQL with streaming replication (readers)
• Redis cluster for distributed caching
• External IDPs provide their own HA
```

---

This visual architecture allows IKEA to:
1. ✓ Federate authentication to partner companies' IDPs
2. ✓ Automatically route users to correct company context
3. ✓ Enforce data access boundaries at token + API + database levels
4. ✓ Audit all access for compliance
5. ✓ Scale to hundreds of B2B partner companies


