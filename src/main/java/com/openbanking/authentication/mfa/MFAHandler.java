package com.openbanking.authentication.mfa;

import java.io.IOException;
import java.util.Optional;
import com.openbanking.authentication.entities.Client;
import com.openbanking.authentication.services.ClientService;
import com.openbanking.authentication.services.JpaOAuth2AuthorizationService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static com.openbanking.authentication.util.Constants.MFA_INPROGRESS;

public class MFAHandler implements AuthenticationSuccessHandler {

    private final ClientService clientService;
    private final JpaOAuth2AuthorizationService jpaOAuth2AuthorizationService;
    private final AuthenticationSuccessHandler mfaNotEnabled = new SavedRequestAwareAuthenticationSuccessHandler();
    private final AuthenticationSuccessHandler authenticationSuccessHandler;

    public MFAHandler(String successUrl, ClientService clientService, JpaOAuth2AuthorizationService jpaOAuth2AuthorizationService) {

        SimpleUrlAuthenticationSuccessHandler authenticationSuccessHandler = new SimpleUrlAuthenticationSuccessHandler(successUrl);
        authenticationSuccessHandler.setAlwaysUseDefaultTargetUrl(true);
        this.authenticationSuccessHandler = authenticationSuccessHandler;
        this.clientService = clientService;
        this.jpaOAuth2AuthorizationService = jpaOAuth2AuthorizationService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        Optional<Client> client = null;
        if (authentication instanceof UsernamePasswordAuthenticationToken) {
            User user = (User) authentication.getPrincipal();
            client = clientService.findByClientId(user.getUsername());
        }

        if (authentication instanceof OAuth2AuthenticationToken) {
            DefaultOAuth2User user = (DefaultOAuth2User) authentication.getPrincipal();
            client = clientService.findByClientId(user.getName());
        }

        if (client != null) {
            if (client.isPresent()) {
                if (client.get().isMfaEnabled()) {
                    redirectToMFA(request, response, authentication);
                    return;
                }
            }
        }

        mfaNotEnabled.onAuthenticationSuccess(request, response, authentication);

    }

    public void redirectToMFA(HttpServletRequest request,
                              HttpServletResponse response,
                              Authentication authentication) throws ServletException, IOException {
        this.jpaOAuth2AuthorizationService.addMFA(authentication.getDetails(), MFA_INPROGRESS);
        this.authenticationSuccessHandler.onAuthenticationSuccess(request, response, authentication);
    }
}
