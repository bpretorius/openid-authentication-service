package com.openbanking.authentication.controller;

import com.openbanking.authentication.dto.ClientRequest;
import com.openbanking.authentication.dto.ClientResponse;
import com.openbanking.authentication.entities.CustomerIdpConfig;
import com.openbanking.authentication.services.ClientService;
import com.openbanking.authentication.services.DynamicIDPRegistrationRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    @Autowired
    private ClientService clientService;

    @Autowired
    private DynamicIDPRegistrationRepository idpRegistrationRepository;

    @PostMapping
    @Operation(summary = "Register a new user")
    public ResponseEntity<ClientResponse> create(@RequestBody @Valid ClientRequest request) {
        clientService.save(request, null);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing user")
    public ResponseEntity<ClientResponse> update(@PathVariable String id, @RequestBody @Valid ClientRequest request) {
        clientService.save(request, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an existing user")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an existing user")
    public ClientResponse getClient(@PathVariable String id) {
        return clientService.getClient(id);
    }

    @GetMapping
    @Operation(summary = "Get an existing users")
    public Page<ClientResponse> getClients(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String clientName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime clientIdIssuedAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime clientSecretExpiresAt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "clientName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection
    ) {
        return clientService.getClients(clientId, clientName, clientIdIssuedAt, clientSecretExpiresAt, page, size, sortBy, sortDirection);
    }

    @PostMapping("/idp")
    @Operation(summary = "Register a new idp config")
    public ResponseEntity<ClientResponse> createRegisteredIDP(@RequestBody @Valid CustomerIdpConfig request) {
        idpRegistrationRepository.save(request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/idp/{id}")
    @Operation(summary = "Update an existing registered idp config")
    public ResponseEntity<ClientResponse> updateRegisteredIDP(@PathVariable Integer id, @RequestBody @Valid CustomerIdpConfig request) {
        request.setId(id);
        idpRegistrationRepository.save(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/idp/{id}")
    @Operation(summary = "Delete an existing registered idp config")
    public ResponseEntity<Void> deleteIDPConfig(@PathVariable Integer id) {
        idpRegistrationRepository.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/idp/{id}")
    @Operation(summary = "Get anregistered IDP")
    public CustomerIdpConfig getRegisteredIDP(@PathVariable Integer id) {
        Optional<CustomerIdpConfig> config = idpRegistrationRepository.findById(id);
        return config.orElse(null);
    }

    @GetMapping("/idp")
    @Operation(summary = "List registered IDPs")
    public Page<CustomerIdpConfig> getRegisteredIDPs(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String registrationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return idpRegistrationRepository.getConfigs(page, size, tenantId, registrationId);
    }

}


