package com.kibootia.backend.notifications.application;

import java.time.Instant;
import java.util.UUID;

import com.kibootia.backend.notifications.domain.NotificationType;

/**
 * One message to one person. Exactly what it is about is the ticket or the issue; the interface looks the rest up.
 *
 * @param actorUserId who caused it, or null
 * @param readAt when the person read it; null while unread
 */
public record Notification(UUID id, UUID recipientUserId, NotificationType type, UUID ticketId, UUID issueId, UUID actorUserId,
		Instant createdAt, Instant readAt) {

	public boolean read() {
		return this.readAt != null;
	}

}
