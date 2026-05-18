package com.openbanking.authentication.repository.writer;

import com.openbanking.authentication.entities.AuthorizationConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WriterAuthorizationConsentRepository extends JpaRepository<AuthorizationConsent, AuthorizationConsent.AuthorizationConsentId> {

    void deleteByRegisteredClientIdAndPrincipalName(String registeredClientId, String principalName);
}
