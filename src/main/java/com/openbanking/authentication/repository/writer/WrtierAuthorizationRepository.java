package com.openbanking.authentication.repository.writer;

import com.openbanking.authentication.entities.Authorization;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface WrtierAuthorizationRepository extends JpaRepository<Authorization, String> {

    @Modifying
    @Transactional
    @Query("""
    DELETE FROM Authorization a
    WHERE
        (a.authorizationCodeExpiresAt IS NOT NULL AND a.authorizationCodeExpiresAt < :expiryTime)
        AND (a.accessTokenExpiresAt IS NULL OR a.accessTokenExpiresAt < :expiryTime)
        AND (a.refreshTokenExpiresAt IS NULL OR a.refreshTokenExpiresAt < :expiryTime)
        AND (a.oidcIdTokenExpiresAt IS NULL OR a.oidcIdTokenExpiresAt < :expiryTime)
    """)
    int deleteExpiredAuthorizations(@Param("expiryTime") Instant expiryTime);

}
