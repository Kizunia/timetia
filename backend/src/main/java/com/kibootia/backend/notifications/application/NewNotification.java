package com.kibootia.backend.notifications.application;

import java.util.UUID;

import com.kibootia.backend.notifications.domain.NotificationType;

/**
 * A notification to write. {@code sourceEventId} is the event it came from: a person is told once of an event even if the
 * event is delivered again.
 */
public record NewNotification(UUID recipientUserId, NotificationType type, UUID ticketId, UUID issueId, UUID actorUserId,
		UUID sourceEventId) {

}
