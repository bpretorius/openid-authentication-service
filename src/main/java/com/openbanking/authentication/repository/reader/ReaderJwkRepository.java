package com.openbanking.authentication.repository.reader;

import com.openbanking.authentication.entities.JwkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReaderJwkRepository extends JpaRepository<JwkEntity, String> {
}

