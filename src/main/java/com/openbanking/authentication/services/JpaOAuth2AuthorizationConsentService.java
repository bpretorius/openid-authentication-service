package com.openbanking.authentication.services;

import java.util.HashSet;
import java.util.Set;

import com.openbanking.authentication.entities.AuthorizationConsent;
import com.openbanking.authentication.repository.reader.ReaderAuthorizationConsentRepository;
import com.openbanking.authentication.repository.writer.WriterAuthorizationConsentRepository;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Component
public class JpaOAuth2AuthorizationConsentService implements OAuth2AuthorizationConsentService {

    private final ReaderAuthorizationConsentRepository readerAuthorizationConsentRepository;
    private final WriterAuthorizationConsentRepository writerAuthorizationConsentRepository;
    private final JpaRegisteredClientRepository splitJpaRegisteredClientRepository;

    public JpaOAuth2AuthorizationConsentService(ReaderAuthorizationConsentRepository readerAuthorizationConsentRepository, WriterAuthorizationConsentRepository writerAuthorizationConsentRepository, JpaRegisteredClientRepository splitJpaRegisteredClientRepository) {
        Assert.notNull(readerAuthorizationConsentRepository, "readerAuthorizationConsentRepository cannot be null");
        Assert.notNull(writerAuthorizationConsentRepository, "writerAuthorizationConsentRepository cannot be null");
        Assert.notNull(splitJpaRegisteredClientRepository, "readerJpaRegisteredClientRepository cannot be null");
        this.readerAuthorizationConsentRepository = readerAuthorizationConsentRepository;
        this.writerAuthorizationConsentRepository = writerAuthorizationConsentRepository;
        this.splitJpaRegisteredClientRepository = splitJpaRegisteredClientRepository;
    }

    @Override
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
        Assert.notNull(authorizationConsent, "authorizationConsent cannot be null");
        this.writerAuthorizationConsentRepository.save(toEntity(authorizationConsent));
    }

    @Override
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        Assert.notNull(authorizationConsent, "authorizationConsent cannot be null");
        this.writerAuthorizationConsentRepository.deleteByRegisteredClientIdAndPrincipalName(
                authorizationConsent.getRegisteredClientId(), authorizationConsent.getPrincipalName());
    }

    @Override
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        Assert.hasText(registeredClientId, "registeredClientId cannot be empty");
        Assert.hasText(principalName, "principalName cannot be empty");
        return this.readerAuthorizationConsentRepository.findByRegisteredClientIdAndPrincipalName(
                registeredClientId, principalName).map(this::toObject).orElse(null);
    }

    private OAuth2AuthorizationConsent toObject(AuthorizationConsent authorizationConsent) {
        String registeredClientId = authorizationConsent.getRegisteredClientId();
        RegisteredClient registeredClient = this.splitJpaRegisteredClientRepository.findById(registeredClientId);
        if (registeredClient == null) {
            throw new DataRetrievalFailureException(
                    "The RegisteredClient with id '" + registeredClientId + "' was not found in the RegisteredClientRepository.");
        }

        OAuth2AuthorizationConsent.Builder builder = OAuth2AuthorizationConsent.withId(
                registeredClientId, authorizationConsent.getPrincipalName());
        if (authorizationConsent.getAuthorities() != null) {
            for (String authority : StringUtils.commaDelimitedListToSet(authorizationConsent.getAuthorities())) {
                builder.authority(new SimpleGrantedAuthority(authority));
            }
        }

        return builder.build();
    }

    private AuthorizationConsent toEntity(OAuth2AuthorizationConsent authorizationConsent) {
        AuthorizationConsent entity = new AuthorizationConsent();
        entity.setRegisteredClientId(authorizationConsent.getRegisteredClientId());
        entity.setPrincipalName(authorizationConsent.getPrincipalName());

        Set<String> authorities = new HashSet<>();
        for (GrantedAuthority authority : authorizationConsent.getAuthorities()) {
            authorities.add(authority.getAuthority());
        }
        entity.setAuthorities(StringUtils.collectionToCommaDelimitedString(authorities));

        return entity;
    }
}