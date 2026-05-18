package com.openbanking.authentication.repository.writer;

import com.openbanking.authentication.entities.CustomerIdpConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WriterCustomerIdpRepository extends JpaRepository<CustomerIdpConfig, Integer> {

}
