# IKEA B2B Implementation Checklist

> Authorization baseline: keep authentication in Spring Auth/IDPs and move runtime role/permission checks to OpenFGA.

## Phase 0: OpenFGA Foundation (Week 0-1)

- [ ] Stand up OpenFGA store per environment (dev, qa, prod)
- [ ] Define and version the OpenFGA model (`company`, `resource`, `member`, `admin`, etc.)
- [ ] Add bootstrap migration/job to write initial tuples for known companies/resources
- [ ] Add `OpenFgaAuthorizationService` adapter in Spring APIs
- [ ] Replace static endpoint-role assumptions with OpenFGA `check`/`list-objects` paths for business resources

## Phase 1: Database Schema & Core Entities (Week 1-2)

### Database
- [ ] Create Flyway migration `V003__add_b2b_company_tables.sql`
  - [ ] `b2b_company` table
  - [ ] `b2b_company_idp_mapping` table
  - [ ] `b2b_user_company_mapping` table
  - [ ] `b2b_resource_company_access` table
  - [ ] `b2b_access_audit_log` table
  - [ ] Create indexes on foreign keys and frequently queried columns

### Java Entities
- [ ] Create `B2bCompany.java` entity
- [ ] Create `B2bUserCompanyMapping.java` entity
- [ ] Create `B2bResourceCompanyAccess.java` entity
- [ ] Add JPA annotations and relationship mappings
- [ ] Add @PrePersist/@PreUpdate lifecycle methods

### Repository Interfaces (Reader/Writer split)
- [ ] Create `ReaderB2bCompanyRepository`
- [ ] Create `WriterB2bCompanyRepository`
- [ ] Create `ReaderB2bUserCompanyMappingRepository`
- [ ] Create `WriterB2bUserCompanyMappingRepository`
- [ ] Create `ReaderB2bResourceCompanyAccessRepository`
- [ ] Create `WriterB2bResourceCompanyAccessRepository`
- [ ] Add custom query methods as needed

### Common Service Updates
- [ ] Modify `CommonService` to handle new entity conversions if needed

---

## Phase 2: Core Service Layer (Week 2-3)

### B2B Context Service
- [ ] Create `B2bContextService.java`
- [ ] Implement `extractB2bContext()` method
  - [ ] Extract registration_id from token
  - [ ] Lookup customer_idp_config to get tenant
  - [ ] Lookup b2b_company by company_key
  - [ ] Lookup b2b_user_company_mapping
  - [ ] Return map with b2b_company_id, b2b_company_key, b2b_user_roles

### B2B Resource Access Service
- [ ] Create `B2bResourceAccessService.java`
- [ ] Implement `canAccessResource()` method
  - [ ] Query b2b_resource_company_access
  - [ ] Check access_level hierarchy
  - [ ] Return boolean access granted/denied

### B2B Company Service (CRUD Operations)
- [ ] Create `B2bCompanyService.java`
- [ ] Implement `registerCompany()` method
- [ ] Implement `linkCompanyToIdp()` method
- [ ] Implement `provisionUsers()` method
- [ ] Implement `assignResourceToCompany()` method
- [ ] Implement `getAllCompanies()` pagination method
- [ ] Add caching via Redis

### Audit Service
- [ ] Create `B2bAuditService.java`
- [ ] Implement `logResourceAccess()` method
- [ ] Implement `getAuditLog()` pagination method

---

## Phase 3: Token Customization (Week 3)

### Authorization Server Config Update
- [ ] Modify `AuthorizationServerConfig.java`
- [ ] Update OAuth2TokenCustomizer
  - [ ] After federated authentication, call `B2bContextService.extractB2bContext()`
  - [ ] Add B2B claims to JWT:
    - [ ] `b2b_company_id`
    - [ ] `b2b_company_key`
    - [ ] `b2b_company_name`
    - [ ] `b2b_user_roles`
  - [ ] Handle case where B2B context not found (block or allow?)

### Token Claims Validation
- [ ] Add validation to ensure B2B claims are present for B2B users
- [ ] Add optional claims for internal-only users

---

## Phase 4: API Controllers & Endpoints (Week 4)

### B2B Company Admin Controller
- [ ] Create `B2bCompanyAdminController.java`
- [ ] Implement `POST /api/b2b/company/register` (create company)
- [ ] Implement `PUT /api/b2b/company/{id}` (update company)
- [ ] Implement `DELETE /api/b2b/company/{id}` (delete/deactivate)
- [ ] Implement `GET /api/b2b/company/{id}` (get company details)
- [ ] Implement `GET /api/b2b/companies` (list all companies, paginated)
- [ ] Implement `POST /api/b2b/company/{id}/link-idp` (associate IDP)
- [ ] Implement `DELETE /api/b2b/company/{id}/link-idp/{idpId}` (unlink IDP)

### B2B User Management Controller
- [ ] Create `B2bUserManagementController.java`
- [ ] Implement `POST /api/b2b/users/provision` (bulk user provisioning)
- [ ] Implement `PUT /api/b2b/users/{userId}` (update user roles)
- [ ] Implement `DELETE /api/b2b/users/{userId}` (deprovision user)
- [ ] Implement `GET /api/b2b/company/{companyId}/users` (list company users)

### B2B Resource Access Controller
- [ ] Create `B2bResourceAccessController.java`
- [ ] Implement `POST /api/resources/{resourceId}/assign-company` (tag resource)
- [ ] Implement `PUT /api/resources/{resourceId}/assign-company` (update access level)
- [ ] Implement `DELETE /api/resources/{resourceId}/company/{companyId}` (remove access)
- [ ] Implement `GET /api/resources/{resourceId}/companies` (list companies with access)
- [ ] Implement `GET /api/b2b/company/{companyId}/resources` (list company resources)

### B2B Resource Portal Controller (User-Facing)
- [ ] Create `B2bResourceController.java`
- [ ] Implement `GET /api/resources/{resourceId}` (with B2B access control)
  - [ ] Verify: extract b2b_company_id from JWT
  - [ ] Verify: b2b_company_id has READ access to resource
  - [ ] Return: 200 OK with filtered data or 403 Forbidden
- [ ] Implement `PUT /api/resources/{resourceId}` (with B2B access control)
  - [ ] Verify: WRITE access required
- [ ] Implement `POST /api/resources` (create, with company context)
- [ ] Implement `DELETE /api/resources/{resourceId}` (with B2B access control)

### B2B Audit Controller
- [ ] Create `B2bAuditController.java`
- [ ] Implement `GET /api/b2b/audit/log` (list all access logs, admin only)
- [ ] Implement `GET /api/b2b/company/{companyId}/audit/log` (company-specific logs)
- [ ] Implement `GET /api/b2b/audit/suspicious` (anomaly detection)

### Security Configuration
- [ ] Update `SecurityConfig.java` to add new endpoints
- [ ] Add role-based access control:
  - [ ] `ROLE_IKEA_ADMIN` → can access all admin endpoints
  - [ ] `ROLE_B2B_ADMIN` → can access company-specific admin endpoints (own company)
  - [ ] `ROLE_B2B_USER` → can access resource portal
- [ ] Add CORS if needed for B2B partner frontend domains

---

## Phase 5: Testing (Week 4-5)

### Unit Tests
- [ ] `B2bContextServiceTest`
  - [ ] Test: extract B2B context with valid registration_id
  - [ ] Test: extract B2B context with invalid registration_id
  - [ ] Test: extract B2B context with user not mapped to company
  - [ ] Test: extract B2B context with missing IDP config

- [ ] `B2bResourceAccessServiceTest`
  - [ ] Test: access allowed with matching company_id
  - [ ] Test: access denied with different company_id
  - [ ] Test: access level hierarchy (ADMIN > WRITE > READ)
  - [ ] Test: resource not assigned to any company

- [ ] `B2bCompanyServiceTest`
  - [ ] Test: register new company
  - [ ] Test: duplicate company_key rejected
  - [ ] Test: link company to IDP
  - [ ] Test: update company details

### Integration Tests
- [ ] `B2bLoginFlowIntegrationTest`
  - [ ] Simulate user login via Acme's Azure AD
  - [ ] Verify JWT contains b2b_company_id
  - [ ] Verify JWT signature is valid

- [ ] `B2bResourceAccessIntegrationTest`
  - [ ] User from Acme company accesses Acme resource → 200 OK
  - [ ] User from Acme company accesses ProLog resource → 403 Forbidden
  - [ ] User with READ access tries PUT → 403 Forbidden
  - [ ] Admin with ADMIN access tries PUT → 200 OK

- [ ] `B2bAuditIntegrationTest`
  - [ ] Access attempt logged to audit table
  - [ ] Query audit log filters by company

### Bruno API Tests
- [ ] Create Bruno collection: `B2B Operations`
  - [ ] `/POST /api/b2b/company/register` - Register Acme
  - [ ] `/POST /api/b2b/company/1/link-idp` - Link Azure AD
  - [ ] `/POST /api/b2b/users/provision` - Provision Alice user
  - [ ] `/POST /api/resources/order-12345/assign-company` - Tag order for Acme
  - [ ] `/GET /api/resources/order-12345` (as Alice) - Should succeed
  - [ ] `/GET /api/resources/order-99999` (as Alice) - Should fail 403
  - [ ] `/GET /api/b2b/audit/log` (as admin) - View all access

### Manual Testing
- [ ] Test end-to-end login flow with real Azure AD instance
- [ ] Test with multiple companies logging in simultaneously
- [ ] Test data isolation by inspecting raw database queries
- [ ] Test audit trail captures all access attempts
- [ ] Test role-based endpoint access with different user roles

---

## Phase 6: Flyway Migration & Seed Data (Week 5)

### Create Seed Data Migration
- [ ] Create `V004__seed_b2b_companies_and_configs.sql`
  - [ ] Insert test companies (Acme, ProLog, BuildRight)
  - [ ] Insert test IDPs (Azure, Google, Okta registrations)
  - [ ] Link companies to IDPs
  - [ ] Provision test users
  - [ ] Tag sample resources

---

## Phase 7: Documentation & Deployment (Week 5-6)

### Technical Documentation
- [ ] Update README with B2B setup instructions
- [ ] Create B2B Admin Guide
  - [ ] How to register a new company
  - [ ] How to link external IDP
  - [ ] How to provision users
  - [ ] How to tag resources
  - [ ] How to monitor access logs

- [ ] Create B2B Developer Guide
  - [ ] How to call B2B resource endpoints
  - [ ] How to extract company context from JWT
  - [ ] How to implement resource filtering

- [ ] Create B2B Security Guidelines
  - [ ] Token validation checklist
  - [ ] Access control best practices
  - [ ] Audit log retention policy

### API Documentation (Swagger/OpenAPI)
- [ ] Add Swagger annotations to all B2B endpoints
- [ ] Document request/response schemas for new DTOs
- [ ] Document error responses (403, 401, 404)

### Deployment Checklist
- [ ] Run Flyway migrations in staging environment
- [ ] Verify all new tables created successfully
- [ ] Test B2B endpoints in staging
- [ ] Load testing: verify performance at scale
- [ ] Security audit: review token handling, SQL injection, etc.
- [ ] Deploy to production
- [ ] Monitor application logs for errors
- [ ] Verify audit table is capturing events

---

## Phase 8: Post-Launch Monitoring (Ongoing)

### Monitoring & Alerting
- [ ] Setup monitoring for B2B resource access latency
- [ ] Alert on spike in 403 Forbidden responses (potential attack)
- [ ] Alert on audit log size (rapid growth might indicate abuse)
- [ ] Setup dashboard: companies registered, users provisioned, resource access patterns

### Maintenance
- [ ] Regular audit log archival (move old logs to cold storage)
- [ ] Periodic review of user access permissions
- [ ] Deactivate suspended companies' resources
- [ ] Update IDP credentials before expiration

---

## Optional Enhancements (Phase 2+)

### User Self-Service Portal
- [ ] Create UI for B2B users to view their permissions
- [ ] Add ability to request access to resources
- [ ] Email notifications on access grant/deny

### Automated User Sync (SCIM)
- [ ] Implement SCIM server endpoint
- [ ] Auto-provision/deprovision users from external IDPs
- [ ] Sync user attributes (name, email, roles)

### GraphQL API
- [ ] Add GraphQL implementation for B2B resources
- [ ] Allow filtering by company_id client-side
- [ ] Reduce over-fetching

### Advanced Access Control
- [ ] Time-based access (access only during business hours)
- [ ] Geo-blocking (restrict by IP range)
- [ ] Conditional MFA (require MFA for sensitive operations)
- [ ] Dynamic policies (attribute-based access control)

### Analytics
- [ ] Dashboard: Usage per company
- [ ] Dashboard: Resource access trends
- [ ] Export reports for compliance

---

## Success Criteria

### Phase 1-3 Complete
- [ ] ✓ Database schema supports multi-company resource access
- [ ] ✓ JWT tokens include b2b_company_id claim
- [ ] ✓ B2B context extracted correctly from federated tokens

### Phase 4 Complete
- [ ] ✓ Admin can register B2B company
- [ ] ✓ Admin can link company to external IDP
- [ ] ✓ Admin can provision users and assign resources
- [ ] ✓ B2B user can access their company's resources only
- [ ] ✓ B2B user cannot access other company's resources (403)

### Phase 5 Complete
- [ ] ✓ All unit tests pass
- [ ] ✓ All integration tests pass
- [ ] ✓ End-to-end login flow works with real IDP
- [ ] ✓ Data isolation verified at database level

### Phase 6-7 Complete
- [ ] ✓ Production database migration successful
- [ ] ✓ Seed data loaded
- [ ] ✓ Documentation complete and reviewed
- [ ] ✓ Team trained on admin operations

### Phase 8 Complete
- [ ] ✓ Zero unauthorized data access in first month of production
- [ ] ✓ All access logged and auditable
- [ ] ✓ Performance meets SLA (P99 latency < 200ms)
- [ ] ✓ No security incidents related to unauthorized access

---

## Risk Mitigation

### Risk: Token Tampering
**Mitigation**: Always verify JWT signature using IKEA's public key; never trust claims from user input

### Risk: SQL Injection
**Mitigation**: Use Spring Data JPA (parameterized queries); avoid string concatenation in queries

### Risk: Data Leakage
**Mitigation**: Implement defense-in-depth (token level + API level + database level filtering)

### Risk: Privilege Escalation
**Mitigation**: Validate user roles in B2B context; implement role hierarchy checks

### Risk: Audit Log Tampering
**Mitigation**: Append-only audit table; write audit logs to immutable storage (e.g., AWS S3)

### Risk: IDP Credential Compromise
**Mitigation**: Store secrets encrypted; rotate credentials regularly; monitor failed auth attempts

### Risk: Performance Degradation
**Mitigation**: Cache company metadata in Redis; index database columns; test at scale

---

## Success Measurement

**Metrics to Track**:
- Number of B2B companies registered
- Number of B2B users provisioned
- Resource access success rate (% of 200s vs 403s)
- Average response time for resource endpoints
- Audit log volume
- Zero unauthorized access incidents
- User satisfaction survey (from B2B partners)

**Goals**:
- Launch with 3+ B2B companies by end of Q2
- < 1% unauthorized access attempts
- P99 resource access latency < 200ms
- 100% audit trail accuracy
- 99.9% uptime for B2B APIs

---

## Timeline Summary

```
Week 1-2: Database schema + Java entities
Week 2-3: Service layer (B2B context, access control)
Week 3:   Token customization
Week 4:   API controllers + testing
Week 5:   Migrations + documentation
Week 5-6: Deployment prep
Week 6+:  Production monitoring & maintenance
```

**Total Effort**: 6 weeks (2-3 senior engineers)

**Deployment Target**: End of Q2 2024 (if starting early Q2)


