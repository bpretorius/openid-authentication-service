package com.openbanking.authentication.services;

import com.openbanking.authentication.entities.Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CommonService {

    public Client toEntity(RegisteredClient registeredClient, ObjectMapper objectMapper) {
        List<String> clientAuthenticationMethods = new ArrayList<>(registeredClient.getClientAuthenticationMethods().size());
        registeredClient.getClientAuthenticationMethods().forEach(clientAuthenticationMethod ->
                clientAuthenticationMethods.add(clientAuthenticationMethod.getValue()));

        List<String> authorizationGrantTypes = new ArrayList<>(registeredClient.getAuthorizationGrantTypes().size());
        registeredClient.getAuthorizationGrantTypes().forEach(authorizationGrantType ->
                authorizationGrantTypes.add(authorizationGrantType.getValue()));

        Client entity = new Client();
        entity.setProvider((String) registeredClient.getClientSettings().getSettings().get("provider"));
        entity.setId(registeredClient.getId());
        entity.setClientId(registeredClient.getClientId());
        entity.setClientIdIssuedAt(registeredClient.getClientIdIssuedAt());
        entity.setClientSecret(registeredClient.getClientSecret());
        entity.setClientSecretExpiresAt(registeredClient.getClientSecretExpiresAt());
        entity.setMfaEnabled((Boolean) registeredClient.getClientSettings().getSettings().get("mfaEnabled"));
        entity.setClientName(registeredClient.getClientName());
        entity.setClientAuthenticationMethods(org.springframework.util.StringUtils.collectionToCommaDelimitedString(clientAuthenticationMethods));
        entity.setAuthorizationGrantTypes(org.springframework.util.StringUtils.collectionToCommaDelimitedString(authorizationGrantTypes));
        entity.setRedirectUris(org.springframework.util.StringUtils.collectionToCommaDelimitedString(registeredClient.getRedirectUris()));
        entity.setPostLogoutRedirectUris(org.springframework.util.StringUtils.collectionToCommaDelimitedString(registeredClient.getPostLogoutRedirectUris()));
        entity.setScopes(org.springframework.util.StringUtils.collectionToCommaDelimitedString(registeredClient.getScopes()));
        entity.setClientSettings(writeMap(registeredClient.getClientSettings().getSettings(), objectMapper));
        entity.setTokenSettings(writeMap(registeredClient.getTokenSettings().getSettings(), objectMapper));

        return entity;
    }

    private String writeMap(Map<String, Object> data, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }
}
