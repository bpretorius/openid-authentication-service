## Why Spring Authorization Server?

| Feature | Use Spring Auth Server | Use Azure / Okta / etc. |
|---------|----------------------|------------------------|
| Control over token logic | ✅ Full control | ❌ Limited flexibility |
| Setup effort | ❌ More work | ✅ Easy setup |
| Custom claims & enrichment | ✅ Custom logic | ❌ Limited flexibility |
| Maintenance burden | ❌ More work | ✅ Managed service / less maintenance |
| Multi-tenant flexibility | ✅ Multi-tenant flexibility | ❌ Limited flexibility |
| Vendor independence | ✅ No vendor lock-in | ❌ Vendor dependency |
| Cost | ✅ Free / open source | ❌ Per-user or monthly licensing fees |
| Hosting | ✅ Self-hosted — runs anywhere | ❌ Cloud-dependent / SaaS only |
| Data sovereignty | ✅ All data stays in your infrastructure | ❌ User data stored in vendor cloud |



### Project Tech Stack

```
┌───────────────────────────────────────────────────────────────────────────────┐
│ Layer 1 — Frontend (SPA)                                                      │
│ - React SPA (IKEA Portal / Supplier UI / Admin Dashboard)                     │
│ - Initiates SSO login via /sso (company selection)                            │
│ - Stores JWT in HTTP-Only cookie / secure session                             │
│ - Calls Spring BFF for all API operations (never hits resource APIs directly) │
└───────────────────────────────────────────────┬───────────────────────────────┘
                                               │ HTTPS
                                               ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│ Layer 2 — Spring Backend for Frontend (BFF)                                   │
│ - Spring Boot + Spring Security (Resource Server mode)                        │
│ - Validates IKEA-issued JWT on every request                                  │
│ - Extracts tenant/company claims (tenantId, b2b_company_id) from token        │
│ - Enforces role/scope checks before proxying to downstream services           │
│ - Returns filtered, company-scoped responses to the SPA                       │
└───────────────────┬──────────────────────────────────────┬────────────────────┘
                    │                                      │
                    ▼                                      ▼
┌───────────────────────────────────┐   ┌─────────────────────────────────────┐
│ Layer 3a — Spring Auth Server     │   │ Layer 3b — Protected Resource APIs   │
│ (this project)                    │   │ - IKEA Order/Inventory/Shipment APIs  │
│ - OAuth2.1 / OIDC server          │   │ - Spring Boot + Spring Security       │
│ - Federated IDP routing           │   │ - Accepts IKEA JWTs (from Auth Server)│
│   (Google, Okta, etc.)            │   │ - Data scoped: WHERE company_id=claim │
│ - JWT token issuance + JWK keys   │   └─────────────────────────────────────┘
│ - Token enrichment                │
│   (tenantId, roles, b2b_company)  │
│ - MFA enforcement                 │
│ - /sso, /oauth2/*, /login         │
└───────────────────────────────────┘
                    │
                    ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│ Layer 4 — Data                                                                │
│                                                                               │
│  ┌──────────────────────────────────────┐                                    │
│  │ PostgreSQL (JPA / Hibernate / Flyway)│                                    │
│  │ - client               (users)       │                                    │
│  │ - authorization        (tokens)      │                                    │
│  │ - customer_idp_config  (IDP config)  │                                    │
│  │ - b2b_company*         (B2B tables)  │                                    │
│  └──────────────────────────────────────┘                                    │
│  ┌──────────────────────────────────────┐                                    │
│  │ Redis                                │                                    │
│  │ - Session / security context cache   │                                    │
│  │ - IDP + client config cache          │                                    │
│  └──────────────────────────────────────┘                                    │
└───────────────────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│ Layer 5 — DevOps + Tooling                                                    │
│ - Maven (build)                                                               │
│ - Docker (Dockerfile for containerised deployment)                            │
│ - Bruno (API test collection)                                                 │
└───────────────────────────────────────────────────────────────────────────────┘

📍 Roadmap: Fine-grained authorization engine (e.g. OpenFGA) — not yet implemented.
```


## Complete Login & Access Flow

### Alice (Captegmini) — Happy Path

```
  React SPA                Spring Auth Server           Google IDP
  ─────────                ──────────────────           ──────────
  1. Select company        
     "Captegmini"    ───▶  POST /sso
                           (registration_id=capgemini)
                           Lookup customer_idp_config
                           Build ClientRegistration  ───▶ 302 → Google login
                                                          Alice enters credentials
                                                    ◀─── code + state callback
                           Exchange code for token
                           Validate Google JWT
                           Enrich token:
                            tenantId = captegmini
                            b2b_company_id = 1
                            roles = supplier_user
                           Issue IKEA-signed JWT     
  ◀─── JWT stored in       
       secure session      
```

### Alice Accesses a Resource

> **What is the Resource API?**
> The Resource API is IKEA's own backend service (e.g. Orders API, Inventory API, Shipments API) — a separate Spring Boot app that accepts the IKEA-issued JWT and only returns data scoped to the company in the token.

```
  React SPA          Spring Auth Server     Spring BFF             Resource API
  ─────────          ──────────────────     ──────────             ────────────
                     (issued JWT at login   (validates every
                      — already done)        request)

  GET /orders/12345 ─────────────────────▶  Verify JWT signature
                                            (using Auth Server's
                                             public JWK key)
                                            Extract b2b_company_id=1
                                            Check role/scope OK
                                                             ───▶  Query WHERE company_id=1
                                                                   Found order-12345 ✓
                                            ◀─── 200 OK + data
  ◀─── Order displayed ✓

  GET /orders/99999 ─────────────────────▶  Verify JWT (company_id=1)
                                            Check role/scope OK
                                                             ───▶  Query WHERE company_id=1
                                                                   order-99999 = ProLog ✗
                                            ◀─── 403 Forbidden
  ◀─── Access denied ✗
```

---

## Multi-Company Parallel Access

```
┌──────────────────────────────────────────────────────────────────┐
│              Simultaneous Access by Different Companies           │
└──────────────────────────────────────────────────────────────────┘

T=00:00  Alice@Captegmini              Charlie@ProLog
         ──────────────────────────────── 
         Logs in via Google            Logs in via Google Workspace
         │                             │
         ├─ Gets JWT with              ├─ Gets JWT with
         │  b2b_company_id=1           │  b2b_company_id=2
         │  b2b_company_key=captegmini │  b2b_company_key=prolog
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
  Company Name: "Captegmini Manufacturing"
  Company Key: "captegmini"
  Description: "Furniture components supplier"

        ▼
  
POST /api/b2b/company/register

        ▼

INSERT INTO b2b_company (company_name, company_key, description)
VALUES ('Captegmini Manufacturing', 'captegmini', ...)

        ▼

Database:
  b2b_company {
    id: 1
    company_name: "Captegmini Manufacturing"
    company_key: "captegmini"
  }

┌─────────────────────────────────────────────────────────────────┐
│ 2. SETUP EXTERNAL IDP FOR COMPANY                               │
└─────────────────────────────────────────────────────────────────┘

Admin enters or IKEA provides:
  Registration ID: "capgemini"
  Client ID: "captegmini-oauth-client-id"
  Client Secret: "***[encrypted]***"
  Endpoints: [issuer, auth, token, jwks, userinfo URIs]

        ▼

POST /api/clients/idp

        ▼

INSERT INTO customer_idp_config (
  tenant_id = "captegmini",
  registration_id = "capgemini",
  ...
)

        ▼

Then link company to IDP:

POST /api/b2b/company/1/link-idp
{
  "idp_registration_id": "capgemini"
}

        ▼

INSERT INTO b2b_company_idp_mapping (
  b2b_company_id = 1,
  customer_idp_config_id = [ID from customer_idp_config]
)

        ▼

Database now knows: Captegmini ↔ Google

┌─────────────────────────────────────────────────────────────────┐
│ 3. PROVISION COMPANY USERS                                      │
└─────────────────────────────────────────────────────────────────┘

Admin enters or auto-syncs via SCIM:
  User ID: "captegmini-user-123"
  Email: "alice@captegmini.com"
  Roles: "supplier_admin"

        ▼

POST /api/b2b/users/provision
{
  "b2b_company_id": 1,
  "users": [
    {
      "federated_user_id": "captegmini-user-123",
      "federated_email": "alice@captegmini.com",
      "idp_registration_id": "capgemini",
      "user_roles": "supplier_admin"
    }
  ]
}

        ▼

INSERT INTO b2b_user_company_mapping (
  federated_user_id = "captegmini-user-123",
  federated_email = "alice@captegmini.com", 
  idp_registration_id = "capgemini",
  b2b_company_id = 1,
  user_roles = "supplier_admin"
)

        ▼

Database now knows: Alice (captegmini-user-123) → Captegmini company

┌─────────────────────────────────────────────────────────────────┐
│ 4. TAG RESOURCES FOR COMPANY ACCESS                             │
└─────────────────────────────────────────────────────────────────┘

Admin/System selects resources and assigns:
  Resource: "order-12345"
  Type: "order"
  Company: "Captegmini" (id=1)
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

Database now knows: Order #12345 is readable by Captegmini

┌─────────────────────────────────────────────────────────────────┐
│ 5. MONITOR ACCESS & AUDIT                                       │
└─────────────────────────────────────────────────────────────────┘

Admin views audit log:

SELECT * FROM b2b_access_audit_log
WHERE b2b_company_id = 1
ORDER BY timestamp DESC

        ▼

Results:
  ✓ alice@captegmini.com READ order-12345 at 2024-05-17 10:30:45
  ✓ alice@captegmini.com READ order-12345 at 2024-05-17 10:25:12
  ✗ bob@captegmini.com READ order-99999 DENIED at 2024-05-17 10:20:00

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
│ ├─ (Google, Okta, etc.)                                    │
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
│  │ Google   │   │ Google   │   │ Okta     │                   │
│  │ (Captegmini)│ │ Workspace│   │ (BR)     │                   │
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
