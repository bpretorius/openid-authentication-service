package com.openbanking.authentication.controller;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.Optional;

import com.openbanking.authentication.config.AppConfig;
import com.openbanking.authentication.entities.Client;
import com.openbanking.authentication.exception.BadRequestException;
import com.openbanking.authentication.repository.reader.ReaderClientRepository;
import com.openbanking.authentication.repository.writer.WriterClientRepository;
import com.openbanking.authentication.services.ClientService;
import com.openbanking.authentication.services.DynamicIDPRegistrationRepository;
import com.openbanking.authentication.services.JpaOAuth2AuthorizationService;
import com.openbanking.authentication.services.MfaService;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;

import org.springframework.context.MessageSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static com.openbanking.authentication.util.Constants.BUSINESS_ERROR.ORGANIZATION_EMPTY;
import static com.openbanking.authentication.util.Constants.BUSINESS_ERROR.ORGANIZATION_NOT_FOUND;
import static com.openbanking.authentication.util.Constants.MFA_COMPLETED;

@Controller
public class LoginController {

    private final AuthenticationFailureHandler authenticatorFailureHandler =
            new SimpleUrlAuthenticationFailureHandler("/authenticator?error");

    private final MfaService mfaService;
    private final ReaderClientRepository readerClientRepository;
    private final WriterClientRepository writerClientRepository;
    private final DynamicIDPRegistrationRepository dynamicIDPRegistrationRepository;
    private final MessageSource messageSource;
    private final AppConfig appConfig;
    private final ClientService clientService;
    private final JpaOAuth2AuthorizationService jpaOAuth2AuthorizationService;

    public LoginController(
            MfaService mfaService,
            ReaderClientRepository readerClientRepository,
            WriterClientRepository writerClientRepository,
            DynamicIDPRegistrationRepository dynamicIDPRegistrationRepository,
            MessageSource messageSource,
            AppConfig appConfig,
            ClientService clientService,
            JpaOAuth2AuthorizationService jpaOAuth2AuthorizationService) {
            this.mfaService = mfaService;
            this.readerClientRepository = readerClientRepository;
            this.writerClientRepository = writerClientRepository;
            this.dynamicIDPRegistrationRepository = dynamicIDPRegistrationRepository;
            this.messageSource = messageSource;
            this.appConfig = appConfig;
            this.clientService = clientService;
            this.jpaOAuth2AuthorizationService = jpaOAuth2AuthorizationService;
        }

        @GetMapping("/")
        public String redirectToLogin() {
            return "redirect:/login";
        }

        @GetMapping("/login")
        public String login(Model model,
                HttpServletRequest request,
                HttpServletResponse response,
                @CurrentSecurityContext SecurityContext context) {
            return "login";
        }

        @GetMapping("/error")
        public String error(Model model,
                HttpServletRequest request,
                HttpServletResponse response,
                @CurrentSecurityContext SecurityContext context) {
            return "login";
        }

        @GetMapping("/sso")
        public String getSSO(Model model,
                HttpServletRequest request,
                HttpServletResponse response,
                @CurrentSecurityContext SecurityContext context) {

            return "sso";

        }

        @PostMapping("/sso")
        public String postSSO(Model model,
                HttpServletRequest request,
                HttpServletResponse response,
                @CurrentSecurityContext SecurityContext context) {

            String registrationId = request.getParameter("sso_registration_id");
            if (StringUtils.isBlank(registrationId)) {
                model.addAttribute("errorMessage", this.messageSource.getMessage(ORGANIZATION_EMPTY.toString(), null, Locale.getDefault()));
                return "sso";
            }
            try {
                ClientRegistration clientRegistration = dynamicIDPRegistrationRepository.findByRegistrationId(registrationId);

                if (clientRegistration == null) {
                    model.addAttribute("errorMessage", this.messageSource.getMessage(ORGANIZATION_NOT_FOUND.toString(), null, Locale.getDefault()));
                    return "sso";
                }
                return "redirect:/oauth2/authorization?registration_id="+clientRegistration.getRegistrationId();

            } catch (BadRequestException bre) {
                model.addAttribute("errorMessage", this.messageSource.getMessage(bre.getErrorCode(), null, Locale.getDefault()));
                return "sso";
            }
    }

        @GetMapping("/authenticator")
        public String authenticator(
                HttpServletRequest request,
                HttpServletResponse response,
                @CurrentSecurityContext SecurityContext context) throws GeneralSecurityException {

            if (!getClient(context, request).isMfaRegistered()) {
                return "redirect:registration";
            }
            return "authenticator";
        }

        @PostMapping("/authenticator")
        public void validateCode(
        @RequestParam("code") int code,
        HttpServletRequest request,
        HttpServletResponse response,
        @CurrentSecurityContext SecurityContext context) throws ServletException, IOException {

            if (mfaService.check(getClient(context, request).getMfaSecret(), code)) {
                Authentication authentication = context.getAuthentication();
                SimpleUrlAuthenticationSuccessHandler authenticationSuccessHandler =
                        new SimpleUrlAuthenticationSuccessHandler(appConfig.getDefaultRedirectUris().get(0));

                AuthenticationSuccessHandler savedAuthenticationSuccessHandler = new SavedRequestAwareAuthenticationSuccessHandler();
                savedAuthenticationSuccessHandler.onAuthenticationSuccess(request, response, authentication);
                jpaOAuth2AuthorizationService.addMFA(context.getAuthentication().getDetails(), MFA_COMPLETED);
                return;
            }
            this.authenticatorFailureHandler.onAuthenticationFailure(request, response, new BadCredentialsException("bad credentials"));
        }

        @GetMapping("/registration")
        public String registration(
                Model model,
                HttpServletRequest request,
                @CurrentSecurityContext SecurityContext context) {

            String username = null;
            Authentication authentication =  context.getAuthentication();
            if (authentication instanceof UsernamePasswordAuthenticationToken) {
                User user = (User) authentication.getPrincipal();
                username = user.getUsername();
            }

            if (authentication instanceof OAuth2AuthenticationToken) {
                DefaultOAuth2User user = (DefaultOAuth2User) authentication.getPrincipal();
                username = user.getName();
            }

            String base32Secret = mfaService.generateSecret();
            String keyId = "OpenId: " + username;

            storeValueInSession(request, "keyId", keyId);
            storeValueInSession(request, "base32Secret", base32Secret);

            String qrImage = mfaService.generateQrImageUrl(keyId, base32Secret);

            model.addAttribute("qrImage", qrImage);
            return "registration";
        }

        @PostMapping("/registration")
        public void validateRegistration(@RequestParam("code") int code,
        HttpServletRequest request,
        HttpServletResponse response,
        @CurrentSecurityContext SecurityContext context) throws ServletException, IOException {

            String base32Secret = (String) getValueFromSession(request,"base32Secret");
            if (mfaService.check(base32Secret, code)) {
                String keyId = (String) getValueFromSession(request,"keyId");
                Client client = getClient(context, request);
                client.setMfaKeyId(keyId);
                client.setMfaSecret(base32Secret);
                client.setMfaRegistered(true);
                clientService.save(client);
                Authentication authentication = context.getAuthentication();
                SimpleUrlAuthenticationSuccessHandler authenticationSuccessHandler =
                        new SimpleUrlAuthenticationSuccessHandler(appConfig.getDefaultRedirectUris().get(0));

                AuthenticationSuccessHandler savedAuthenticationSuccessHandler = new SavedRequestAwareAuthenticationSuccessHandler();
                savedAuthenticationSuccessHandler.onAuthenticationSuccess(request, response, authentication);
                jpaOAuth2AuthorizationService.addMFA(context.getAuthentication().getDetails(), MFA_COMPLETED);
                return;
            }

            this.authenticatorFailureHandler.onAuthenticationFailure(request, response, new BadCredentialsException("bad credentials"));
        }

        private Client getClient(SecurityContext context, HttpServletRequest request) {

            String username = null;
            Authentication authentication =  context.getAuthentication();
            if (authentication instanceof UsernamePasswordAuthenticationToken) {
                User user = (User) authentication.getPrincipal();
                username = user.getUsername();
            }

            if (authentication instanceof OAuth2AuthenticationToken) {
                DefaultOAuth2User user = (DefaultOAuth2User) authentication.getPrincipal();
                username = user.getName();
            }

            Optional<Client> client = clientService.findByClientId(username);
            return client.orElse(null);

        }

        public void storeValueInSession(HttpServletRequest request, String key, Object value) {
            HttpSession session = request.getSession(); // Get or create session
            session.setAttribute(key, value); // Store value in session
        }

        public Object getValueFromSession(HttpServletRequest request, String key) {
            HttpSession session = request.getSession(false); // Get existing session, do not create new
            if (session != null) {
                return session.getAttribute(key); // Retrieve stored value
            }
            return null;
        }

    }
