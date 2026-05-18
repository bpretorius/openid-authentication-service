package com.openbanking.authentication.repository.reader;

import com.openbanking.authentication.entities.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ReaderClientPaginationRepository extends JpaRepository<Client, String>, JpaSpecificationExecutor<Client> {
}