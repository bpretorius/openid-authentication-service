# Code Implementation Reference: Tenant, Registration ID, and Client

This document maps the architectural concepts to actual code locations in the codebase.

> Authorization update: authentication remains in Spring Auth and federated IDPs; roles and permissions should be resolved by OpenFGA at request time. See `OPENFGA_AUTHORIZATION_MODEL.md`.

---

## Registration ID → External IDP Provider Lookup

### Concept
When a user selects an IDP on the login form and submits `/sso?sso_registration_id=google`, the system looks up the Google IDP configuration from the database.

### Code Location: `DynamicIDPRegistrationRepository`

**File**: `src/main/java/com/openbanking/authentication/services/DynamicIDPRegistrationRepository.java`

```java
@Override
public ClientRegistration findByRegistrationId(String registrationId) {
    // ✓ registrationId = "google" (or "azure", etc.)
    // ✓ This queries customer_idp_config table
    
    CustomerIdpConfig config = readerCustomerIdpRepository
        .findByRegistrationIdIgnoreCase(registrationId)
        .orElseThrow(() -> new BadRequestException(ORGANIZATION_NOT_FOUND.toString(), ""));
    
    // ✓ config now contains:
    //   - tenantId: "hotel_chain_1"
    //   - client_id: "google.apps.googleusercontent.com"
    //   - client_secret: "[encrypted]"
    //   - issuer_uri, authorization_uri, etc.
    
    return ClientRegistration.withRegistrationId(config.getRegistrationId())
        .clientName(config.getRegistrationId())
        .clientId(config.getClientId())                // <- External provider's OAuth client ID
        .clientSecret(config.getClientSecret())        // <- External provider's OAuth secret
        .issuerUri(config.getIssuerUri())
        .authorizationUri(config.getAuthorizationUri())
        .tokenUri(config.getTokenUri())
        .userInfoUri(config.getUserInfoUri())
        .jwkSetUri(config.getJwkSetUri())
        .redirectUri(
            appConfig.getDefaultAuthCodeUrl() + registrationId  // <- "http://localhost:8081/login/oauth2/code/google"
        )
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .scope("openid", "profile", "email")
        .userNameAttributeName(config.getUserNameAttribute())
        .build();
}
```

**Key Points**:
- `registrationId` = provider name (e.g., "google", "azure")
- Queries `customer_idp_config` table
- Returns `ClientRegistration` for Spring Security OAuth2Client
- `config.getClientId()` is the **EXTERNAL** provider's OAuth client ID (not this app's)
- `config.getClientSecret()` is **EXTERNAL** provider's secret (encrypted in DB)

**Related Code**:
- Implementation class: `ReaderCustomerIdpRepository` (Spring Data JPA)
- Entity: `CustomerIdpConfig` - Maps to `customer_idp_config` table

---

## Internal Client Registration & Lookup

### Concept
When creating an OAuth2 application (SPA, backend, etc.), the system stores it in the `client` table.

### Code Location: `ClientController` + `ClientService`

**File**: `src/main/java/com/openbanking/authentication/controller/ClientController.java`

```java
@PostMapping
@Operation(summary = "Register a new user")
public ResponseEntity<ClientResponse> create(@RequestBody @Valid ClientRequest request) {
    // ✓ Receives JSON request:
    // {
    //   "provider": "internal",
    //   "clientId": "demo-client",
    //   "clientSecret": "demo-secret",
    //   "clientName": "Demo Client",
    //   "redirectUris": ["http://localhost:3000/callback"],
    //   ...
    // }
    
    clientService.save(request, null);
    return ResponseEntity.noContent().build();
}
```

**Service Implementation**: `src/main/java/com/openbanking/authentication/services/ClientService.java`

```java
public Client save(ClientRequest dto, String id) {
    // ✓ Build RegisteredClient from DTO
    
    RegisteredClient registeredClient = RegisteredClient
        .withId(UUID.randomUUID().toString())
        .id(StringUtils.isNotBlank(id) ? id : UUID.randomUUID().toString())
        .clientId(dto.clientId())                                    // <- "demo-client"
        .clientSecret(
            StringUtils.isNotBlank(dto.clientSecret()) 
                ? passwordEncoder.encode(dto.clientSecret())        // <- Bcrypt encoded
                : null
        )
        .clientName(dto.clientName())                              // <- "Demo Client"
        .clientAuthenticationMethods(methods -> 
            dto.authenticationMethods()
                .forEach(m -> methods.add(new ClientAuthenticationMethod(m)))
        )
        .authorizationGrantTypes(grants -> 
            dto.grantTypes()
                .forEach(g -> grants.add(new AuthorizationGrantType(g)))
        )
        .scopes(scopes -> scopes.addAll(dto.scopes()))             // <- "read:hotel", "create:hotel"
        .redirectUris(redirectUris -> {
            if (dto.redirectUris() != null) {
                redirectUris.addAll(dto.redirectUris());           // <- "http://localhost:3000/callback"
            }
        })
        .postLogoutRedirectUris(postLogoutRedirectUris -> {
            if (dto.postLogoutRedirectUris() != null) {
                postLogoutRedirectUris.addAll(dto.postLogoutRedirectUris());
            }
        })
        .clientIdIssuedAt(Instant.now())
        .tokenSettings(TokenSettings.builder()
            .accessTokenTimeToLive(Duration.ofMinutes(appConfig.getAccessTokenTTL()))
            .refreshTokenTimeToLive(Duration.ofDays(appConfig.getRefreshTokenTTL()))
            .reuseRefreshTokens(true)
            .build())
        .clientSettings(ClientSettings.builder()
            .requireAuthorizationConsent(dto.requireAuthorizationConsent())
            .requireProofKey(dto.requireProofKey())
            .setting("fullName", dto.clientName())
            .setting("mfaEnabled", dto.mfaEnabled())
            .setting("resource_indicator", dto.aud() != null ? String.join(",", dto.aud()) : "")
            .setting("roles", dto.roles() != null ? String.join(",", dto.roles()) : "")
            .setting("provider", dto.provider() != null ? dto.provider() : "internal")
            .build())
        .build();

    return this.save(registeredClient);
}

private Client save(RegisteredClient registeredClient) {
    // ✓ Convert Spring RegisteredClient to JPA Entity
    Client client = commonService.toEntity(registeredClient, objectMapper);
    return this.save(client);
}

public Client save(Client client) {
    // ✓ Persist to database
    client = this.writerClientRepository.save(client);
    reloadCache(client);  // <- Cache in Redis
    return client;
}
```

**Note**: NO `tenant_id` or `registration_id` fields in this request!
- This client is **NOT** tenant-specific (shared across all tenants)
- It does **NOT** know which external IDPs to use (that's in `customer_idp_config`)

---

## SSO Flow: User Selects External IDP

### Concept
When user clicks "Sign in with Google" and submits the SSO form, the system routes to the external IDP.

### Code Location: `LoginController`

**File**: `src/main/java/com/openbanking/authentication/controller/LoginController.java`

```java
@PostMapping("/sso")
public String postSSO(
    Model model, 
    HttpServletRequest request, 
    HttpServletResponse response, 
    @CurrentSecurityContext SecurityContext context
) {
    // ✓ Get registration_id from form: "google" or "azure"
    String registrationId = request.getParameter("sso_registration_id");
    
    if (StringUtils.isBlank(registrationId)) {
        model.addAttribute("errorMessage", 
            this.messageSource.getMessage(ORGANIZATION_EMPTY.toString(), null, Locale.getDefault()));
        return "sso";
    }
    
    try {
        // ✓ Look up the IDP configuration
        // Internally calls DynamicIDPRegistrationRepository.findByRegistrationId(registrationId)
        ClientRegistration clientRegistration = dynamicIDPRegistrationRepository
            .findByRegistrationId(registrationId);
        
        if (clientRegistration == null) {
            model.addAttribute("errorMessage", 
                this.messageSource.getMessage(ORGANIZATION_NOT_FOUND.toString(), null, Locale.getDefault()));
            return "sso";
        }
        
        // ✓ Redirect to Spring's OAuth2 authorization endpoint
        // This triggers the external IDP redirect
        return "redirect:/oauth2/authorization?registration_id=" + clientRegistration.getRegistrationId();
    } catch (BadRequestException bre) {
        model.addAttribute("errorMessage", 
            this.messageSource.getMessage(bre.getErrorCode(), null, Locale.getDefault()));
        return "sso";
    }
}
```

**Flow**:
1. User submits: `POST /sso?sso_registration_id=google`
2. System calls `DynamicIDPRegistrationRepository.findByRegistrationId("google")`
3. Queries `customer_idp_config` where `registration_id = "google"`
4. Returns Google's IDP config (tenant_id, client_id, client_secret, endpoints)
5. Redirects: `redirect:/oauth2/authorization?registration_id=google`
6. Spring Security OAuth2 client handles the redirect to Google

---

## Entity Mappings

### `CustomerIdpConfig` Entity

**File**: `src/main/java/com/openbanking/authentication/entities/CustomerIdpConfig.java`

```java
@Getter
@Setter
@Entity
@Table(name = "customer_idp_config")
public class CustomerIdpConfig implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tenant_id", unique = true, nullable = false)
    private String tenantId;                              // ← Tenant boundary

    @Column(name = "registration_id", nullable = false)
    private String registrationId;                        // ← Provider name (google, azure, etc.)

    @Column(name = "client_id", nullable = false)
    private String clientId;                              // ← EXTERNAL provider's OAuth client ID

    @Column(name = "client_secret", nullable = false)
    @Convert(converter = EncryptionConverter.class)       // ← AES encrypted
    private String clientSecret;                          // ← EXTERNAL provider's OAuth secret

    @Column(name = "issuer_uri", nullable = false)
    private String issuerUri;                             // ← OIDC issuer endpoint

    @Column(name = "authorization_uri", nullable = false)
    private String authorizationUri;                      // ← OAuth authorization endpoint

    @Column(name = "token_uri", nullable = false)
    private String tokenUri;                              // ← OAuth token endpoint

    @Column(name = "jwk_set_uri", nullable = false)
    private String jwkSetUri;                             // ← JWKS endpoint

    @Column(name = "user_info_uri", nullable = false)
    private String userInfoUri;                           // ← UserInfo endpoint

    @Column(name = "user_name_attribute", nullable = false)
    private String userNameAttribute;                     // ← Which claim to use as username

    @Column(name = "scope")
    private String scope;                                 // ← OAuth scopes (openid, profile, email)
}
```

**Maps to SQL**:
```sql
CREATE TABLE customer_idp_config (
    id SERIAL PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL UNIQUE,           -- Org boundary
    registration_id VARCHAR(100) NOT NULL UNIQUE,     -- Provider name
    client_id VARCHAR(255) NOT NULL,                  -- External OAuth client
    client_secret VARCHAR(255) NOT NULL,              -- Encrypted external secret
    issuer_uri VARCHAR(255) NOT NULL,
    authorization_uri VARCHAR(255) NOT NULL,
    token_uri VARCHAR(255) NOT NULL,
    jwk_set_uri VARCHAR(255) NOT NULL,
    user_info_uri VARCHAR(255) NOT NULL,
    user_name_attribute VARCHAR(100) NOT NULL,
    scope VARCHAR(255) DEFAULT 'openid,profile,email',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

### `Client` Entity

**File**: `src/main/java/com/openbanking/authentication/entities/Client.java`

```java
@Getter
@Setter
@Entity
@Table(name = "\"client\"")
public class Client implements Serializable {
    @Id
    private String id;                                    // UUID

    private String provider;                              // "internal" or "external"

    @Column(name = "client_id", nullable = false, unique = true)
    private String clientId;                              // "demo-client"

    @Column(name = "client_secret")
    private String clientSecret;                          // Bcrypt encoded

    @Column(name = "client_name")
    private String clientName;                            // "Demo Client"

    @Column(name = "client_authentication_methods", length = 1000)
    private String clientAuthenticationMethods;           // "client_secret_basic,client_secret_post"

    @Column(name = "authorization_grant_types", length = 1000)
    private String authorizationGrantTypes;               // "authorization_code,refresh_token"

    @Column(name = "redirect_uris", length = 1000)
    private String redirectUris;                          // "http://localhost:3000/callback"

    @Column(name = "post_logout_redirect_uris", length = 1000)
    private String postLogoutRedirectUris;                // "http://localhost:3000"

    @Column(length = 1000)
    private String scopes;                                // "read:hotel,write:booking"

    @Column(name = "client_settings", length = 2000)
    private String clientSettings;                        // JSON: {roles, mfaEnabled, ...}

    @Column(name = "token_settings", length = 2000)
    private String tokenSettings;                         // JSON: {accessTokenTTL, refreshTokenTTL, ...}

    @Column(name = "mfa_enabled")
    private boolean mfaEnabled;

    @Column(name = "mfa_secret")
    @Convert(converter = EncryptionConverter.class)
    private String mfaSecret;                             // Encrypted TOTP secret
}
```

**Note**: NO `tenant_id` or `registration_id` fields!

**Maps to SQL**:
```sql
CREATE TABLE client (
    id VARCHAR(255) NOT NULL,
    provider VARCHAR(255) NOT NULL,
    client_id VARCHAR(255) NOT NULL UNIQUE,
    client_name VARCHAR(255) NOT NULL,
    client_secret VARCHAR(255),
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types VARCHAR(1000) NOT NULL,
    redirect_uris VARCHAR(1000),
    post_logout_redirect_uris VARCHAR(1000),
    scopes VARCHAR(1000) NOT NULL,
    client_settings VARCHAR(2000) NOT NULL,
    token_settings VARCHAR(2000) NOT NULL,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    -- ... other fields
    PRIMARY KEY (id)
);
```

---

## Client Request DTO

**File**: `src/main/java/com/openbanking/authentication/dto/ClientRequest.java`

```java
public record ClientRequest(
    String provider,                                    // "internal"
    @NotNull String clientId,                           // "demo-client"
    String clientSecret,                                // "demo-secret" (will be encoded)
    @NotNull String clientName,                         // "Demo Client"
    @NotNull List<String> aud,                          // ["hotel_reservation"]
    @NotNull boolean mfaEnabled,                        // false
    @NotNull boolean requireProofKey,                   // true (PKCE)
    @NotNull boolean requireAuthorizationConsent,       // false
    @NotNull List<String> authenticationMethods,        // ["client_secret_basic"]
    @NotNull List<String> grantTypes,                   // ["authorization_code", "refresh_token"]
    @NotNull List<String> scopes,                       // ["read:hotel_reservation", "create:hotel_reservation"]
    List<String> roles,                                 // ["read:idp", "create:idp"]
    List<String> redirectUris,                          // ["http://localhost:3000/callback"]
    List<String> postLogoutRedirectUris                 // ["http://localhost:3000"]
) {}
```

**Note**: NO `tenant_id` or `registration_id` fields!

---

## Repository Layer

### IDP Config Repository

**File**: `src/main/java/com/openbanking/authentication/repository/reader/ReaderCustomerIdpRepository.java` (assumed)

```java
@Repository
public interface ReaderCustomerIdpRepository extends JpaRepository<CustomerIdpConfig, Integer> {
    // ✓ Query by registration_id (provider name)
    Optional<CustomerIdpConfig> findByRegistrationIdIgnoreCase(String registrationId);

    // ✓ Query by tenant_id
    Optional<CustomerIdpConfig> findByTenantIdIgnoreCase(String tenantId);

    // ✓ Query by both (for multi-tenant)
    Optional<CustomerIdpConfig> findByRegistrationIdIgnoreCaseAndTenantIdIgnoreCase(
        String registrationId, 
        String tenantId
    );
}
```

**Usage in DynamicIDPRegistrationRepository**:
```java
@Override
public ClientRegistration findByRegistrationId(String registrationId) {
    CustomerIdpConfig config = readerCustomerIdpRepository
        .findByRegistrationIdIgnoreCase(registrationId)  // ← Single query by registration_id
        .orElseThrow(...);
    // ...
}
```

---

### Client Repository

**Files**: 
- `src/main/java/com/openbanking/authentication/repository/reader/ReaderClientRepository.java`
- `src/main/java/com/openbanking/authentication/repository/writer/WriterClientRepository.java`

```java
@Repository
public interface ReaderClientRepository extends JpaRepository<Client, String> {
    // ✓ Query by client_id (unique)
    Optional<Client> findByClientId(String clientId);

    // ✓ Query by id (UUID)
    Optional<Client> findById(String id);
}
```

**Usage in ClientService**:
```java
public Optional<Client> findByClientId(String clientId) {
    Optional<Client> client = Optional.empty();
    Client cachedClient = (Client) redisTemplate
        .opsForValue()
        .get(CLIENTID_DTO + clientId);      // ← Check Redis cache first
    
    if (cachedClient == null) {
        client = this.readerClientRepository.findByClientId(clientId);  // ← Query DB
        client.ifPresent(this::reloadCache);
    } else {
        client = Optional.of(cachedClient);
    }
    return client;
}
```

---

## Config Classes

### `AppConfig` (Application Settings)

**File**: `src/main/java/com/openbanking/authentication/config/AppConfig.java`

```java
@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppConfig {
    // ✓ Callback URL base (different per profile)
    // local.yml: "https://localhost:8443/login/oauth2/code/"
    // local-http.yml: "http://localhost:8081/login/oauth2/code/"
    private String defaultAuthCodeUrl;

    private List<String> defaultRedirectUris;
    
    // ... other settings
}
```

**Usage**:
```java
// In DynamicIDPRegistrationRepository:
.redirectUri(appConfig.getDefaultAuthCodeUrl() + registrationId)
// E.g., "http://localhost:8081/login/oauth2/code/" + "google" 
//    = "http://localhost:8081/login/oauth2/code/google"
```

---

## Encryption & Security

### Entity Encryption

**File**: `src/main/java/com/openbanking/authentication/util/EncryptionConverter.java`

```java
@Converter
public class EncryptionConverter implements AttributeConverter<String, String> {
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        // ✓ Encrypt sensitive fields before persisting
        // Applied to:
        // - CustomerIdpConfig.clientSecret (external IDP secret)
        // - Client.mfaSecret (TOTP secret)
        if (attribute == null) return null;
        return encryptionService.encrypt(attribute);
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        // ✓ Decrypt sensitive fields when loading from DB
        if (dbData == null) return null;
        return encryptionService.decrypt(dbData);
    }
}
```

**Applied to**:
```java
// In CustomerIdpConfig:
@Convert(converter = EncryptionConverter.class)
private String clientSecret;  // ← EXTERNAL provider's secret (AES encrypted in DB)

// In Client:
@Convert(converter = EncryptionConverter.class)
private String mfaSecret;     // ← TOTP secret (AES encrypted in DB)
```

---

## Bruno Requests Reference

### Create IDP Config (Tenant's External Provider)

**File**: `bruno/ob-authentication-service/IDP/01 Create IDP Config.bru`

```
POST /api/clients/idp
{
  "tenant_id": "hotel_chain_1",
  "registration_id": "google",
  "client_id": "...apps.googleusercontent.com",
  "client_secret": "...",
  "issuer_uri": "https://accounts.google.com",
  "authorization_uri": "https://accounts.google.com/o/oauth2/v2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "jwk_set_uri": "https://www.googleapis.com/oauth2/v3/certs",
  "user_info_uri": "https://openidconnect.googleapis.com/v1/userinfo",
  "user_name_attribute": "email",
  "scope": "openid,profile,email"
}

↓ Saved to: customer_idp_config
```

### Create Internal Client (Application)

**File**: `bruno/ob-authentication-service/Clients/01 Create Client.bru`

```
POST /api/clients
{
  "provider": "internal",
  "clientId": "demo-client",
  "clientSecret": "demo-secret",
  "clientName": "Demo Client",
  "aud": ["hotel_reservation"],
  "mfaEnabled": false,
  "requireProofKey": true,
  "requireAuthorizationConsent": false,
  "authenticationMethods": ["client_secret_basic"],
  "grantTypes": ["authorization_code", "refresh_token"],
  "scopes": ["read:hotel_reservation", "create:hotel_reservation"],
  "roles": ["read:idp", "create:idp"],
  "redirectUris": ["http://localhost:3000/callback"],
  "postLogoutRedirectUris": ["http://localhost:3000"]
}

↓ Saved to: client
```

---

## Summary: Code Evidence

| Concept | Code Location | Evidence |
|---------|---------------|----------|
| `registration_id` = provider name | `DynamicIDPRegistrationRepository.findByRegistrationId()` | Queries `customer_idp_config` by `registration_id` to get Google/Azure config |
| `tenant_id` = org boundary | `CustomerIdpConfig.tenantId` field | Column in `customer_idp_config` table |
| `clientId` = internal app ID | `Client.clientId` field | Unique column in `client` table |
| Internal client NOT tenant-specific | `ClientRequest` DTO | No `tenant_id` parameter when creating client |
| IDP config = external provider creds | `CustomerIdpConfig` fields | Contains `client_id` and `client_secret` of external provider |
| Internal app redirects after auth | `Client.redirectUris` field | Where app is redirected after federated login |
| Tenant context implicit in IDP selection | `LoginController.postSSO()` | User selects IDP (registration_id), which carries tenant context |

---

## Quick Trace: From Request to Database

### Creating an Internal Client

```
HTTP Request:
POST /api/clients
{
  "clientId": "demo-client",
  ...
}
      ↓
ClientController.create()
      ↓
ClientService.save(ClientRequest dto, null)
      ↓
CommonService.toEntity(RegisteredClient, ObjectMapper)
      ↓
WriterClientRepository.save(Client entity)
      ↓
INSERT INTO client (id, clientId, ...)
VALUES (uuid, 'demo-client', ...)
      ↓
Success: Client stored in DB
```

### Creating an IDP Config

```
HTTP Request:
POST /api/clients/idp
{
  "tenant_id": "hotel_chain_1",
  "registration_id": "google",
  ...
}
      ↓
ClientController.createRegisteredIDP()
      ↓
DynamicIDPRegistrationRepository.save(CustomerIdpConfig request)
      ↓
WriterCustomerIdpRepository.save(CustomerIdpConfig entity)
      ↓
INSERT INTO customer_idp_config (tenant_id, registration_id, ...)
VALUES ('hotel_chain_1', 'google', ...)
      ↓
Success: IDP config stored in DB
```

### Federated Login Flow

```
Browser Request:
POST /sso?sso_registration_id=google
      ↓
LoginController.postSSO(registrationId = "google")
      ↓
DynamicIDPRegistrationRepository.findByRegistrationId("google")
      ↓
ReaderCustomerIdpRepository.findByRegistrationIdIgnoreCase("google")
      ↓
SELECT * FROM customer_idp_config 
WHERE registration_id = 'google'
      ↓
ClientRegistration built with:
  .clientId("...apps.googleusercontent.com")    [from DB]
  .clientSecret("[encrypted secret]")             [from DB, decrypted]
  .redirectUri("http://localhost:8081/login/oauth2/code/google")  [from AppConfig + registration_id]
      ↓
30x Redirect to:
https://accounts.google.com/o/oauth2/v2/auth?client_id=...&redirect_uri=...&scope=...
      ↓
User signs in at Google
      ↓
Google redirects back to:
http://localhost:8081/login/oauth2/code/google?code=AUTH_CODE&state=STATE
      ↓
Spring exchanges AUTH_CODE for ID Token
      ↓
User is authenticated
```

---

See also:
- [TENANT_AND_CLIENT_QUICK_REFERENCE.md](./TENANT_AND_CLIENT_QUICK_REFERENCE.md) for simplified concepts
- [MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md](./MULTI_TENANCY_AND_CLIENT_MANAGEMENT.md) for architectural deep dive

