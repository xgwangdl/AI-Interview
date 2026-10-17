package com.guangge.Interview.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, UUID> {
    boolean existsByOwnerIdAndKindAndSentAtAfter(String ownerId, String kind, Instant after);
}
