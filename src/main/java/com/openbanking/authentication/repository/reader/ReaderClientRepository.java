package com.openbanking.authentication.repository.reader;

import com.openbanking.authentication.entities.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReaderClientRepository extends JpaRepository<Client, String> {

    Optional<Client> findByClientId(String clientId);
}