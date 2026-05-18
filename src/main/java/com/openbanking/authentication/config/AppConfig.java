package com.openbanking.authentication.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppConfig {

    private Map<String, String[]> validationErrorCodes;
    private String jwtKeyId;
    private String defaultAuthCodeUrl;
    private int accessTokenTTL;
    private int refreshTokenTTL;
    private List<String> defaultResources;
    private List<String> defaultAuthenticationMethods;
    private List<String> defaultGrantTypes;
    private List<String> defaultScopes;
    private List<String> defaultRoles;
    private List<String> defaultRedirectUris;
    private List<String> defaultPostLogoutRedirectUris;
    private int defaultEntityTTL;
    private int securityContextEntityTTL;
    private int clientEntityTTL;
}
