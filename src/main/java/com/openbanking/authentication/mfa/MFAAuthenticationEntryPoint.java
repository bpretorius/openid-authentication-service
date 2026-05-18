package com.openbanking.authentication.mfa;

import com.openbanking.authentication.services.JpaOAuth2AuthorizationService;
import com.openbanking.authentication.util.Constants;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.io.IOException;

import static com.openbanking.authentication.util.Constants.*;

public class MFAAuthenticationEntryPoint extends LoginUrlAuthenticationEntryPoint {

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    private final String loginFormUrl;
    private final JpaOAuth2AuthorizationService jpaOAuth2AuthorizationService;

    public MFAAuthenticationEntryPoint(String loginFormUrl, JpaOAuth2AuthorizationService jpaOAuth2AuthorizationService) {
        super(loginFormUrl);
        this.loginFormUrl = loginFormUrl;
        this.jpaOAuth2AuthorizationService = jpaOAuth2AuthorizationService;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        if (authException instanceof InsufficientAuthenticationException e) {
            if (e.getMessage().contains(Constants.BUSINESS_ERROR.MFA_NOT_COMPLETED.toString())) {

                // CLear the cache so that do not get stuck in a loop
                String[] messages = e.getMessage().split("#=");
                if (messages.length > 1) {
                    String details = messages[1];
                    jpaOAuth2AuthorizationService.removeMFA(details, MFA_INPROGRESS);
                    jpaOAuth2AuthorizationService.removeMFA(details, MFA_COMPLETED);
                    jpaOAuth2AuthorizationService.addMFA(details, MFA_FORCE_LOGOUT);

                    Cookie cookie = new Cookie("JSESSIONID", null);
                    cookie.setPath("/");
                    cookie.setMaxAge(0); // Deletes the cookie
                    cookie.setHttpOnly(true); // Optional: match original settings
                    cookie.setSecure(true);   // Optional: match original settings
                    response.addCookie(cookie);

                }
            }
        }
        super.commence(request ,response, authException);
    }

}
