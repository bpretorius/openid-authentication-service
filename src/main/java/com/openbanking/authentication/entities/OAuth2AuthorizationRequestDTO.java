package com.openbanking.authentication.entities;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
public class OAuth2AuthorizationRequestDTO implements Serializable {

    private String authorizationRequestId;
    private String clientId;
    private String registrationId;
    private String state;
    private String redirectUri;
    private String authorizationUri;
    private String tokenUri;
    private LocalDateTime createdAt = LocalDateTime.now();

}

