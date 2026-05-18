package com.openbanking.authentication.entities;

import com.openbanking.authentication.util.EncryptionConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "\"client\"")
public class Client implements Serializable {
    @Id
    private String id;
    private String provider;
    @Column(name = "client_id", nullable = false, unique = true)
    private String clientId;
    @Column(name = "client_id_issued_at")
    private Instant clientIdIssuedAt;
    @Column(name = "client_secret")
    private String clientSecret;
    @Column(name = "client_secret_expires_at")
    private Instant clientSecretExpiresAt;
    @Column(name = "mfa_key_id")
    private String mfaKeyId;
    @Column(name = "mfa_secret")
    @Convert(converter = EncryptionConverter.class)
    private String mfaSecret;
    @Column(name = "mfa_enabled")
    private boolean mfaEnabled = false;
    @Column(name = "mfa_registered")
    private boolean mfaRegistered = false;
    @Column(name = "client_name")
    private String clientName;
    @Column(name = "client_authentication_methods", length = 1000)
    private String clientAuthenticationMethods;
    @Column(name = "authorization_grant_types", length = 1000)
    private String authorizationGrantTypes;
    @Column(name = "redirect_uris", length = 1000)
    private String redirectUris;
    @Column(name = "post_logout_redirect_uris", length = 1000)
    private String postLogoutRedirectUris;
    @Column(length = 1000)
    private String scopes;
    @Column(name = "client_settings", length = 2000)
    private String clientSettings;
    @Column(name = "token_settings", length = 2000)
    private String tokenSettings;
}
