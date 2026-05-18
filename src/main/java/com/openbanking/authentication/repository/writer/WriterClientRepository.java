package com.openbanking.authentication.repository.writer;

import com.openbanking.authentication.entities.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WriterClientRepository extends JpaRepository<Client, String> {

}