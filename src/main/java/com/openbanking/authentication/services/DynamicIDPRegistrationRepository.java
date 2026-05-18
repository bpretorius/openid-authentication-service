package com.openbanking.authentication.services;

import com.openbanking.authentication.config.AppConfig;
import com.openbanking.authentication.entities.CustomerIdpConfig;
import com.openbanking.authentication.exception.BadRequestException;
import com.openbanking.authentication.repository.reader.ReaderCustomerIdpRepository;
import com.openbanking.authentication.repository.writer.WriterCustomerIdpRepository;
import com.openbanking.authentication.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.openbanking.authentication.util.Constants.BUSINESS_ERROR.ORGANIZATION_NOT_FOUND;

@Component
public class DynamicIDPRegistrationRepository implements ClientRegistrationRepository {

    @Autowired
    private AppConfig appConfig;

    private final ReaderCustomerIdpRepository readerCustomerIdpRepository;
    private final WriterCustomerIdpRepository writerCustomerIdpRepository;

    public DynamicIDPRegistrationRepository(ReaderCustomerIdpRepository readerCustomerIdpRepository, WriterCustomerIdpRepository writerCustomerIdpRepository) {
        this.readerCustomerIdpRepository = readerCustomerIdpRepository;
        this.writerCustomerIdpRepository = writerCustomerIdpRepository;
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        CustomerIdpConfig config = readerCustomerIdpRepository.findByRegistrationIdIgnoreCase(registrationId)
                .orElseThrow(() -> new BadRequestException(ORGANIZATION_NOT_FOUND.toString(), ""));

        return ClientRegistration.withRegistrationId(config.getRegistrationId())
                .clientName(config.getRegistrationId())
                .clientId(config.getClientId())
                .clientSecret(config.getClientSecret())
                .issuerUri(config.getIssuerUri())
                .authorizationUri(config.getAuthorizationUri())
                .tokenUri(config.getTokenUri())
                .userInfoUri(config.getUserInfoUri())
                .jwkSetUri(config.getJwkSetUri())
                .redirectUri(appConfig.getDefaultAuthCodeUrl() + registrationId)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .scope("openid", "profile", "email")
                .userNameAttributeName(config.getUserNameAttribute())
                .build();
    }

    public void save(CustomerIdpConfig config) {
        writerCustomerIdpRepository.save(config);
    }

    public Optional<CustomerIdpConfig> findById(Integer id) {
            return readerCustomerIdpRepository.findById(id);
    }

    public Page<CustomerIdpConfig> getConfigs(int page, int size, String tenantId, String registrationId) {
        PageRequest pageable = PageRequest.of(page, size);
        return readerCustomerIdpRepository.findByFilters(
                tenantId != null && !tenantId.isBlank() ? tenantId : null,
                registrationId != null && !registrationId.isBlank() ? registrationId : null,
                pageable
        );
    }

    public void delete(Integer id) {
        CustomerIdpConfig config = readerCustomerIdpRepository.findById(id).orElseThrow(() -> new BadRequestException(Constants.BUSINESS_ERROR.CLIENT_NOT_FOUND.toString()));
        writerCustomerIdpRepository.delete(config);
    }

}

