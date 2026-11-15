package com.kibootia.backend.notifications.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Notifications in MySQL. Entity queries and the bulk update below are filtered by tenant on their own (ADR 0028). */
interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

	@Query("""
			select n from NotificationEntity n
			where n.recipientUserId = :recipient and (:unreadOnly = false or n.readAt is null)
			""")
	Page<NotificationEntity> ofRecipient(UUID recipient, boolean unreadOnly, Pageable pageable);

	long countByRecipientUserIdAndReadAtIsNull(UUID recipient);

	boolean existsBySourceEventIdAndRecipientUserIdAndType(UUID sourceEventId, UUID recipientUserId,
			com.kibootia.backend.notifications.domain.NotificationType type);

	@Modifying
	@Query("update NotificationEntity n set n.readAt = :at where n.recipientUserId = :recipient and n.readAt is null")
	int markAllRead(UUID recipient, Instant at);

}
