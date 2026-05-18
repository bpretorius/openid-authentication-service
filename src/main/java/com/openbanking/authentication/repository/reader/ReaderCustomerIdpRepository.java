package com.openbanking.authentication.repository.reader;

import com.openbanking.authentication.entities.CustomerIdpConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReaderCustomerIdpRepository extends JpaRepository<CustomerIdpConfig, Integer> {

    Optional<CustomerIdpConfig> findByTenantIdIgnoreCase(String tenantId);
    Optional<CustomerIdpConfig> findByRegistrationIdIgnoreCase(String tenantId);



    @Query("""
              SELECT c FROM CustomerIdpConfig c
              WHERE (:tenantId IS NULL OR LOWER(c.tenantId) LIKE LOWER(CONCAT('%', CAST(:tenantId AS java.lang.String), '%')))
                AND (:registrationId IS NULL OR LOWER(c.registrationId) LIKE LOWER(CONCAT('%', CAST(:registrationId AS java.lang.String), '%')))
            """)
    Page<CustomerIdpConfig> findByFilters(
            @Param("tenantId") String tenantId,
            @Param("registrationId") String registrationId,
            Pageable pageable
    );

}
