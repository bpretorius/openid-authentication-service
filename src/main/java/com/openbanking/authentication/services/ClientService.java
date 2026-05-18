package com.openbanking.authentication.services;

import com.openbanking.authentication.config.AppConfig;
import com.openbanking.authentication.dto.ClientRequest;
import com.openbanking.authentication.dto.ClientResponse;
import com.openbanking.authentication.entities.Client;
import com.openbanking.authentication.exception.BadRequestException;
import com.openbanking.authentication.repository.reader.ReaderClientPaginationRepository;
import com.openbanking.authentication.repository.reader.ReaderClientRepository;
import com.openbanking.authentication.repository.writer.WriterClientRepository;
import com.openbanking.authentication.util.Constants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import jakarta.persistence.criteria.Predicate;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class ClientService implements UserDetailsService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AppConfig appConfig;
    private final CommonService commonService;
    private final ReaderClientRepository readerClientRepository;
    private final WriterClientRepository writerClientRepository;
    private final ReaderClientPaginationRepository readerClientPaginationRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String CLIENTID_DTO = "findClientByClientId";
    private final String ID_DTO = "findClientById";

    public ClientService(RedisTemplate<String, Object> redisTemplate, AppConfig appConfig, CommonService commonService, ReaderClientRepository readerClientRepository, WriterClientRepository writerClientRepository, ReaderClientPaginationRepository readerClientPaginationRepository, PasswordEncoder passwordEncoder) {
        this.redisTemplate = redisTemplate;
        this.appConfig = appConfig;
        this.readerClientRepository = readerClientRepository;
        this.writerClientRepository = writerClientRepository;
        this.commonService = commonService;
        this.readerClientPaginationRepository = readerClientPaginationRepository;
        this.passwordEncoder = passwordEncoder;
        ClassLoader classLoader = ClientService.class.getClassLoader();
        List<Module> securityModules = SecurityJackson2Modules.getModules(classLoader);
        this.objectMapper.registerModules(securityModules);
        this.objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Client client = this.findByClientId(username)
                .orElseThrow(() -> new BadRequestException(Constants.BUSINESS_ERROR.CLIENT_NOT_FOUND.toString()));

        List<GrantedAuthority> authorities = new ArrayList<>();
        List<String> roles = getClientSettings(client, "roles");
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
        List<String> scopes = client.getScopes() == null ? List.of() : Arrays.asList(client.getScopes().split(","));
        for (String scope : scopes) {
            authorities.add(new SimpleGrantedAuthority(scope));
        }

        return new User(client.getClientId(), client.getClientSecret(), authorities);
    }

    public Optional<Client> findByClientId(String clientId) {
        Optional<Client> client = Optional.empty();
        Client cachedClient = (Client) redisTemplate.opsForValue().get(CLIENTID_DTO + clientId);
        if (cachedClient == null) {
            client = this.readerClientRepository.findByClientId(clientId);
            client.ifPresent(this::reloadCache);
        } else {
            client = Optional.of(cachedClient);
        }

        return client;
    }

    public Optional<Client> findById(String id) {
        Optional<Client> client = Optional.empty();
        Client cachedClient = (Client) redisTemplate.opsForValue().get(ID_DTO + id);
        if (cachedClient == null) {
            client = this.readerClientRepository.findById(id);
            client.ifPresent(this::reloadCache);
        } else {
            client = Optional.of(cachedClient);
        }

        return client;
    }

    public Client save(ClientRequest dto, String id) {

        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .id(StringUtils.isNotBlank(id) ? id : UUID.randomUUID().toString())
                .clientId(dto.clientId())
                .clientSecret(StringUtils.isNotBlank(dto.clientSecret()) ? passwordEncoder.encode(dto.clientSecret()) : null)
                .clientName(dto.clientName())
                .clientAuthenticationMethods(methods -> dto.authenticationMethods().forEach(m -> methods.add(new ClientAuthenticationMethod(m))))
                .authorizationGrantTypes(grants -> dto.grantTypes().forEach(g -> grants.add(new AuthorizationGrantType(g))))
                .scopes(scopes -> scopes.addAll(dto.scopes()))
                .redirectUris(redirectUris -> {
                    if (dto.redirectUris() != null) {
                        redirectUris.addAll(dto.redirectUris());
                    }
                })
                .postLogoutRedirectUris(postLogoutRedirectUris -> {
                    if (dto.postLogoutRedirectUris() != null) {
                        postLogoutRedirectUris.addAll(dto.postLogoutRedirectUris());
                    }
                })
                .clientIdIssuedAt(Instant.now())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(appConfig.getAccessTokenTTL()))
                        .refreshTokenTimeToLive(Duration.ofDays(appConfig.getRefreshTokenTTL()))
                        .reuseRefreshTokens(true)
                        .build())
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(dto.requireAuthorizationConsent())
                        .requireProofKey(dto.requireProofKey())
                        .setting("fullName", dto.clientName())
                        .setting("mfaEnabled", dto.mfaEnabled())
                        .setting("resource_indicator", dto.aud()!= null ? String.join(",", dto.aud()): "")
                        .setting("roles", dto.roles()!= null ? String.join(",", dto.roles()): "")
                        .setting("provider", dto.provider() != null ? dto.provider() : "internal")
                        .build())
                .build();

        return this.save(registeredClient);
    }

    private Client save(RegisteredClient registeredClient) {
        Assert.notNull(registeredClient, "registeredClient cannot be null");
        Client client = commonService.toEntity(registeredClient, objectMapper);
        return this.save(client);
    }

    public Client save(Client client) {
        client = this.writerClientRepository.save(client);
        reloadCache(client);
        return client;
    }

    public void delete(String id) {
        Client client = readerClientRepository.findById(id).orElseThrow(() -> new BadRequestException(Constants.BUSINESS_ERROR.CLIENT_NOT_FOUND.toString()));
        writerClientRepository.delete(client);
        clearCache(client);
    }

    public ClientResponse getClient(String id) {
        Client client = readerClientRepository.findById(id).orElseThrow(() -> new BadRequestException(Constants.BUSINESS_ERROR.CLIENT_NOT_FOUND.toString()));
        reloadCache(client);
        return toResponse(client);
    }

    public List<ClientResponse> list() {
        return readerClientRepository.findAll().stream().map(this::toResponse).toList();
    }

    public Page<ClientResponse> getClients(
            String clientId,
            String clientName,
            LocalDateTime clientIdIssuedAt,
            LocalDateTime clientSecretExpiresAt,
            int page,
            int size,
            String sortBy,
            String sortDirection
    ) {
        Pageable pageable = PageRequest.of(page, size,
                sortDirection.equalsIgnoreCase("desc") ?
                        Sort.by(sortBy).descending() :
                        Sort.by(sortBy).ascending());

        Specification<Client> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (clientId != null) {
                predicates.add(cb.like(cb.lower(root.get("clientId")), "%" + clientId.toLowerCase() + "%"));
            }
            if (clientName != null) {
                predicates.add(cb.like(cb.lower(root.get("clientName")), "%" + clientName.toLowerCase() + "%"));
            }
            if (clientIdIssuedAt != null) {
                predicates.add(cb.equal(root.get("clientIdIssuedAt"), clientIdIssuedAt));
            }
            if (clientSecretExpiresAt != null) {
                predicates.add(cb.equal(root.get("clientSecretExpiresAt"), clientSecretExpiresAt));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return readerClientPaginationRepository.findAll(spec, pageable)
                .map(this::toResponse);
    }

    private ClientResponse toResponse(Client e) {
        String provider = (String) getClientSetting(e, "provider");
        Boolean proofKey = (Boolean) getClientSetting(e, "settings.client.require-proof-key");
        Boolean requireConsent = (Boolean) getClientSetting(e, "settings.client.require-authorization-consent");
        return new ClientResponse(
                provider,
                e.getId(),
                e.getClientId(),
                e.getClientName(),
                getClientSettings(e, "resource_indicator"),
                e.isMfaEnabled(),
                proofKey,
                requireConsent,
                Arrays.asList(e.getClientAuthenticationMethods().split(",")),
                Arrays.asList(e.getAuthorizationGrantTypes().split(",")),
                Arrays.asList(e.getScopes().split(",")),
                getClientSettings(e, "roles"),
                e.getRedirectUris() == null ? List.of() : Arrays.asList(e.getRedirectUris().split(",")),
                e.getPostLogoutRedirectUris() == null ? List.of() : Arrays.asList(e.getPostLogoutRedirectUris().split(","))
        );
    }

    public List<String> getClientSettings(Client client, String resource) {
        Map<String, Object> map = null;
        try {
            map = objectMapper.readValue(client.getClientSettings(), Map.class);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }

        String rolesStr = (String) map.get(resource);
        if (StringUtils.isBlank(rolesStr)) {
            return new ArrayList<String>();
        }
        return Arrays.stream(rolesStr.split(","))
                .map(String::trim)
                .toList();

    }

    private Object getClientSetting(Client client, String resource) {
        Map<String, Object> map = null;
        try {
            map = objectMapper.readValue(client.getClientSettings(), Map.class);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }

        return map.get(resource);

    }

    public void reloadCache(Client client) {
        if (client.getId() != null) {
            redisTemplate.opsForValue().set(ID_DTO + client.getId(), client, appConfig.getClientEntityTTL(), TimeUnit.SECONDS);
        }
        if (client.getClientId() != null) {
            redisTemplate.opsForValue().set(CLIENTID_DTO + client.getClientId(), client, appConfig.getClientEntityTTL(), TimeUnit.SECONDS);
        }
    }

    public void clearCache(Client client) {
        if (client.getId() != null) {
            redisTemplate.delete(ID_DTO + client.getId());
        }
        if (client.getClientId() != null) {
            redisTemplate.delete(CLIENTID_DTO + client.getClientId());
        }
    }
}
