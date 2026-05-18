package com.openbanking.authentication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "encryption")
public class EncryptionConfigHolder {

    private static String secret;

    public void setSecret(String secret) {
        EncryptionConfigHolder.secret = secret;
    }
    public static String getSecret() {
        return secret;
    }

}