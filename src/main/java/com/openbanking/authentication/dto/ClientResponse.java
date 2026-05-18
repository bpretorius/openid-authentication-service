package com.openbanking.authentication.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ClientResponse(
        String provider,
        String id,
        String clientId,
        String clientName,
        List<String> aud,
        boolean mfaEnabled,
        boolean requireProofKey,
        boolean requireAuthorizationConsent,
        List<String> authenticationMethods,
        List<String> grantTypes,
        List<String> scopes,
        List<String> roles,
        List<String> redirectUris,
        List<String> postLogoutRedirectUris
) {}
// This record represents a response object for client information.