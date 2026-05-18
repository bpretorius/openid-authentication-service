package com.openbanking.authentication.config;

import com.openbanking.authentication.mfa.MFAAuthenticationEntryPoint;
import com.openbanking.authentication.mfa.MFAHandler;
import com.openbanking.authentication.services.*;
import com.openbanking.authentication.services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.function.Function;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private RedisAuthorizationRequestRepository redisAuthorizationRequestRepository;

    @Autowired
    private ClientService clientService;

    @Autowired
    JpaOAuth2AuthorizationService jpaOAuth2AuthorizationService;

    @Autowired
    RedisSecurityContextRepository redisSecurityContextRepository;

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http, ClientRegistrationRepository repo)
            throws Exception {

        Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper = (context) -> {
            OidcUserInfoAuthenticationToken authentication = context.getAuthentication();
            JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication.getPrincipal();

            return new OidcUserInfo(principal.getToken().getClaims());
        };

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
                .securityContext(securityContext ->
                        securityContext.securityContextRepository(redisSecurityContextRepository)
                )
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, (authorizationServer) ->
                        authorizationServer
                                .oidc((oidc) -> oidc
                                        .userInfoEndpoint((userInfo) -> userInfo
                                                .userInfoMapper(userInfoMapper)
                                        )
                                )
                )
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers("/login", "/logout").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(new DynamicAuthorizationRequestResolver(repo))
                                .authorizationRequestRepository(redisAuthorizationRequestRepository)
                        )
                        .successHandler(new MFAHandler("/authenticator", clientService, jpaOAuth2AuthorizationService))
                        .failureHandler(new SimpleUrlAuthenticationFailureHandler("/login?error=true"))
                )
                .logout(logout -> logout
                        .logoutUrl("/logout").permitAll() // You can customize this
                        .deleteCookies("JSESSIONID")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                )
                .exceptionHandling((exceptions) -> exceptions
                        .authenticationEntryPoint(new MFAAuthenticationEntryPoint("/login", jpaOAuth2AuthorizationService))
                );

        return http.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ClientRegistrationRepository repo) throws Exception {
        http
                //.addFilterBefore(new TenantFilter(), UsernamePasswordAuthenticationFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .securityContext(securityContext ->
                        securityContext.securityContextRepository(redisSecurityContextRepository)
                )
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/login/**").permitAll()
                        .requestMatchers("/sso/**").permitAll()
                        .requestMatchers("/registration/**", "/authenticator/**").authenticated()
                        .requestMatchers("/oauth2/**").permitAll()
                        .requestMatchers("/registration/**", "/authenticator/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/clients/**").hasAnyAuthority("ROLE_create:idp", "ROLE_super_user:idp")
                        .requestMatchers(HttpMethod.GET, "/api/clients/**").hasAnyAuthority("ROLE_read:idp", "ROLE_super_user:idp")
                        .requestMatchers(HttpMethod.PUT, "/api/clients/**").hasAnyAuthority("ROLE_update:idp", "ROLE_super_user:idp")
                        .requestMatchers(HttpMethod.DELETE, "/api/clients/**").hasAnyAuthority("ROLE_delete:idp", "ROLE_super_user:idp")
                        .anyRequest().permitAll()
                )
                .formLogin(formLogin -> formLogin
                        .loginPage("/login").permitAll()
                        .failureUrl("/login?error=true").permitAll()
                        .successHandler(new MFAHandler("/authenticator", clientService, jpaOAuth2AuthorizationService))
                        .failureHandler(new SimpleUrlAuthenticationFailureHandler("/login?error=true"))
                )
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(new DynamicAuthorizationRequestResolver(repo))
                                .authorizationRequestRepository(redisAuthorizationRequestRepository)
                        )
                        .successHandler(new MFAHandler("/authenticator", clientService, jpaOAuth2AuthorizationService))
                        .failureHandler(new SimpleUrlAuthenticationFailureHandler("/login?error=true"))
                )
                .logout(logout -> logout
                        .logoutUrl("/logout").permitAll() // You can customize this
                        .deleteCookies("JSESSIONID")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
                )
                .exceptionHandling((exceptions) -> exceptions
                        .authenticationEntryPoint(new MFAAuthenticationEntryPoint("/login", jpaOAuth2AuthorizationService))
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*")); // Allow all origins
        configuration.setAllowedMethods(List.of("*")); // Allow all HTTP methods
        configuration.setAllowedHeaders(List.of("*")); // Allow all headers
        configuration.setAllowCredentials(false); // Required for wildcard origins

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private JwtAuthenticationConverter jwtAuthConverter() {
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthorityPrefix("ROLE_"); // Ensures 'auth_admin' becomes 'ROLE_auth_admin'
        rolesConverter.setAuthoritiesClaimName("roles"); // Map from 'roles' claim in JWT

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(rolesConverter);
        return converter;
    }
}
