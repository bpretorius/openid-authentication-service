package com.openbanking.authentication.config;

import com.openbanking.authentication.dto.ClientRequest;
import com.openbanking.authentication.entities.Client;
import com.openbanking.authentication.entities.JwkEntity;
import com.openbanking.authentication.repository.reader.ReaderJwkRepository;
import com.openbanking.authentication.repository.writer.WriterJwkRepository;
import com.openbanking.authentication.services.ClientService;
import com.openbanking.authentication.services.JpaOAuth2AuthorizationService;
import com.openbanking.authentication.services.RedisSecurityContextRepository;
import com.openbanking.authentication.util.Constants;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.keygen.BytesKeyGenerator;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import java.util.*;

import static com.openbanking.authentication.util.Constants.MFA_COMPLETED;
import static com.openbanking.authentication.util.Constants.MFA_INPROGRESS;

@Configuration
public class AuthorizationServerConfig {

    private final ReaderJwkRepository readerJwkRepository;
    private final WriterJwkRepository writerJwkRepository;
    private final AppConfig appConfig;

    public AuthorizationServerConfig(ReaderJwkRepository readerJwkRepository, WriterJwkRepository writerJwkRepository, AppConfig appConfig) {
        this.writerJwkRepository = writerJwkRepository;
        this.readerJwkRepository = readerJwkRepository;
        this.appConfig = appConfig;
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {

        JwkEntity jwkEntity = readerJwkRepository.findById(appConfig.getJwtKeyId()).orElseGet(() -> {
            try {
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
                keyPairGenerator.initialize(2048);
                KeyPair keyPair = keyPairGenerator.generateKeyPair();

                RSAKey rsaKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                        .privateKey(keyPair.getPrivate())
                        .keyID(appConfig.getJwtKeyId())
                        .build();

                JwkEntity entity = new JwkEntity();
                entity.setKeyId(appConfig.getJwtKeyId());
                entity.setJwkJson(rsaKey.toJSONString());
                return writerJwkRepository.save(entity);
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate key", e);
            }
        });

        RSAKey rsaKey = RSAKey.parse(jwkEntity.getJwkJson());
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }

    private static KeyPair generateRsaKey() {
        KeyPair keyPair;
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            keyPair = keyPairGenerator.generateKeyPair();
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return keyPair;
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer(HttpServletRequest request,
                                                                   HttpServletResponse response,
                                                                   ClientService clientService,
                                                                   JpaOAuth2AuthorizationService jpaOAuth2AuthorizationService,
                                                                   RedisSecurityContextRepository redisSecurityContextRepository) {
        return context -> {
            Authentication authentication = context.getPrincipal();
            // Ensure If MFA enabled that the MFA process is complete before returning a token.
            String mfaInProgress = jpaOAuth2AuthorizationService.getMFA(authentication.getDetails(), MFA_INPROGRESS);
            if (StringUtils.isNotBlank(mfaInProgress)) {
                String mfaCompleted = jpaOAuth2AuthorizationService.getMFA(authentication.getDetails(), MFA_COMPLETED);
                if (StringUtils.isBlank(mfaCompleted)) {
                    throw new InsufficientAuthenticationException(Constants.BUSINESS_ERROR.MFA_NOT_COMPLETED.toString() + "#=" + authentication.getDetails());
                }
            }

            Object principalObj = authentication.getPrincipal();
            String clientName = getAttribute(principalObj, "name");
            if (StringUtils.isBlank(clientName)) {
                clientName = getAttribute(principalObj, "fullname");
            } else if (StringUtils.isBlank(clientName)) {
                clientName = getAttribute(principalObj, "email");
            } else if (StringUtils.isBlank(clientName)) {
                clientName = getAttribute(principalObj, "preferred_username");
            } else if (StringUtils.isBlank(clientName)) {
                clientName = getAttribute(principalObj, "sub");
            } else if (StringUtils.isBlank(clientName)) {
                clientName = authentication.getName();
            }

            context.getClaims().claim("unique_name", authentication.getName());
            if (StringUtils.isBlank(clientName)) {
                Optional<Client>  client = clientService.findByClientId(authentication.getName());
                if (client.isPresent()) {
                    clientName = client.get().getClientName();
                }
            }

            context.getClaims().claim("name", clientName);

            RegisteredClient client = context.getRegisteredClient();
            Object rolesSetting = client.getClientSettings().getSetting("roles");

            if (rolesSetting instanceof String rolesStr) {
                List<String> roles = new ArrayList<>(Arrays.stream(rolesStr.split(","))
                        .map(String::trim)
                        .toList());
                context.getClaims().claim("roles", roles);
            } else if (rolesSetting instanceof List<?> rolesList) {
                context.getClaims().claim("roles", new ArrayList<>(rolesList));
            }

            List<String> authorities = new ArrayList<>(
                    authentication.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .toList()
            );
            if (!authorities.isEmpty()) {
                context.getClaims().claim("roles", authorities);
            }
        };
    }

    private String getAttribute(Object principalObj, String attributeName) {
        if (principalObj instanceof OAuth2User oauth2User) {
            return oauth2User.getAttribute(attributeName);
        } else if (principalObj instanceof Jwt jwt) {
            return jwt.getClaim(attributeName);
        }
        return null;
    }

    private String getSessionId(Authentication authentication) {
        if (authentication.getDetails() instanceof WebAuthenticationDetails details) {
            return details.getSessionId();
        }
        return null;
    }

    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> customOAuth2UserService(
            PasswordEncoder passwordEncoder,
            ClientService clientService) {
        return userRequest -> {
            OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
            OAuth2User oAuth2User = delegate.loadUser(userRequest);

            String clientId = oAuth2User.getName();
            String provider = userRequest.getClientRegistration().getRegistrationId();

            String clientName = oAuth2User.getAttribute("name");
            if (StringUtils.isBlank(clientName)) {
                clientName = oAuth2User.getAttribute("sub");
            } else if (StringUtils.isBlank(clientName)) {
                clientName = oAuth2User.getAttribute("fullname");
            } else if (StringUtils.isBlank(clientName)) {
                clientName = oAuth2User.getAttribute("preferred_username");
            } else if (StringUtils.isBlank(clientName)) {
                clientName = oAuth2User.getAttribute("email");
            } else if (StringUtils.isBlank(clientName)) {
                clientName = oAuth2User.getName();
            } else if (StringUtils.isBlank(clientName)) {
                clientName = "unknown";
            }

            // Ensure the cache is cleared so that any changes will be persisted.
            Client clearClient = new Client();
            clearClient.setClientId(clientId);
            clientService.clearCache(clearClient);

            // 🧩 Create or fetch your internal user
            String finalClientName = clientName;
            Client client = clientService.findByClientId(clientId)
                    .orElseGet(() -> {
                        ClientRequest clientRequest = new ClientRequest(
                                provider,
                                clientId,
                                passwordEncoder.encode(UUID.randomUUID().toString()),
                                finalClientName,
                                appConfig.getDefaultResources(),
                                false,
                                true,
                                false,
                                appConfig.getDefaultAuthenticationMethods(),
                                appConfig.getDefaultGrantTypes(),
                                appConfig.getDefaultScopes(),
                                appConfig.getDefaultRoles(),
                                appConfig.getDefaultRedirectUris(),
                                appConfig.getDefaultPostLogoutRedirectUris()
                        );
                        return clientService.save(clientRequest, null);
                    });

            // 🛡️ Build authorities based on client scopes/roles
            List<GrantedAuthority> authorities = new ArrayList<>();

            Arrays.asList(client.getScopes().split(",")).forEach(scope ->
                    authorities.add(new SimpleGrantedAuthority(scope))
            );
            clientService.getClientSettings(client, "roles").forEach(role ->
                    authorities.add(new SimpleGrantedAuthority(role))
            );

            String nameAttributeKey = userRequest
                    .getClientRegistration()
                    .getProviderDetails()
                    .getUserInfoEndpoint()
                    .getUserNameAttributeName(); // <- This is what you're looking f
            return new DefaultOAuth2User(
                    authorities,
                    oAuth2User.getAttributes(),
                    nameAttributeKey // or "email", or whatever your user ID claim is
            );
        };
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new CustomJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public BytesEncryptor bytesEncryptor(@Value("${jwt.secret.key}") String secret) {
        SecretKey secretKey = new SecretKeySpec(Base64.getDecoder().decode(secret.trim()), "AES");
        BytesKeyGenerator ivGenerator = KeyGenerators.secureRandom(12);
        return new AesBytesEncryptor(secretKey, ivGenerator, AesBytesEncryptor.CipherAlgorithm.GCM);
    }

}
