package com.openbanking.authentication.services;

import com.openbanking.authentication.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.openbanking.authentication.util.Constants.BUSINESS_ERROR.ORGANIZATION_NOT_FOUND;

public class DynamicAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final ClientRegistrationRepository clientRegistrationRepository;

    public DynamicAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {

      String registrationId = request.getParameter("registration_id"); // or from subdomain/header/etc.

        if (registrationId == null) {
            return null;
        }

        return build(request, registrationId);
    }

    private OAuth2AuthorizationRequest build(HttpServletRequest request, String registrationId) {
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(registrationId);
        if (clientRegistration == null) {
            throw new BadRequestException(ORGANIZATION_NOT_FOUND.toString(), null);
        }

        // Important: include nonce for OpenID Connect (required by Azure)
        String nonce = UUID.randomUUID().toString();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(OAuth2ParameterNames.REGISTRATION_ID, clientRegistration.getRegistrationId());
        attributes.put(OAuth2ParameterNames.USERNAME, "unique_name");
        attributes.put("user-name", "unique_name");
        attributes.put("user name", "unique_name");
        attributes.put("user-name-attribute", "unique_name");

        Map<String, Object> additionalParameters = new HashMap<>();
        additionalParameters.put("nonce", nonce);
        additionalParameters.put("response_mode", "form_post"); // Optional: form_post or query

        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(clientRegistration.getProviderDetails().getAuthorizationUri())
                .clientId(clientRegistration.getClientId())
                .redirectUri(clientRegistration.getRedirectUri())
                .scopes(clientRegistration.getScopes())
                .state(UUID.randomUUID().toString())
                .attributes(attributes)
                .additionalParameters(additionalParameters)
                .build();
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return build(request, registrationId);
    }
}
