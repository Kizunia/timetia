package com.kibootia.backend.notifications.web;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kibootia.backend.notifications.domain.NotificationType;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * A notification as the API returns it: what kind, and the ticket or issue it is about. {@code readAt} is empty while it is
 * unread.
 */
public record NotificationResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) NotificationType type,
		@Schema(nullable = true) UUID ticketId,
		@Schema(nullable = true) UUID issueId,
		@Schema(nullable = true, description = "Who caused it.") UUID actorUserId,
		@Schema(requiredMode = REQUIRED) Instant createdAt,
		@Schema(nullable = true) Instant readAt) {

}
