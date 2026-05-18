package com.openbanking.authentication.entities;


import com.openbanking.authentication.util.EncryptionConverter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "customer_idp_config")
public class CustomerIdpConfig implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tenant_id", unique = true, nullable = false)
    private String tenantId;

    @Column(name = "registration_id", nullable = false)
    private String registrationId;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "client_secret", nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Convert(converter = EncryptionConverter.class)
    private String clientSecret;

    @Column(name = "issuer_uri", nullable = false)
    private String issuerUri;

    @Column(name = "authorization_uri", nullable = false)
    private String authorizationUri;

    @Column(name = "token_uri", nullable = false)
    private String tokenUri;

    @Column(name = "jwk_set_uri", nullable = false)
    private String jwkSetUri;

    @Column(name = "user_info_uri", nullable = false)
    private String userInfoUri;

    @Column(name = "user_name_attribute", nullable = false)
    private String userNameAttribute;

    @Column(name = "scope")
    private String scope;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
