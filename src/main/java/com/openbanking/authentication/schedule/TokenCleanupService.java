package com.openbanking.authentication.schedule;

import com.openbanking.authentication.repository.writer.WrtierAuthorizationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class TokenCleanupService {

    @Value(value = "${app.token_expiry_time_cleanup_hours}")
    private int tokenExpiryTimeCleanupHours;

    private final WrtierAuthorizationRepository wrtierAuthorizationRepository;

    public TokenCleanupService(WrtierAuthorizationRepository wrtierAuthorizationRepository) {
        this.wrtierAuthorizationRepository = wrtierAuthorizationRepository;
    }

    // Runs at the top of every hour (e.g., 01:00, 02:00, 03:00, ...)
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredTokens() {
        // Get the current time 24 hours ago
        LocalDateTime localDateTime = LocalDateTime.now().minusHours(tokenExpiryTimeCleanupHours);

        // Convert LocalDateTime to Instant (UTC time zone)
        Instant expiryTime = localDateTime.atZone(ZoneId.systemDefault()).toInstant();

        // Pass the Instant to the repository
        int deleted = wrtierAuthorizationRepository.deleteExpiredAuthorizations(expiryTime);
        System.out.println("Deleted " + deleted + " expired authorizations.");
    }
}

