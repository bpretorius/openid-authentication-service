package com.openbanking.authentication.repository.writer;

import com.openbanking.authentication.entities.JwkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WriterJwkRepository extends JpaRepository<JwkEntity, String> {
}

