package com.openbanking.authentication.services;

import com.openbanking.authentication.config.AppConfig;
import com.openbanking.authentication.entities.OAuth2AuthorizationRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AppConfig appConfig;
    private final String DTO = "OAuth2AuthorizationRequestDTO";

    public RedisAuthorizationRequestRepository(
            RedisTemplate<String, Object> redisTemplate,
            AppConfig appConfig) {
        this.redisTemplate = redisTemplate;
        this.appConfig = appConfig;
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {

        if (authorizationRequest == null) {
            // Treat null as a signal to remove
            removeAuthorizationRequest(request, response);
            return;
        }

        OAuth2AuthorizationRequestDTO entity = new OAuth2AuthorizationRequestDTO();
        entity.setAuthorizationRequestId(authorizationRequest.getState());
        entity.setClientId(authorizationRequest.getClientId());
        entity.setRegistrationId(authorizationRequest.getAttribute("registration_id"));
        entity.setState(authorizationRequest.getState());
        entity.setRedirectUri(authorizationRequest.getRedirectUri());
        entity.setAuthorizationUri(authorizationRequest.getAuthorizationUri());
        entity.setTokenUri(authorizationRequest.getAttributes() != null
                ? (String) authorizationRequest.getAttributes().get("token_uri") : null);


        redisTemplate.opsForValue().set(DTO + authorizationRequest.getState(), entity, appConfig.getDefaultEntityTTL(), TimeUnit.SECONDS);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        String state = request.getParameter("state");
        if (state == null) return null;

        OAuth2AuthorizationRequestDTO entity = (OAuth2AuthorizationRequestDTO) redisTemplate.opsForValue().get(DTO + state);

        redisTemplate.delete(DTO + state);

        return build(entity);
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter("state");
        if (state == null) return null;

        OAuth2AuthorizationRequestDTO entity = (OAuth2AuthorizationRequestDTO) redisTemplate.opsForValue().get(DTO + state);

        return build(entity);
    }

    private OAuth2AuthorizationRequest build(OAuth2AuthorizationRequestDTO entity) {

        if (entity == null) {
            return null;
        }
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .clientId(entity.getClientId())
                .state(entity.getState())
                .redirectUri(entity.getRedirectUri())
                .authorizationUri(entity.getAuthorizationUri())
                .attributes(attrs -> {
                    if (entity.getTokenUri() != null) {
                        attrs.put("token_uri", entity.getTokenUri());
                    }
                    if (entity.getRegistrationId() != null) {
                        attrs.put("registration_id", entity.getRegistrationId());
                    }
                    attrs.put("user-name-attribute", "unique_name");
                    attrs.put("user-name", "unique_name");
                    attrs.put("user name", "unique_name");
                    attrs.put("username", "unique_name");
                })
                .build();

        return authorizationRequest;
    }
}
