# IKEA Multi-Tenant B2B IDP Implementation

> Updated model: Spring Auth + external IDPs authenticate users; OpenFGA performs dynamic authorization checks for roles and resource permissions.

## Use Case Overview

**Company**: IKEA (Primary Organization - Identity Provider)
**Scenario**: IKEA wants to register other B2B companies (Suppliers, Partners, Logistics) that have their own IDPs. Users from these external companies must:
- Authenticate using their company's IDP
- Access ONLY IKEA resources associated with their company
- Have role-based access control within their company's scope

### Example Structure

```
IKEA (Primary Platform)
├─ Supplier: "Acme Manufacturing" (has its own Azure AD)
│  └─ Users: alice@acme.com, bob@acme.com
│  └─ Can access: IKEA resources tagged for "Acme"
│
├─ Supplier: "ProLog Logistics" (has its own Google Workspace)
│  └─ Users: charlie@prolog.com, dave@prolog.com
│  └─ Can access: IKEA resources tagged for "ProLog"
│
└─ Partner: "BuildRight Construction" (has its own Okta)
   └─ Users: eve@buildright.com
   └─ Can access: IKEA resources tagged for "BuildRight"
```

---

## Current Architecture vs. Required Changes

### Current State

```
customer_idp_config
├─ tenant_id: "hotel_chain_1"
├─ registration_id: "google"
└─ External IDP creds

Authorization is done at:
└─ Token level (scopes, roles in JWT claims)
└─ No data-level tenant isolation
```

### Required for IKEA B2B

```
IKEA needs:
1. B2B Company Registration (with organization hierarchy)
2. Company ↔ External IDP mapping (knowing which Azure AD = which supplier)
3. Data-level Access Control (users can only see company's resources)
4. Resource Tagging System (mark IKEA resources with company associations)
5. Authorization Filter (token → company context → resource filtering)
```

---

## Implementation Architecture

### Layer 1: Database Schema Enhancements

Add new tables to support B2B company registration and resource association:

```sql
-- New table: B2B Companies registered in IKEA platform
CREATE TABLE b2b_company (
    id SERIAL PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    logo_url VARCHAR(500),
    company_key VARCHAR(100) NOT NULL UNIQUE,          -- "acme", "prolog", etc
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Link B2B companies to their external IDPs
CREATE TABLE b2b_company_idp_mapping (
    id SERIAL PRIMARY KEY,
    b2b_company_id INTEGER NOT NULL,
    customer_idp_config_id INTEGER NOT NULL,           -- Which IDP this company uses
    status VARCHAR(50) DEFAULT 'ACTIVE',               -- ACTIVE, SUSPENDED, etc
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (b2b_company_id) REFERENCES b2b_company(id),
    FOREIGN KEY (customer_idp_config_id) REFERENCES customer_idp_config(id),
    UNIQUE (b2b_company_id, customer_idp_config_id)
);

-- Resource association with B2B companies
CREATE TABLE b2b_resource_company_access (
    id SERIAL PRIMARY KEY,
    resource_id VARCHAR(255) NOT NULL,                 -- IKEA resource identifier
    resource_type VARCHAR(100) NOT NULL,               -- "order", "shipment", "inventory", etc
    b2b_company_id INTEGER NOT NULL,
    access_level VARCHAR(50) NOT NULL,                 -- "READ", "WRITE", "ADMIN"
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (b2b_company_id) REFERENCES b2b_company(id),
    UNIQUE (resource_id, resource_type, b2b_company_id)
);

-- Track which B2B company a user belongs to in federated ID token
CREATE TABLE b2b_user_company_mapping (
    id SERIAL PRIMARY KEY,
    federated_user_id VARCHAR(255) NOT NULL,           -- "sub" claim from external IDP
    federated_email VARCHAR(255) NOT NULL,             -- "email" claim from external IDP
    idp_registration_id VARCHAR(100) NOT NULL,         -- Which IDP issued the token
    b2b_company_id INTEGER NOT NULL,
    user_roles VARCHAR(500),                           -- "supplier_admin", "viewer", etc (company-level)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (b2b_company_id) REFERENCES b2b_company(id),
    UNIQUE (federated_user_id, idp_registration_id, b2b_company_id)
);

-- Audit log: Track which user accessed what resource
CREATE TABLE b2b_access_audit_log (
    id SERIAL PRIMARY KEY,
    b2b_company_id INTEGER NOT NULL,
    federated_user_id VARCHAR(255) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    action VARCHAR(50),                                -- "READ", "WRITE", "DELETE"
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (b2b_company_id) REFERENCES b2b_company(id)
);
```

---

## Implementation Steps

### Step 1: Registration Flow

**Admin (IKEA)** registers a new B2B company:

```bash
POST /api/b2b/company/register
{
  "company_name": "Acme Manufacturing",
  "company_key": "acme",
  "description": "Supplier of IKEA furniture components",
  "logo_url": "https://acme.example.com/logo.png"
}

Response:
{
  "id": 1,
  "company_name": "Acme Manufacturing",
  "company_key": "acme",
  "status": "PENDING"
}
```

### Step 2: IDP Linking

**Admin (IKEA)** links the B2B company to its external IDP:

```bash
POST /api/b2b/company/{id}/link-idp
{
  "idp_registration_id": "acme_azure"
}

Behind the scenes:
├─ Create entry in b2b_company_idp_mapping
├─ Link b2b_company_id=1 to customer_idp_config where registration_id="acme_azure"
└─ Now users authenticating via "acme_azure" IDP will be associated with company "Acme"
```

### Step 3: Resource Tagging

**IKEA System** associates internal resources with B2B companies:

```bash
POST /api/resources/{resource_id}/assign-company
{
  "resource_type": "order",
  "b2b_company_id": 1,
  "access_level": "READ"
}

Example:
├─ Order #12345 → readable by Acme Manufacturing (b2b_company_id=1)
├─ Order #12346 → readable by ProLog Logistics (b2b_company_id=2)
└─ Order #12347 → not assigned to any B2B company (internal only)
```

### Step 4: User Claim Extraction

**Spring Auth Server** extracts company context from federated token:

```java
// During federated login (Step 1: User authenticates at Acme's Azure AD)

TokenClaims from Acme's Azure AD:
{
  "oid": "acme-user-123",                    // ← user's ID in Acme's Azure
  "email": "alice@acme.com",
  "name": "Alice Smith"
}

Spring Auth Server logic:
1. Receives token with email="alice@acme.com"
2. Looks up: SELECT * FROM customer_idp_config 
             WHERE registration_id matches Azure IDP
3. Finds: registration_id="acme_azure", tenant_id="acme"
4. Queries: SELECT b2b_company_id FROM b2b_company_idp_mapping 
            WHERE customer_idp_config_id=<found_config>
5. Result: b2b_company_id=1 (Acme Manufacturing)
6. Stores in token claim: "b2b_company_id": "1", "company_key": "acme"

Issued JWT from IKEA Auth Server:
{
  "sub": "acme-user-123",
  "email": "alice@acme.com",
  "b2b_company_id": "1",
  "b2b_company_key": "acme",
  "b2b_user_roles": ["supplier_user"],
  "iat": 1234567890,
  "exp": 1234571490
}
```

---

## Code Implementation

### 1. New Entities

**File**: `src/main/java/com/openbanking/authentication/entities/B2bCompany.java`

```java
package com.openbanking.authentication.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "b2b_company")
public class B2bCompany {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "company_name", nullable = false, unique = true)
    private String companyName;
    
    @Column(name = "company_key", nullable = false, unique = true)
    private String companyKey;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "logo_url")
    private String logoUrl;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
```

**File**: `src/main/java/com/openbanking/authentication/entities/B2bUserCompanyMapping.java`

```java
package com.openbanking.authentication.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "b2b_user_company_mapping", 
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"federated_user_id", "idp_registration_id", "b2b_company_id"}
       ))
public class B2bUserCompanyMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "federated_user_id", nullable = false)
    private String federatedUserId;
    
    @Column(name = "federated_email", nullable = false)
    private String federatedEmail;
    
    @Column(name = "idp_registration_id", nullable = false)
    private String idpRegistrationId;
    
    @Column(name = "b2b_company_id")
    private Integer b2bCompanyId;
    
    @ManyToOne
    @JoinColumn(name = "b2b_company_id", insertable = false, updatable = false)
    private B2bCompany b2bCompany;
    
    @Column(name = "user_roles")
    private String userRoles;  // "supplier_admin,viewer"
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
```

**File**: `src/main/java/com/openbanking/authentication/entities/B2bResourceCompanyAccess.java`

```java
package com.openbanking.authentication.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "b2b_resource_company_access",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"resource_id", "resource_type", "b2b_company_id"}
       ))
public class B2bResourceCompanyAccess {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "resource_id", nullable = false)
    private String resourceId;
    
    @Column(name = "resource_type", nullable = false)
    private String resourceType;  // "order", "shipment", "inventory"
    
    @Column(name = "b2b_company_id")
    private Integer b2bCompanyId;
    
    @ManyToOne
    @JoinColumn(name = "b2b_company_id", insertable = false, updatable = false)
    private B2bCompany b2bCompany;
    
    @Column(name = "access_level", nullable = false)
    private String accessLevel;  // "READ", "WRITE", "ADMIN"
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
```

### 2. Repository Interfaces

**File**: `src/main/java/com/openbanking/authentication/repository/reader/ReaderB2bCompanyRepository.java`

```java
package com.openbanking.authentication.repository.reader;

import com.openbanking.authentication.entities.B2bCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ReaderB2bCompanyRepository extends JpaRepository<B2bCompany, Integer> {
    Optional<B2bCompany> findByCompanyKey(String companyKey);
    Optional<B2bCompany> findByCompanyName(String companyName);
}
```

**File**: `src/main/java/com/openbanking/authentication/repository/reader/ReaderB2bUserCompanyMappingRepository.java`

```java
package com.openbanking.authentication.repository.reader;

import com.openbanking.authentication.entities.B2bUserCompanyMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ReaderB2bUserCompanyMappingRepository extends JpaRepository<B2bUserCompanyMapping, Integer> {
    Optional<B2bUserCompanyMapping> findByFederatedUserIdAndIdpRegistrationId(
        String federatedUserId, 
        String idpRegistrationId
    );
    
    Optional<B2bUserCompanyMapping> findByFederatedEmailAndIdpRegistrationId(
        String federatedEmail,
        String idpRegistrationId
    );
}
```

**File**: `src/main/java/com/openbanking/authentication/repository/reader/ReaderB2bResourceCompanyAccessRepository.java`

```java
package com.openbanking.authentication.repository.reader;

import com.openbanking.authentication.entities.B2bResourceCompanyAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface ReaderB2bResourceCompanyAccessRepository extends JpaRepository<B2bResourceCompanyAccess, Integer> {
    Optional<B2bResourceCompanyAccess> findByResourceIdAndResourceTypeAndB2bCompanyId(
        String resourceId,
        String resourceType,
        Integer b2bCompanyId
    );
    
    List<B2bResourceCompanyAccess> findByB2bCompanyIdAndResourceType(
        Integer b2bCompanyId,
        String resourceType
    );
}
```

### 3. Key Service: Token Enhancement with B2B Context

**File**: `src/main/java/com/openbanking/authentication/services/B2bContextService.java`

```java
package com.openbanking.authentication.services;

import com.openbanking.authentication.entities.B2bCompany;
import com.openbanking.authentication.entities.B2bUserCompanyMapping;
import com.openbanking.authentication.entities.CustomerIdpConfig;
import com.openbanking.authentication.repository.reader.ReaderB2bCompanyRepository;
import com.openbanking.authentication.repository.reader.ReaderB2bUserCompanyMappingRepository;
import com.openbanking.authentication.repository.reader.ReaderCustomerIdpRepository;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Optional;

@Service
public class B2bContextService {
    
    private final ReaderB2bCompanyRepository b2bCompanyRepository;
    private final ReaderB2bUserCompanyMappingRepository userCompanyMappingRepository;
    private final ReaderCustomerIdpRepository customerIdpRepository;
    
    public B2bContextService(
            ReaderB2bCompanyRepository b2bCompanyRepository,
            ReaderB2bUserCompanyMappingRepository userCompanyMappingRepository,
            ReaderCustomerIdpRepository customerIdpRepository) {
        this.b2bCompanyRepository = b2bCompanyRepository;
        this.userCompanyMappingRepository = userCompanyMappingRepository;
        this.customerIdpRepository = customerIdpRepository;
    }
    
    /**
     * Given federated token claims, extract B2B company context
     * 
     * @param federatedUserIdClaim The "sub" claim from external IDP
     * @param federatedEmailClaim The "email" claim from external IDP
     * @param idpRegistrationId Which IDP issued the token (e.g., "acme_azure")
     * @return Map with b2b_company_id, b2b_company_key, b2b_user_roles
     */
    public Optional<Map<String, Object>> extractB2bContext(
            String federatedUserIdClaim,
            String federatedEmailClaim,
            String idpRegistrationId) {
        
        // Step 1: Find IDP configuration
        Optional<CustomerIdpConfig> idpConfig = 
            customerIdpRepository.findByRegistrationIdIgnoreCase(idpRegistrationId);
        
        if (idpConfig.isEmpty()) {
            return Optional.empty();
        }
        
        // Step 2: Query which B2B company uses this IDP
        // (This assumes a mapping exists in b2b_company_idp_mapping table)
        // For now, we use tenant_id from customer_idp_config as company key
        String companyKey = idpConfig.get().getTenantId();
        
        Optional<B2bCompany> company = b2bCompanyRepository.findByCompanyKey(companyKey);
        if (company.isEmpty()) {
            return Optional.empty();
        }
        
        // Step 3: Look up user's company mapping
        Optional<B2bUserCompanyMapping> userMapping = 
            userCompanyMappingRepository.findByFederatedUserIdAndIdpRegistrationId(
                federatedUserIdClaim,
                idpRegistrationId
            );
        
        if (userMapping.isEmpty()) {
            // User not yet registered - could trigger auto-provisioning or block
            return Optional.empty();
        }
        
        // Step 4: Build context map to add to token claims
        return Optional.of(Map.of(
            "b2b_company_id", userMapping.get().getB2bCompanyId().toString(),
            "b2b_company_key", company.get().getCompanyKey(),
            "b2b_company_name", company.get().getCompanyName(),
            "b2b_user_roles", userMapping.get().getUserRoles() != null 
                ? userMapping.get().getUserRoles() 
                : "viewer"
        ));
    }
}
```

### 4. Authorization Filter: Restrict Resource Access

**File**: `src/main/java/com/openbanking/authentication/services/B2bResourceAccessService.java`

```java
package com.openbanking.authentication.services;

import com.openbanking.authentication.entities.B2bResourceCompanyAccess;
import com.openbanking.authentication.repository.reader.ReaderB2bResourceCompanyAccessRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class B2bResourceAccessService {
    
    private final ReaderB2bResourceCompanyAccessRepository resourceAccessRepository;
    
    public B2bResourceAccessService(ReaderB2bResourceCompanyAccessRepository resourceAccessRepository) {
        this.resourceAccessRepository = resourceAccessRepository;
    }
    
    /**
     * Check if a user (via their B2B company context) can access a specific resource
     * 
     * @param b2bCompanyId The company ID from token claims
     * @param resourceId The IKEA resource ID (e.g., "order-12345")
     * @param resourceType The resource type (e.g., "order")
     * @param requiredAccessLevel Required access ("READ", "WRITE", "ADMIN")
     * @return true if user's company has access, false otherwise
     */
    public boolean canAccessResource(
            Integer b2bCompanyId,
            String resourceId,
            String resourceType,
            String requiredAccessLevel) {
        
        // Query: Does this company have access to this resource?
        Optional<B2bResourceCompanyAccess> access = 
            resourceAccessRepository.findByResourceIdAndResourceTypeAndB2bCompanyId(
                resourceId,
                resourceType,
                b2bCompanyId
            );
        
        if (access.isEmpty()) {
            return false;  // Company has no access to this resource
        }
        
        // Check access level (can READ > requires READ, can WRITE > requires READ/WRITE, etc)
        String companyAccessLevel = access.get().getAccessLevel();
        return hasRequiredAccessLevel(companyAccessLevel, requiredAccessLevel);
    }
    
    /**
     * Check if company's access level meets requirement
     */
    private boolean hasRequiredAccessLevel(String companyAccessLevel, String requiredLevel) {
        // Access hierarchy: ADMIN > WRITE > READ
        int companyLevel = getAccessLevelRank(companyAccessLevel);
        int requiredRank = getAccessLevelRank(requiredLevel);
        return companyLevel >= requiredRank;
    }
    
    private int getAccessLevelRank(String level) {
        return switch (level) {
            case "ADMIN" -> 3;
            case "WRITE" -> 2;
            case "READ" -> 1;
            default -> 0;
        };
    }
}
```

### 5. Token Claims Enhancement

**File**: `src/main/java/com/openbanking/authentication/config/AuthorizationServerConfig.java` (MODIFY)

In the token customizer, add B2B context to the JWT:

```java
// Inside OAuth2TokenCustomizer implementation, when issuing access token:

@Override
public void customize(JwtEncodingContext context) {
    if (context.getTokenType() == OAuth2TokenType.ACCESS_TOKEN) {
        
        // Existing claims...
        
        // NEW: Extract and add B2B context
        Authentication authentication = context.getPrincipal();
        String federatedUserId = authentication.getName();  // From external IDP
        
        SecurityContext securityContext = SecurityContextHolder.getContext();
        // Get OAuth2AuthenticatedPrincipal which has federated claims
        
        if (authentication instanceof OAuth2AuthenticatedPrincipal) {
            OAuth2AuthenticatedPrincipal principal = (OAuth2AuthenticatedPrincipal) authentication;
            Map<String, Object> attributes = principal.getAttributes();
            
            String email = (String) attributes.get("email");
            String registrationId = getRegistrationIdFromSession();  // Get from session context
            
            // Extract B2B context
            Optional<Map<String, Object>> b2bContext = 
                b2bContextService.extractB2bContext(federatedUserId, email, registrationId);
            
            if (b2bContext.isPresent()) {
                // Add to token claims
                b2bContext.get().forEach((key, value) -> 
                    context.getClaims().claim(key, value)
                );
            }
        }
    }
}
```

### 6. API Endpoint: Resource Access with B2B Filter

**File**: `src/main/java/com/openbanking/authentication/controller/B2bResourceController.java`

```java
package com.openbanking.authentication.controller;

import com.openbanking.authentication.services.B2bResourceAccessService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/resources")
public class B2bResourceController {
    
    @Autowired
    private B2bResourceAccessService resourceAccessService;
    
    @Autowired
    private ResourceService resourceService;
    
    @GetMapping("/{resourceId}")
    @PreAuthorize("hasAnyRole('ROLE_B2B_USER', 'ROLE_ADMIN')")
    @Operation(summary = "Get resource with B2B access control")
    public ResponseEntity<?> getResource(
            @PathVariable String resourceId,
            @RequestParam String resourceType) {
        
        // Extract B2B company ID from token claims
        Integer b2bCompanyId = getB2bCompanyIdFromToken();
        
        // Check if user's company can access this resource
        if (!resourceAccessService.canAccessResource(b2bCompanyId, resourceId, resourceType, "READ")) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "Forbidden",
                "message", "Your company does not have access to this resource"
            ));
        }
        
        // Resource is accessible to this company
        return ResponseEntity.ok(resourceService.getResource(resourceId));
    }
    
    @PutMapping("/{resourceId}")
    @PreAuthorize("hasAnyRole('ROLE_B2B_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Update resource with B2B access control")
    public ResponseEntity<?> updateResource(
            @PathVariable String resourceId,
            @RequestParam String resourceType,
            @RequestBody Map<String, Object> updates) {
        
        Integer b2bCompanyId = getB2bCompanyIdFromToken();
        
        // Check WRITE access
        if (!resourceAccessService.canAccessResource(b2bCompanyId, resourceId, resourceType, "WRITE")) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "Forbidden",
                "message", "Your company does not have write access to this resource"
            ));
        }
        
        return ResponseEntity.ok(resourceService.updateResource(resourceId, updates));
    }
    
    private Integer getB2bCompanyIdFromToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        OAuth2AuthenticatedPrincipal principal = (OAuth2AuthenticatedPrincipal) auth.getPrincipal();
        Object companyId = principal.getAttributes().get("b2b_company_id");
        return Integer.parseInt(companyId.toString());
    }
}
```

---

## Request Flow: IKEA B2B Federated Login

```
1. Acme User (alice@acme.com) visits IKEA portal
   ├─ Clicks "Sign In with Your Company"
   └─ Selects "Acme Manufacturing"

2. Frontend redirects to:
   POST /sso?sso_registration_id=acme_azure

3. Spring Auth Server:
   ├─ Queries customer_idp_config WHERE registration_id='acme_azure'
   ├─ Finds: tenant_id='acme', client_id=[Azure OAuth], endpoints=[Azure endpoints]
   └─ 302 Redirect to: https://login.microsoftonline.com/.../oauth2/authorize?...

4. User authenticates at Azure AD:
   ├─ Enters credentials
   └─ Azure returns: ID token with sub='acme-user-123', email='alice@acme.com'

5. Spring Auth Server receives auth code, exchanges for token:
   ├─ Decodes ID Token from Azure
   ├─ Extracts: sub='acme-user-123', email='alice@acme.com'
   └─ Calls: B2bContextService.extractB2bContext(...)

6. B2bContextService logic:
   ├─ Finds: registration_id='acme_azure' → tenant_id='acme'
   ├─ Queries: B2bCompany.findByCompanyKey('acme') → id=1
   ├─ Queries: B2bUserCompanyMapping.find(sub='acme-user-123', idp='acme_azure') 
   ├─ Finds: b2b_company_id=1, user_roles='supplier_user'
   └─ Returns: {b2b_company_id: '1', b2b_company_key: 'acme', b2b_user_roles: 'supplier_user'}

7. Spring issues JWT with following claims:
   {
     "sub": "acme-user-123",
     "email": "alice@acme.com",
     "b2b_company_id": "1",
     "b2b_company_key": "acme",
     "b2b_company_name": "Acme Manufacturing",
     "b2b_user_roles": "supplier_user",
     "aud": "acme_supplier_portal",
     "iat": 1234567890,
     "exp": 1234571490
   }

8. Token returned to IKEA SPA frontend

9. User tries to access order details:
   GET /api/resources/order-12345?resourceType=order
   Authorization: Bearer [JWT from step 7]

10. B2bResourceController:
    ├─ Extracts: b2b_company_id='1' from token
    ├─ Calls: canAccessResource(1, 'order-12345', 'order', 'READ')
    ├─ Queries: B2bResourceCompanyAccess.find(
    │   resourceId='order-12345',
    │   resourceType='order',
    │   b2bCompanyId=1
    │ )
    ├─ Finds: accessLevel='READ' (Acme has read access to this order)
    └─ Returns: 200 OK with order details

11. User tries to access order for ProLog (not their company):
    GET /api/resources/order-99999?resourceType=order
    (order-99999 is tagged for b2b_company_id=2, ProLog)
    
    B2bResourceController:
    ├─ Extracts: b2b_company_id='1' from token
    ├─ Calls: canAccessResource(1, 'order-99999', 'order', 'READ')
    ├─ Queries: B2bResourceCompanyAccess.find(
    │   resourceId='order-99999',
    │   resourceType='order',
    │   b2bCompanyId=1
    │ )
    ├─ Finds: Empty (no access)
    └─ Returns: 403 Forbidden
```

---

## Setup Scripts

### Flyway Migration V003: B2B Tables

**File**: `src/main/resources/db/migration/V003__add_b2b_company_tables.sql`

```sql
-- B2B Companies
CREATE TABLE b2b_company (
    id SERIAL PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL UNIQUE,
    company_key VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    logo_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Link B2B companies to external IDPs
CREATE TABLE b2b_company_idp_mapping (
    id SERIAL PRIMARY KEY,
    b2b_company_id INTEGER NOT NULL,
    customer_idp_config_id INTEGER NOT NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (b2b_company_id) REFERENCES b2b_company(id),
    FOREIGN KEY (customer_idp_config_id) REFERENCES customer_idp_config(id),
    UNIQUE (b2b_company_id, customer_idp_config_id)
);

-- User → Company mapping from federated tokens
CREATE TABLE b2b_user_company_mapping (
    id SERIAL PRIMARY KEY,
    federated_user_id VARCHAR(255) NOT NULL,
    federated_email VARCHAR(255) NOT NULL,
    idp_registration_id VARCHAR(100) NOT NULL,
    b2b_company_id INTEGER NOT NULL,
    user_roles VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (b2b_company_id) REFERENCES b2b_company(id),
    UNIQUE (federated_user_id, idp_registration_id, b2b_company_id)
);

-- Resource → Company access control
CREATE TABLE b2b_resource_company_access (
    id SERIAL PRIMARY KEY,
    resource_id VARCHAR(255) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    b2b_company_id INTEGER NOT NULL,
    access_level VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (b2b_company_id) REFERENCES b2b_company(id),
    UNIQUE (resource_id, resource_type, b2b_company_id)
);

-- Audit log
CREATE TABLE b2b_access_audit_log (
    id SERIAL PRIMARY KEY,
    b2b_company_id INTEGER NOT NULL,
    federated_user_id VARCHAR(255) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    action VARCHAR(50),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (b2b_company_id) REFERENCES b2b_company(id)
);

-- Insert example companies
INSERT INTO b2b_company (company_name, company_key, description)
VALUES 
  ('Acme Manufacturing', 'acme', 'Supplier of furniture components'),
  ('ProLog Logistics', 'prolog', 'Logistics and distribution partner'),
  ('BuildRight Construction', 'buildright', 'Construction and assembly partner');
```

### Bruno API Requests

**File**: `bruno/ob-authentication-service/B2B/01 Register B2B Company.bru`

```
meta {
  name: 01 Register B2B Company
  type: http
  seq: 1
}

post {
  url: {{baseUrl}}/api/b2b/company/register
  body: json
  auth: bearer
}

auth:bearer {
  token: {{accessToken}}
}

headers {
  Accept: application/json
  Content-Type: application/json
}

body:json {
  {
    "company_name": "Acme Manufacturing",
    "company_key": "acme",
    "description": "Supplier of IKEA furniture components",
    "logo_url": "https://acme.example.com/logo.png"
  }
}
```

**File**: `bruno/ob-authentication-service/B2B/02 Link IDP to Company.bru`

```
meta {
  name: 02 Link IDP to Company
  type: http
  seq: 2
}

post {
  url: {{baseUrl}}/api/b2b/company/1/link-idp
  body: json
  auth: bearer
}

auth:bearer {
  token: {{accessToken}}
}

headers {
  Accept: application/json
  Content-Type: application/json
}

body:json {
  {
    "idp_registration_id": "acme_azure",
    "status": "ACTIVE"
  }
}
```

**File**: `bruno/ob-authentication-service/B2B/03 Assign Resource to Company.bru`

```
meta {
  name: 03 Assign Resource to Company
  type: http
  seq: 3
}

post {
  url: {{baseUrl}}/api/resources/order-12345/assign-company
  body: json
  auth: bearer
}

auth:bearer {
  token: {{accessToken}}
}

headers {
  Accept: application/json
  Content-Type: application/json
}

body:json {
  {
    "resource_type": "order",
    "b2b_company_id": 1,
    "access_level": "READ"
  }
}
```

---

## Security Considerations

### 1. **Token Validation**
- Always verify JWT signature using IKEA Auth Server's public key
- Validate `aud` (audience) claim matches expected service
- Check `exp` (expiration) hasn't passed

### 2. **Company Context Verification**
- Never trust B2B company ID from user input
- Always extract from JWT claims issued by Auth Server
- Validate company exists and is ACTIVE

### 3. **Resource Access Audit**
- Log every resource access attempt (especially failures)
- Monitor for anomalies (user accessing unusual resource types)

### 4. **IDP Credential Security**
- Store external IDP credentials encrypted in DB
- Rotate credentials periodically
- Never expose credentials in logs or API responses

### 5. **User Provisioning**
- Use SCIM protocol to sync users from external IDPs
- Or manually provision users via admin API
- Track provisioning date for audit purposes

### 6. **Access Level Hierarchy**
```
ADMIN (highest)
  ├─ Delete/Edit all resources
  ├─ Manage company users
  └─ View all data

WRITE (medium)
  ├─ Create/Edit assigned resources
  └─ View all data

READ (lowest)
  └─ View only
```

---

## Example: Complete Setup for Acme Manufacturing

### 1. Admin registers Acme

```bash
curl -X POST https://ikea-auth.example.com/api/b2b/company/register \
  -H "Authorization: Bearer [IKEA_ADMIN_TOKEN]" \
  -H "Content-Type: application/json" \
  -d '{
    "company_name": "Acme Manufacturing",
    "company_key": "acme",
    "description": "Supplier of furniture parts"
  }'

Response: { "id": 1, "company_key": "acme" }
```

### 2. Admin registers Acme's Azure AD as external IDP

```bash
curl -X POST https://ikea-auth.example.com/api/clients/idp \
  -H "Authorization: Bearer [IKEA_ADMIN_TOKEN]" \
  -H "Content-Type: application/json" \
  -d '{
    "tenant_id": "acme",
    "registration_id": "acme_azure",
    "client_id": "acme-azure-client-id",
    "client_secret": "[ENCRYPTED]",
    "issuer_uri": "https://login.microsoftonline.com/acme-tenant-id/v2.0",
    "authorization_uri": "https://login.microsoftonline.com/acme-tenant-id/oauth2/v2.0/authorize",
    "token_uri": "https://login.microsoftonline.com/acme-tenant-id/oauth2/v2.0/token",
    "jwk_set_uri": "https://login.microsoftonline.com/acme-tenant-id/discovery/v2.0/keys",
    "user_info_uri": "https://graph.microsoft.com/v1.0/me",
    "user_name_attribute": "email",
    "scope": "openid profile email"
  }'
```

### 3. Admin links company to IDP

```bash
curl -X POST https://ikea-auth.example.com/api/b2b/company/1/link-idp \
  -H "Authorization: Bearer [IKEA_ADMIN_TOKEN]" \
  -H "Content-Type: application/json" \
  -d '{
    "idp_registration_id": "acme_azure",
    "status": "ACTIVE"
  }'
```

### 4. Admin provisions Acme users

```bash
curl -X POST https://ikea-auth.example.com/api/b2b/users/provision \
  -H "Authorization: Bearer [IKEA_ADMIN_TOKEN]" \
  -H "Content-Type: application/json" \
  -d '{
    "b2b_company_id": 1,
    "users": [
      {
        "federated_user_id": "acme-user-123",
        "federated_email": "alice@acme.com",
        "idp_registration_id": "acme_azure",
        "user_roles": "supplier_admin"
      },
      {
        "federated_user_id": "acme-user-456",
        "federated_email": "bob@acme.com",
        "idp_registration_id": "acme_azure",
        "user_roles": "supplier_user"
      }
    ]
  }'
```

### 5. Admin assigns resources to Acme

```bash
curl -X POST https://ikea-auth.example.com/api/resources/order-12345/assign-company \
  -H "Authorization: Bearer [IKEA_ADMIN_TOKEN]" \
  -H "Content-Type: application/json" \
  -d '{
    "resource_type": "order",
    "b2b_company_id": 1,
    "access_level": "READ"
  }'

curl -X POST https://ikea-auth.example.com/api/resources/inventory-5678/assign-company \
  -H "Authorization: Bearer [IKEA_ADMIN_TOKEN]" \
  -H "Content-Type: application/json" \
  -d '{
    "resource_type": "inventory",
    "b2b_company_id": 1,
    "access_level": "READ"
  }'
```

### 6. Alice (user at Acme) logs in

```
1. Alice visits: https://ikea-portal.example.com
2. Clicks "Sign In"
3. Selects "Acme Manufacturing"
4. Redirected to: https://ikea-auth.example.com/login/oauth2/authorization?registration_id=acme_azure
5. Spring redirects to: https://login.microsoftonline.com/.../authorize?client_id=...
6. Alice logs in with alice@acme.com
7. Azure redirects back with auth code
8. Spring exchanges code for ID token
9. B2bContextService extracts:
   - registration_id=acme_azure → tenant_id=acme → company_key=acme
   - sub=acme-user-123 + idp=acme_azure → b2b_company_id=1
   - user_roles=supplier_admin
10. JWT issued with:
    {
      "sub": "acme-user-123",
      "email": "alice@acme.com",
      "b2b_company_id": "1",
      "b2b_company_key": "acme",
      "b2b_user_roles": "supplier_admin"
    }
11. Alice can now access:
    - GET /api/resources/order-12345 ✓ (assigned to company 1)
    - PUT /api/resources/order-12345 ✓ (has WRITE access)
    - GET /api/resources/order-99999 ✗ (assigned to different company)
```

---

## Next Steps / Enhancements

1. **User Self-Service Portal**
   - B2B users can manage their own roles/permissions
   - Request access to resources
   - Audit their own access logs

2. **Automated Resource Tagging**
   - Tag resources based on business logic (e.g., all orders for Acme supplier)
   - Bulk assignment APIs

3. **API Rate Limiting per Company**
   - Different rate limits for different B2B companies
   - Prevent one company from consuming all resources

4. **SCIM Integration**
   - Auto-sync users from external IDPs
   - Automatic provisioning/deprovisioning

5. **GraphQL API**
   - Allow data filtering by company context client-side
   - Reduce over-fetching

6. **Audit Dashboard**
   - View access logs per company
   - Identify suspicious activity
   - Export compliance reports

7. **Single Sign-Out**
   - When user logs out, revoke all tokens for that company
   - Cascade logout to external IDP

8. **Conditional Access**
   - Require MFA for sensitive resources
   - Geo-blocking (e.g., only allow login from Acme's office IPs)
   - Device compliance checks

---

## Summary

**IKEA B2B Model** = **Tenant + IDP + Resource-Level Access Control**

```
┌─────────────────────────────────────┐
│ IKEA (Primary Organization)         │
├─────────────────────────────────────┤
│                                     │
│  B2B Companies:                     │
│  ├─ Acme (Azure AD)  ─────→ Users   │
│  ├─ ProLog (Google)  ─────→ Users   │
│  └─ BuildRight (Okta) ─────→ Users  │
│                                     │
│  Internal Resources:                │
│  ├─ Order #12345 → [Acme only]      │
│  ├─ Inventory #5678 → [ProLog+Acme] │
│  └─ Shipment #999 → [All B2B]       │
│                                     │
│  Access Control:                    │
│  ├─ At token level (which company)  │
│  ├─ At API level (resource tagging) │
│  └─ At data level (query filtering) │
└─────────────────────────────────────┘
```

Each external company's users can only access IKEA resources designated for their company.


