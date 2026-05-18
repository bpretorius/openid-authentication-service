# IKEA B2B Federated IDP Implementation - Executive Summary

> Updated authorization strategy: Spring Auth/IDP handles authentication, and OpenFGA handles dynamic roles/permissions for company and resource access.

## The Challenge

**Company**: IKEA (or any enterprise)

**Problem Statement**:
> IKEA wants to register other companies (suppliers, partners, logistics providers) in their platform. These external companies have their own identity providers (Azure AD, Google, Okta, etc.). IKEA needs to allow users from these external companies to access IKEA systems, BUT only see and modify resources that are associated with their specific company. Additionally, users from one company should NOT be able to access resources from another company.

---

## The Solution Architecture

### Three Core Components

#### 1. **External IDP Integration** (Already Exists)
- External companies have their own Azure AD, Google Workspace, Okta, etc.
- Users authenticate with their company's IDP
- Spring Auth Server acts as an OAuth2/OIDC client to these external IDPs
- Users get an ID token from their company's IDP

#### 2. **B2B Company Registry** (NEW - Add 5 Tables)
The IKEA platform tracks:
- Which external companies are registered
- Which external IDP each company uses
- Which users belong to which company
- Which IKEA resources each company can access

#### 3. **Resource-Level Access Control** (NEW - Add Authorization Layer)
- IKEA resources (orders, inventory, shipments, etc.) are tagged with company associations
- When a user requests a resource, the system checks: "Does this user's company have access to this resource?"
- If yes → Return data (200 OK)
- If no → Block access (403 Forbidden)

---

## How It Works: Step-by-Step

### Example: Alice from Acme Manufacturing Logs In

```
1. Alice visits: https://ikea-portal.example.com
   └─ Clicks "Sign In"

2. System shows company options:
   ├─ □ Acme Manufacturing
   ├─ □ ProLog Logistics
   └─ □ BuildRight Construction
   
   └─ Alice selects "Acme Manufacturing"

3. Alice is redirected to Acme's Azure AD login page
   └─ Alice enters: alice@acme.com / password

4. Acme's Azure AD validates and returns token
   ├─ sub: "acme-user-123" (Alice's ID in Acme's system)
   ├─ email: "alice@acme.com"
   └─ Issued by: Microsoft Azure

5. Spring Auth Server (IKEA) processes the token
   ├─ Verifies token signature with Azure's public key
   ├─ Extracts: sub="acme-user-123", email="alice@acme.com"
   ├─ Looks up: Which company uses Azure? → "Acme Manufacturing" (id=1)
   ├─ Looks up: Which company is user "acme-user-123"? → Company 1
   ├─ Determines: User's company context = Acme (id=1)
   └─ **ENRICHES TOKEN** with company info:
      ├─ b2b_company_id: "1"
      ├─ b2b_company_key: "acme"
      ├─ b2b_company_name: "Acme Manufacturing"
      └─ b2b_user_roles: "supplier_user"

6. Spring generates JWT and returns to Alice
   └─ JWT now contains company context

7. Alice tries to access an order: GET /api/resources/order-12345
   ├─ API extracts: b2b_company_id="1" from JWT
   ├─ Questions: "Can company 1 access order-12345?"
   ├─ Checks database: "Is order-12345 tagged for company 1?"
   ├─ Answer: YES ✓
   └─ Returns: Full order details (200 OK)

8. Alice tries to access different order: GET /api/resources/order-99999
   ├─ API extracts: b2b_company_id="1" from JWT
   ├─ Questions: "Can company 1 access order-99999?"
   ├─ Checks database: "Is order-99999 tagged for company 1?"
   ├─ Checks database: (order-99999 is tagged for company 2 - ProLog)
   ├─ Answer: NO ✗
   └─ Returns: 403 Forbidden "Your company does not have access"

9. Audit trail records:
   ├─ alice@acme.com READ order-12345 ✓ at 10:30:45
   ├─ alice@acme.com READ order-99999 ✗ DENIED at 10:31:12
```

---

## What Needs to Be Built

### New Database Tables (5 tables)

```sql
b2b_company
├─ id, company_name, company_key, description
└─ Stores: Acme, ProLog, BuildRight

b2b_company_idp_mapping
├─ b2b_company_id, customer_idp_config_id
└─ Links: Acme → Azure AD, ProLog → Google, BuildRight → Okta

b2b_user_company_mapping
├─ federated_user_id, federated_email, idp_registration_id, b2b_company_id
└─ Links: "acme-user-123" (from Azure) → Acme company

b2b_resource_company_access
├─ resource_id, resource_type, b2b_company_id, access_level
└─ Stores: order-12345 → Acme [READ], order-99999 → ProLog [READ]

b2b_access_audit_log
├─ b2b_company_id, federated_user_id, resource_id, action, timestamp
└─ Audit: Alice read order-12345
```

### New Java Components (~1000 LOC)

**Services**:
- `B2bContextService` - Extracts company context from federated token
- `B2bResourceAccessService` - Checks if company can access resource
- `B2bCompanyService` - CRUD operations for companies
- `B2bAuditService` - Logs access attempts

**Controllers**:
- `B2bCompanyAdminController` - Register/manage companies
- `B2bUserManagementController` - Provision/manage users
- `B2bResourceAccessController` - Tag resources with company access
- `B2bResourceController` - User-facing API with access control
- `B2bAuditController` - View access logs

**Entities & Repositories** (10 files)
- JPA entities for 5 tables
- Reader/Writer repository interfaces

### Security Implementation

**Defense-in-Depth**: Access is controlled at 5 layers

1. **Authentication**: User logs in via external IDP (Azure, Google, Okta)
2. **Token Enrichment**: Spring adds company context to JWT
3. **API Authorization**: Verify JWT signature + company exists
4. **Resource Access**: Check company has permission for resource
5. **Data Filtering**: Query-level WHERE company_id = X

**Example**:
- If Alice's company_id=1, could she theoretically change SQL to `WHERE company_id=2`? NO - all 5 layers protect against this

---

## Implementation Timeline

| Phase | What | Duration | Owner |
|-------|------|----------|-------|
| 1 | Database schema + entities | 1-2 weeks | Backend Dev |
| 2 | Service layer (B2B context, access control) | 1-2 weeks | Backend Dev |
| 3 | Token customization (add company context) | 3-4 days | Backend Dev |
| 4 | API controllers + endpoints | 1 week | Backend Dev |
| 5 | Comprehensive testing | 1 week | QA + Developers |
| 6 | Database migrations + seed data | 2-3 days | DevOps + DBA |
| 7 | Documentation + training | 3-5 days | Technical Writer |
| 8 | Deployment + monitoring | 2-3 days | DevOps |
| **TOTAL** | | **6 weeks** | **2-3 engineers** |

---

## Key Design Decisions

### 1. **Company Context in JWT Token**
Instead of:
- Looking up company on every request (slow)

We:
- Add company context to JWT once at login
- Every request carries company context
- Result: Fast, no extra database queries

### 2. **Multi-Layered Authorization**
Instead of:
- Trusting company_id from user input

We:
- Verify JWT signature (ensures it came from Auth Server)
- Extract company_id from signed JWT (trustworthy)
- Double-check company exists in database
- Verify company is ACTIVE
- Result: Defense-in-depth, hard to circumvent

### 3. **Resource Tagging**
Instead of:
- Implicit resource ownership (based on creator)

We:
- Explicit resource→company mappings in database
- Admin can assign/reassign resources dynamically
- No code changes needed to add/modify access
- Result: Flexible, auditable, easy to manage

### 4. **Append-Only Audit Log**
Instead of:
- Updating audit records

We:
- Insert-only audit entries
- Impossible to cover tracks
- Compliant with regulations (SOX, HIPAA, etc.)
- Result: Forensic evidence preserved

---

## What Stays the Same

✓ Existing OAuth2/OIDC flow with external IDPs
✓ Existing Spring Auth Server + JWT token issuance
✓ Existing SecurityConfig and authorization rules
✓ Existing internal client registration (`client` table)

**No breaking changes** - Just adding new tables and services!

---

## What Changes

✗ Token structure (adds 4 new claims for B2B context)
✗ All resource endpoints (add access control checks)
✗ Admin workflows (add company registration/management)
✗ Deployment (new database schema via Flyway)

**All changes are backward-compatible** except token structure (which existing clients don't validate anyway)

---

## Real-World Example

### Current IKEA Setup (Before B2B)
```
IKEA Portal
└─ Internal employees log in
   └─ All employees can see all resources
   └─ Role-based access (admin, manager, viewer)
```

### Future IKEA Setup (After B2B)
```
IKEA Portal
├─ Internal employees (existing)
│  └─ Can see all IKEA resources (unchanged)
│
└─ B2B Companies (NEW)
   ├─ Acme Manufacturing (Azure AD)
   │  ├─ alice@acme.com → can see: orders 12345, 12346, inventory 5678
   │  └─ bob@acme.com → can see: orders 12345, 12346, inventory 5678
   │
   ├─ ProLog Logistics (Google)
   │  ├─ charlie@prolog.com → can see: inventory 5678, shipments 999
   │  └─ dave@prolog.com → can see: inventory 5678, shipments 999
   │
   └─ BuildRight Construction (Okta)
      └─ eve@buildright.com → can see: shipments 999
```

---

## Risk Assessment & Mitigation

### Risk 1: Token Tampering
**Risk**: Malicious user modifies JWT to claim different company_id
**Mitigation**: JWT is signed by private key; tampering invalidates signature
**Status**: ✓ Mitigated

### Risk 2: Data Leakage at Database Level
**Risk**: Someone bypasses API, queries database directly for all companies
**Mitigation**: Database-level access control + audit logging
**Status**: ✓ Mitigated (org responsibility)

### Risk 3: Performance Degradation
**Risk**: Adding access checks on every request slows down system
**Mitigation**: Cache company metadata in Redis; use database indexes
**Status**: ✓ Mitigated (with proper caching)

### Risk 4: User Provisioning Errors
**Risk**: Admin mistakenly assigns user to wrong company
**Mitigation**: Audit trail shows who did what and when; easy to fix
**Status**: ✓ Mitigated (user error, not security flaw)

---

## Success Metrics

**Day 1 (Launch)**:
- ✓ 3 B2B companies registered
- ✓ 20+ B2B users provisioned
- ✓ 100+ resources tagged with company associations
- ✓ Zero unauthorized access incidents

**Month 1**:
- ✓ 10+ B2B companies
- ✓ <1% unauthorized access attempts
- ✓ 100% audit trail accuracy
- ✓ P99 latency < 200ms

**Year 1**:
- ✓ 100+ B2B companies
- ✓ 99.9% service uptime
- ✓ Zero data breaches
- ✓ Full compliance audit cleared

---

## Next Steps

### Immediate (This Month)
1. Review this implementation guide with your team
2. Assess current infrastructure (database, caching, deployment)
3. Identify 2-3 pilot B2B companies for testing
4. Begin Phase 1 (database schema)

### Short-term (Next 2 Months)
1. Complete Phases 1-4 (code implementation)
2. Conduct security review with internal security team
3. Begin Phase 5 testing with pilot companies
4. Prepare production deployment plan

### Medium-term (Months 3-6)
1. Soft launch with pilot companies
2. Monitor performance + security metrics
3. Gather feedback from B2B partners
4. Scale to 50+ companies

---

## Documentation Available

**Start with**: `IKEA_B2B_IMPLEMENTATION.md`
- Complete step-by-step technical guide
- Full code examples (Java, SQL, API requests)
- Setup scripts

**Then read**: `IKEA_B2B_VISUAL_DIAGRAMS.md`
- Complete login flow walkthrough
- Database schema diagrams
- Multi-company access examples
- Admin task flows

**Finally**: `IKEA_B2B_IMPLEMENTATION_CHECKLIST.md`
- 8-phase roadmap with checkbox
- Detailed 6-week timeline
- Resource requirements
- Success criteria

---

## Questions?

**Q: Do we need to change the existing OAuth2 flow?**
A: No. We're just adding a layer on top. Existing users unaffected.

**Q: What if a B2B user needs to access multiple companies' resources?**
A: Update their b2b_user_company_mapping record to show different company (or create new route for multi-company users).

**Q: Can B2B users authenticate directly with username/password instead of external IDP?**
A: Yes - that's already supported. They would just have no company context (treated as internal user).

**Q: What happens if a company's external IDP goes down?**
A: Users for that company can't log in. But existing logged-in users keep access. Can fall back to admin-issued temporary password.

**Q: How do we handle federated user ID collisions (same email at different companies)?**
A: Primary key is (federated_user_id, idp_registration_id, b2b_company_id) - so alice@acme.com (from Azure) is different from alice@acme.com (from another IDP).

---

## Summary

**IKEA B2B Implementation** enables a true multi-tenant system where:

✓ External companies register with IKEA
✓ Users authenticate via their company's IDP
✓ IKEA automatically scopes user access to company's resources
✓ Complete audit trail of all access
✓ Defense-in-depth security (5 layers)
✓ No breaking changes to existing system
✓ Minimal performance impact (with caching)
✓ 6-week implementation with 2-3 engineers

**Result**: IKEA becomes a partner platform where suppliers, logistics, and construction companies can collaborate securely within their own scope.

---

**Document**: IKEA B2B Federated IDP Implementation
**Version**: 1.0
**Date**: May 17, 2026
**Author**: Architecture Team


