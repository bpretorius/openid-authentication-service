package com.openbanking.authentication.dto;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ClientRequest(
        String provider,
        @NotNull String clientId,
        String clientSecret,
        @NotNull String clientName,
        @NotNull List<String> aud,
        @NotNull boolean mfaEnabled,
        @NotNull boolean requireProofKey,
        @NotNull boolean requireAuthorizationConsent,
        @NotNull List<String> authenticationMethods,
        @NotNull List<String> grantTypes,
        @NotNull List<String> scopes,
        List<String> roles,
        List<String> redirectUris,
        List<String> postLogoutRedirectUris
) {}
// This class is used to represent a request to create or update a client in the system.