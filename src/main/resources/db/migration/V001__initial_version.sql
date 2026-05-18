CREATE TABLE client (
    id VARCHAR(255) NOT NULL,
    provider VARCHAR(255) NOT NULL,
    client_id VARCHAR(255) NOT NULL UNIQUE,
    client_id_issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret VARCHAR(255),
    client_secret_expires_at TIMESTAMP,
    mfa_key_id VARCHAR(255),
    mfa_secret VARCHAR(255),
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_registered BOOLEAN NOT NULL DEFAULT FALSE,
    client_name VARCHAR(255) NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types VARCHAR(1000) NOT NULL,
    redirect_uris VARCHAR(1000),
    post_logout_redirect_uris VARCHAR(1000),
    scopes VARCHAR(1000) NOT NULL,
    client_settings VARCHAR(2000) NOT NULL,
    token_settings VARCHAR(2000) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE "authorization" (
    id VARCHAR(255) NOT NULL,
    registered_client_id VARCHAR(255) NOT NULL,
    principal_name VARCHAR(255) NOT NULL,
    authorization_grant_type VARCHAR(255) NOT NULL,
    authorized_scopes VARCHAR(1000),
    attributes TEXT,
    state VARCHAR(500),
    authorization_code_value TEXT UNIQUE,
    authorization_code_issued_at TIMESTAMP,
    authorization_code_expires_at TIMESTAMP,
    authorization_code_metadata VARCHAR(2000),
    access_token_value TEXT UNIQUE,
    access_token_issued_at TIMESTAMP,
    access_token_expires_at TIMESTAMP,
    access_token_metadata VARCHAR(2000),
    access_token_type VARCHAR(255),
    access_token_scopes VARCHAR(1000),
    refresh_token_value TEXT UNIQUE,
    refresh_token_issued_at TIMESTAMP,
    refresh_token_expires_at TIMESTAMP,
    refresh_token_metadata VARCHAR(2000),
    oidc_id_token_value TEXT UNIQUE,
    oidc_id_token_issued_at TIMESTAMP,
    oidc_id_token_expires_at TIMESTAMP,
    oidc_id_token_metadata VARCHAR(2000),
    oidc_id_token_claims VARCHAR(2000),
    user_code_value TEXT UNIQUE,
    user_code_issued_at TIMESTAMP,
    user_code_expires_at TIMESTAMP,
    user_code_metadata VARCHAR(2000),
    device_code_value TEXT UNIQUE,
    device_code_issued_at TIMESTAMP,
    device_code_expires_at TIMESTAMP,
    device_code_metadata VARCHAR(2000),
    PRIMARY KEY (id)
);

CREATE TABLE authorization_consent (
    registered_client_id VARCHAR(255) NOT NULL,
    principal_name VARCHAR(255) NOT NULL,
    authorities VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

CREATE TABLE jwk_keys (
    key_id VARCHAR(255) NOT NULL,
    jwk_json VARCHAR(8192) NOT NULL,
    PRIMARY KEY (key_id)
);

CREATE TABLE customer_idp_config (
    id SERIAL PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL UNIQUE,
    registration_id VARCHAR(100) NOT NULL UNIQUE,
    client_id VARCHAR(255) NOT NULL,
    client_secret VARCHAR(255) NOT NULL,
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

-- Insert super user
INSERT INTO public.client
(id, provider, client_id, client_id_issued_at, client_secret, client_secret_expires_at, client_name, client_authentication_methods, authorization_grant_types, redirect_uris, post_logout_redirect_uris, scopes, client_settings, token_settings)
VALUES('bebf0cc9-10ba-49e1-abbe-ac407ef362b3', 'internal', '05d0c763-b23d-4cd5-b628-63fde9ab7227', '2025-05-14 07:59:39.168', '$2a$10$w6OTN/H5CJg1QE6peKCmb.JbJ37Da2rugL4/0di8JukioijUDMMdi', NULL, 'super_user_idp', 'client_secret_jwt,client_secret_basic,client_secret_post', 'client_credentials', '', '', 'openid,profile,email', '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-authorization-consent":false,"settings.client.require-proof-key":false,"resource_indicator":"ob_idp","roles":"super_user:idp","provider":"internal"}', '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",604800.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}');
