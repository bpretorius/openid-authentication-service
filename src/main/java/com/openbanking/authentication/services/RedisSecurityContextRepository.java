package com.openbanking.authentication.services;

import com.openbanking.authentication.config.AppConfig;
import com.openbanking.authentication.exception.InternalErrorException;
import com.openbanking.authentication.mfa.SupplierDeferredSecurityContext;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static com.openbanking.authentication.util.Constants.*;

@Component
public class RedisSecurityContextRepository implements SecurityContextRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AppConfig appConfig;
    public final static String DTO = "SPRING_SECURITY_CONTEXT";
    private final JpaOAuth2AuthorizationService jpaOAuth2AuthorizationService;

    public RedisSecurityContextRepository(RedisTemplate<String, Object> redisTemplate, AppConfig appConfig, JpaOAuth2AuthorizationService jpaOAuth2AuthorizationService) {
        this.redisTemplate = redisTemplate;
        this.appConfig = appConfig;
        this.jpaOAuth2AuthorizationService = jpaOAuth2AuthorizationService;
    }

    @Override
    @Deprecated
    public SecurityContext loadContext(HttpRequestResponseHolder httpRequestResponseHolder) {
        return null;
    }

    /**
     * New method to be used in Spring Security 6.1+
     */
    @Override
    public DeferredSecurityContext loadDeferredContext(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return new SupplierDeferredSecurityContext(SecurityContextHolder::createEmptyContext);
        }
        String id = session.getId();
        SecurityContext securityContext = (SecurityContext) redisTemplate.opsForValue().get(DTO+id);
        if (securityContext == null) {
            return new SupplierDeferredSecurityContext(SecurityContextHolder::createEmptyContext);
        }
        Authentication authentication = securityContext.getAuthentication();
        String mfaForceLogout = jpaOAuth2AuthorizationService.getMFA(authentication.getDetails(), MFA_FORCE_LOGOUT);
        if (StringUtils.isNotBlank(mfaForceLogout)) {
            redisTemplate.delete(DTO+id);
            jpaOAuth2AuthorizationService.removeMFA(authentication.getDetails(), MFA_FORCE_LOGOUT);
            return new SupplierDeferredSecurityContext(SecurityContextHolder::createEmptyContext);
        }
        return new SupplierDeferredSecurityContext(() -> securityContext);
    }

    @Override
    public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                return;
            }
            String id = session.getId();
            if (context.getAuthentication().getDetails() instanceof WebAuthenticationDetails details) {
                //id = details.getSessionId();
            }
            redisTemplate.opsForValue().set(DTO+ id, context, appConfig.getSecurityContextEntityTTL(), TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new InternalErrorException(e.getMessage());
        }
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        SecurityContext securityContext = (SecurityContext) redisTemplate.opsForValue().get(DTO+request.getSession().getId());
        return securityContext != null;
    }
}
