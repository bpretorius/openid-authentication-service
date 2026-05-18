# Multi-Tenancy Architecture Documentation Index

> Authentication/Authorization split: Spring Auth + external IDPs handle authentication, while OpenFGA handles dynamic authorization. Start with `OPENFGA_AUTHORIZATION_MODEL.md`.

## Start Here

If you're new to this codebase or trying to understand multi-tenancy:

### OpenFGA Authorization (Recommended First for Permissions)
**File**: [OPENFGA_AUTHORIZATION_MODEL.md](./OPENFGA_AUTHORIZATION_MODEL.md)
- Defines dynamic role/permission checks in OpenFGA
- Documents sample FGA model and tuple patterns
- Shows where Spring Auth authentication ends and OpenFGA authorization begins

### 🎯 Your Specific Question Answered
**File**: [ANSWER_TO_YOUR_QUESTION.md](./ANSWER_TO_YOUR_QUESTION.md)
- Direct answers to: "How is this client request associated to tenant and registration_id?"
- Is registration_id the external IDP provider? (YES)
- What gets stored in which database table?
- Perfect if you want a quick answer, not the full story

### 📖 I Want to Understand the Complete Architecture
**File**: [TENANT_AND_CLIENT_QUICK_REFERENCE.md](./TENANT_AND_CLIENT_QUICK_REFERENCE.md)
- Start with this if you're new to the system
- Simplified diagrams and explanations
- Real-world example: Hotel chain using Google and Azure
- Answers: "What's a tenant? What's a registration ID? What's an internal client?"
- Read time: 5-10 minutes

### 📊 I'm a Visual Learner
**File**: [VISUAL_ARCHITECTURE_DIAGRAMS.md](./VISUAL_ARCHITECTURE_DIAGRAMS.md)
- ASCII architecture diagram showing all layers
- Complete OAuth flow walkthrough with numbers and arrows
- Multi-tenant isolation pattern
- Token flow diagram
- Database relationship diagrams
- Best for: Understanding the request journey from UI to database

### 💻 I Need to Implement Features or Debug Code
**File**: [CODE_IMPLEMENTATION_REFERENCE.md](./CODE_IMPLEMENTATION_REFERENCE.md)
- Exact Java class and file locations
- Code snippets showing implementation
- Where entity scanning happens
- Repository interfaces and usage
- Encryption converter usage
- Follow-the-code traces from HTTP request to database insert

### 📚 I Want the Deep Dive
**File**: [MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md](./MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md)
- Complete architectural deep-dive
- Three-tier system explained (tenant, registration ID, client)
- Request flow diagrams with all decision points
- Database schema with annotations
- Practical multi-tenant examples
- Current implementation limitations
- How to extend for hard tenant isolation
- Next steps for enhanced multi-tenancy

---

## By User Role

### I'm a Product Manager / Business Analyst
**Read in order:**
1. [ANSWER_TO_YOUR_QUESTION.md](./ANSWER_TO_YOUR_QUESTION.md) (2 min)
2. [TENANT_AND_CLIENT_QUICK_REFERENCE.md](./TENANT_AND_CLIENT_QUICK_REFERENCE.md) → "Multi-Tenant Example" section (3 min)

**Key Takeaway**: One internal app (SPA) can serve multiple tenants, each authenticated through their own external IDP (Google, Azure, etc).

### I'm a Backend Developer (Java/Spring)
**Read in order:**
1. [CODE_IMPLEMENTATION_REFERENCE.md](./CODE_IMPLEMENTATION_REFERENCE.md) (15 min)
2. [MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md](./MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md) (20 min)
3. [VISUAL_ARCHITECTURE_DIAGRAMS.md](./VISUAL_ARCHITECTURE_DIAGRAMS.md) → "Request Flow" section (5 min)

**Key Takeaway**: External IDP configs are tenant+provider pairs in `customer_idp_config`. Internal apps are in `client` table. Lookup happens in `DynamicIDPRegistrationRepository`.

### I'm a DevOps / Infrastructure Engineer
**Read:**
1. [VISUAL_ARCHITECTURE_DIAGRAMS.md](./VISUAL_ARCHITECTURE_DIAGRAMS.md) → "Master Architecture" section (3 min)
2. [TENANT_AND_CLIENT_QUICK_REFERENCE.md](./TENANT_AND_CLIENT_QUICK_REFERENCE.md) → "Three-Tier System" section (2 min)

**Key Takeaway**: Three independent systems interacting: external OAuth providers (Google, Azure), this auth server (layers 2-4), and internal apps (SPAs, backends).

### I'm Debugging a Login Issue
**Read:**
1. [VISUAL_ARCHITECTURE_DIAGRAMS.md](./VISUAL_ARCHITECTURE_DIAGRAMS.md) → "Token Flow Diagram" section (5 min)
2. [CODE_IMPLEMENTATION_REFERENCE.md](./CODE_IMPLEMENTATION_REFERENCE.md) → "Registration ID → External IDP Provider Lookup" section (3 min)

**Key Takeaway**: Trace which IDP config was loaded, verify tenant context, check redirect_uri matches.

### I'm Adding a New External IDP (like Okta, SAML, etc)
**Read:**
1. [MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md](./MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md) → "How Does Multi-Tenancy Work?" section (10 min)
2. [CODE_IMPLEMENTATION_REFERENCE.md](./CODE_IMPLEMENTATION_REFERENCE.md) → "DynamicIDPRegistrationRepository" section (5 min)
3. [ANSWER_TO_YOUR_QUESTION.md](./ANSWER_TO_YOUR_QUESTION.md) → "Database Mapping" section (3 min)

**Key Takeaway**: Add new row to `customer_idp_config` with registration_id, tenant_id, and external OAuth endpoints. No code changes needed if you're just adding a new provider.

### I'm Implementing Hard Tenant Isolation
**Read:**
1. [MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md](./MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md) → "Current Limitation & Enhancement" section (5 min)
2. [VISUAL_ARCHITECTURE_DIAGRAMS.md](./VISUAL_ARCHITECTURE_DIAGRAMS.md) → "Multi-Tenant Isolation Pattern" section (5 min)
3. Full [CODE_IMPLEMENTATION_REFERENCE.md](./CODE_IMPLEMENTATION_REFERENCE.md) (20 min)

**Key Takeaway**: Add `tenant_id` to `client` table, update all lookups to filter by both `clientId` AND `tenantId`, add tenant context extraction to auth flow.

---

## Quick Reference by Concept

### External IDP Provider (Registration ID)
- **What it is**: Name of an external OAuth/OIDC service (Google, Azure, Okta, etc)
- **Stored in**: `customer_idp_config.registration_id`
- **Example values**: "google", "azure", "okta", "custom-oidc"
- **Read**: [ANSWER_TO_YOUR_QUESTION.md](./ANSWER_TO_YOUR_QUESTION.md) + [CODE_IMPLEMENTATION_REFERENCE.md](./CODE_IMPLEMENTATION_REFERENCE.md) Registration ID section

### Tenant
- **What it is**: Organizational boundary (Hotel Chain 1, Airline X, etc)
- **Stored in**: `customer_idp_config.tenant_id`
- **Example values**: "hotel_chain_1", "airline_x", "bank_y"
- **Uniqueness**: Unique globally (one org per ID)
- **Read**: [TENANT_AND_CLIENT_QUICK_REFERENCE.md](./TENANT_AND_CLIENT_QUICK_REFERENCE.md) + [MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md](./MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md)

### Internal Client (Your Request)
- **What it is**: An application (SPA, mobile app, backend service) registered in this auth server
- **Stored in**: `client` table
- **Example values**: "hotel-reservation-spa", "mobile-app", "backend-service"
- **Uniqueness**: Unique globally per client_id, but NOT per tenant
- **Read**: [ANSWER_TO_YOUR_QUESTION.md](./ANSWER_TO_YOUR_QUESTION.md) + [CODE_IMPLEMENTATION_REFERENCE.md](./CODE_IMPLEMENTATION_REFERENCE.md) Client Registration section

### Authorization Token
- **What it is**: JWT token issued by this auth server
- **Stored in**: `authorization` table
- **Contains**: User claims (from federated IDP) + client info + scopes + expiry
- **Links together**: Tenant context (via user identity) + Client (audience) + User
- **Read**: [VISUAL_ARCHITECTURE_DIAGRAMS.md](./VISUAL_ARCHITECTURE_DIAGRAMS.md) Token Flow section

---

## Database Tables Reference

### `customer_idp_config` (Tenant's External IDP)
```
Tracks: Which external OAuth provider each tenant uses
Columns: tenant_id, registration_id, client_id, client_secret, endpoints
Uniqueness: tenant_id is UNIQUE, registration_id is UNIQUE
Primary use: Federated login (redirecting to Google/Azure/etc)
Read: MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md
```

### `client` (Internal OAuth2 Application)
```
Tracks: Apps registered to request tokens from this auth server
Columns: clientId, clientSecret, scopes, redirectUris, settings
Uniqueness: clientId is UNIQUE (not per-tenant currently)
Primary use: Client authentication and app configuration
Read: CODE_IMPLEMENTATION_REFERENCE.md + ANSWER_TO_YOUR_QUESTION.md
```

### `authorization` (Issued Tokens)
```
Tracks: Tokens issued by this auth server
Columns: access_token, refresh_token, issued_at, expires_at, scopes, principal_name
Links: registered_client_id (FK to client.id) + user identity
Primary use: Token validation, token revocation, audit log
Read: VISUAL_ARCHITECTURE_DIAGRAMS.md
```

---

## Documentation Filenames at a Glance

| File | Purpose | Length | Audience |
|------|---------|--------|----------|
| [OPENFGA_AUTHORIZATION_MODEL.md](./OPENFGA_AUTHORIZATION_MODEL.md) | Authentication/authorization split and dynamic permission model | 3 pages | Architects, Backend Developers |
| [ANSWER_TO_YOUR_QUESTION.md](./ANSWER_TO_YOUR_QUESTION.md) | Direct answer to your specific question | 3 pages | Anyone |
| [TENANT_AND_CLIENT_QUICK_REFERENCE.md](./TENANT_AND_CLIENT_QUICK_REFERENCE.md) | Quick reference with simplified diagram | 5 pages | Architects, PMs |
| [VISUAL_ARCHITECTURE_DIAGRAMS.md](./VISUAL_ARCHITECTURE_DIAGRAMS.md) | ASCII diagrams and flow charts | 8 pages | Visual learners, Debuggers |
| [MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md](./MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md) | Complete architectural deep-dive | 12 pages | Developers, Architects |
| [CODE_IMPLEMENTATION_REFERENCE.md](./CODE_IMPLEMENTATION_REFERENCE.md) | Code locations and snippets | 10 pages | Backend Developers |
| [IKEA_B2B_IMPLEMENTATION.md](./IKEA_B2B_IMPLEMENTATION.md) | Complete IKEA B2B federated IDP guide | 15 pages | Backend Developers, Architects |
| [IKEA_B2B_VISUAL_DIAGRAMS.md](./IKEA_B2B_VISUAL_DIAGRAMS.md) | ASCII diagrams and flow charts for B2B | 12 pages | Visual learners, Product Managers |
| [IKEA_B2B_IMPLEMENTATION_CHECKLIST.md](./IKEA_B2B_IMPLEMENTATION_CHECKLIST.md) | Step-by-step implementation checklist | 8 pages | Project Managers, Developers |

---

## New: IKEA B2B Multi-Tenant Federated IDP Implementation

### Use Case
IKEA (or any enterprise) wants to:
- Register B2B companies (suppliers, partners, logistics) with their own external IDPs
- Allow these companies' users to access IKEA systems
- Restrict access to resources associated with each company only
- Maintain full audit trail of all access

### Files for B2B Implementation (Read in Order)

1. **[IKEA_B2B_IMPLEMENTATION.md](./IKEA_B2B_IMPLEMENTATION.md)** 🎯 START HERE
   - Complete step-by-step implementation guide
   - Database schema for B2B companies, resources, users
   - Java entities and repositories
   - Service layer (B2B context extraction, access control)
   - API endpoints for admin and users
   - Setup scripts and examples
   - Read time: 30-40 minutes

2. **[IKEA_B2B_VISUAL_DIAGRAMS.md](./IKEA_B2B_VISUAL_DIAGRAMS.md)** 📊
   - Complete login flow diagram (step-by-step)
   - Database schema relationships
   - Multi-company parallel access example
   - Admin task flows
   - Security layers
   - Deployment architecture
   - Read time: 20-30 minutes

3. **[IKEA_B2B_IMPLEMENTATION_CHECKLIST.md](./IKEA_B2B_IMPLEMENTATION_CHECKLIST.md)** ✅
   - 8-phase implementation roadmap
   - Database migrations
   - Entity creation
   - Service layer implementation
   - Controller endpoints
   - Testing strategy
   - Deployment checklist
   - Success criteria
   - 6-week timeline with effort estimation
   - Read time: 15-20 minutes

### Key Concepts

```
IKEA B2B = Company Registration + IDP Linking + Resource-Level Access Control

┌──────────────────────────────────┐
│ IKEA (Primary Organization)      │
├──────────────────────────────────┤
│                                  │
│ B2B Companies (registered):      │
│ ├─ Acme (Azure AD)               │
│ ├─ ProLog (Google Workspace)     │
│ └─ BuildRight (Okta)             │
│                                  │
│ Each company can access ONLY     │
│ resources tagged for them        │
│                                  │
|-home-orders (Acme only)         │
│ ├─ inventory-5678 (Acme+ProLog) │
│ └─ shipment-999 (All B2B)        │
│                                  │
└──────────────────────────────────┘
```

### New Database Tables

- `b2b_company` - Company master data
- `b2b_company_idp_mapping` - Links company → external IDP
- `b2b_user_company_mapping` - Maps federated users → company
- `b2b_resource_company_access` - Resource visibility/permissions per company
- `b2b_access_audit_log` - Audit trail of all access

### Typical Workflow

1. **Admin registers B2B company** (Acme)
2. **Admin links company to IDP** (Acme's Azure AD)
3. **Admin provisions company users** (alice@acme.com → Acme)
4. **Admin tags resources** (order-12345 → readable by Acme)
5. **User logs in** via their company IDP (Alice via Azure AD)
6. **Spring enriches token** with company context (b2b_company_id=1)
7. **User accesses resource** (order-12345)
8. **API checks** if company 1 can access resource 12345 (yes)
9. **User gets data** (200 OK)
10. **Audit logged** (Alice read order-12345 at time X)

### Implementation Effort

- **Scope**: 6 weeks (2-3 senior engineers)
- **Database**: New 5-table schema
- **Backend**: ~1000 LOC for services + controllers
- **Testing**: Comprehensive unit + integration tests
- **Deployment**: Minimal risk (append new schema)

### Security Model (Defense-in-Depth)

1. **Authentication Layer**: External IDP (Azure, Google, Okta)
2. **Token Enrichment Layer**: Spring adds company context
3. **API Authorization Layer**: Verify JWT signature + company exists
4. **Resource Access Layer**: Check company has access to resource
5. **Data Filtering Layer**: Query-level WHERE company_id = X
6. **Audit Layer**: Log all access for compliance


---

## FAQ Quick Links

**Q: Is registration_id the same as tenant_id?**
A: No. `registration_id` = provider name (google), `tenant_id` = org name (hotel_chain_1). See [ANSWER_TO_YOUR_QUESTION.md](./ANSWER_TO_YOUR_QUESTION.md)

**Q: Can the same app serve multiple tenants?**
A: Yes, by design. See [TENANT_AND_CLIENT_QUICK_REFERENCE.md](./TENANT_AND_CLIENT_QUICK_REFERENCE.md) "Multi-Tenant Example"

**Q: Where does my client request go?**
A: Into the `client` table. See [CODE_IMPLEMENTATION_REFERENCE.md](./CODE_IMPLEMENTATION_REFERENCE.md) "Client Request DTO" section

**Q: How does Spring know which external IDP to use?**
A: Through `registration_id` lookup in `customer_idp_config`. See [CODE_IMPLEMENTATION_REFERENCE.md](./CODE_IMPLEMENTATION_REFERENCE.md) "Registration ID → External IDP Provider Lookup"

**Q: What's the complete OAuth flow?**
A: See [VISUAL_ARCHITECTURE_DIAGRAMS.md](./VISUAL_ARCHITECTURE_DIAGRAMS.md) "Token Flow Diagram" section

**Q: How do I add hard tenant isolation?**
A: See [MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md](./MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md) "Current Limitation & Enhancement" section

**Q: Which code file contains [specific feature]?**
A: See [CODE_IMPLEMENTATION_REFERENCE.md](./CODE_IMPLEMENTATION_REFERENCE.md) with file locations and line numbers

---

## Learning Path (Recommended)

### Time Investment: 30-60 minutes for complete understanding

**Path 1 - Executive / Quick Understanding (10 minutes)**
1. [ANSWER_TO_YOUR_QUESTION.md](./ANSWER_TO_YOUR_QUESTION.md) (3 min)
2. [TENANT_AND_CLIENT_QUICK_REFERENCE.md](./TENANT_AND_CLIENT_QUICK_REFERENCE.md) - Skim for diagrams (7 min)

**Path 2 - Developer / Implementation Ready (45 minutes)**
1. [ANSWER_TO_YOUR_QUESTION.md](./ANSWER_TO_YOUR_QUESTION.md) (3 min)
2. [VISUAL_ARCHITECTURE_DIAGRAMS.md](./VISUAL_ARCHITECTURE_DIAGRAMS.md) (15 min)
3. [CODE_IMPLEMENTATION_REFERENCE.md](./CODE_IMPLEMENTATION_REFERENCE.md) (20 min)
4. [MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md](./MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md) - Reference as needed (7 min)

**Path 3 - Architect / Complete Deep-Dive (60 minutes)**
1. [ANSWER_TO_YOUR_QUESTION.md](./ANSWER_TO_YOUR_QUESTION.md) (3 min)
2. [TENANT_AND_CLIENT_QUICK_REFERENCE.md](./TENANT_AND_CLIENT_QUICK_REFERENCE.md) (10 min)
3. [MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md](./MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md) (20 min)
4. [VISUAL_ARCHITECTURE_DIAGRAMS.md](./VISUAL_ARCHITECTURE_DIAGRAMS.md) (15 min)
5. [CODE_IMPLEMENTATION_REFERENCE.md](./CODE_IMPLEMENTATION_REFERENCE.md) (12 min)

---

## Print-Friendly Versions

All documents are markdown-formatted and can be printed or exported to PDF using your IDE.

**Recommended print order:**
1. [ANSWER_TO_YOUR_QUESTION.md](./ANSWER_TO_YOUR_QUESTION.md)
2. [TENANT_AND_CLIENT_QUICK_REFERENCE.md](./TENANT_AND_CLIENT_QUICK_REFERENCE.md)
3. [VISUAL_ARCHITECTURE_DIAGRAMS.md](./VISUAL_ARCHITECTURE_DIAGRAMS.md)
4. [CODE_IMPLEMENTATION_REFERENCE.md](./CODE_IMPLEMENTATION_REFERENCE.md)
5. [MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md](./MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md)

---

## Where to Find These Files

All documentation is in the root directory of the project:
- `./ANSWER_TO_YOUR_QUESTION.md`
- `./TENANT_AND_CLIENT_QUICK_REFERENCE.md`
- `./VISUAL_ARCHITECTURE_DIAGRAMS.md`
- `./CODE_IMPLEMENTATION_REFERENCE.md`
- `./MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md`
- `./ARCHITECTURE_DOCUMENTATION_INDEX.md` (this file)

---

## Summary

Your question asked about the relationship between:
- **Client Request** (your POST to create demo-client)
- **Tenant** (organizational boundary)
- **Registration ID** (external IDP provider)

**Quick Answer:**
- Your request creates an internal app (NOT tenant-specific, NOT a registration ID)
- Registration ID IS the external IDP provider (google, azure, etc) ✓
- Tenant is the organization (hotel_chain_1, airline_x, etc) ✓
- They relate through the **login flow**, not through direct schema FK relationships

Start with [ANSWER_TO_YOUR_QUESTION.md](./ANSWER_TO_YOUR_QUESTION.md) for the direct answer, then explore other files based on your role and depth of understanding needed.

---

**Last Updated**: May 17, 2026
**Organization**: Multi-Tenant Spring Authorization Server with Federated IDP Support

