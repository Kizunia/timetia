package com.kibootia.backend.notifications.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.kibootia.backend.notifications.application.NewNotification;
import com.kibootia.backend.notifications.domain.NotificationType;
import com.kibootia.backend.platform.persistence.BaseEntity;

/** The table row of a notification. Persistence only (ADR 0004). Only the time it was read ever changes. */
@Entity
@Table(name = "notification")
class NotificationEntity extends BaseEntity {

	@Column(name = "recipient_user_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID recipientUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "notification_type", nullable = false, updatable = false, length = 20)
	private NotificationType type;

	@Column(name = "ticket_id", updatable = false, columnDefinition = "binary(16)")
	private UUID ticketId;

	@Column(name = "issue_id", updatable = false, columnDefinition = "binary(16)")
	private UUID issueId;

	@Column(name = "actor_user_id", updatable = false, columnDefinition = "binary(16)")
	private UUID actorUserId;

	@Column(name = "source_event_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID sourceEventId;

	@Column(name = "read_at")
	private Instant readAt;

	protected NotificationEntity() {
	}

	NotificationEntity(NewNotification notification) {
		this.recipientUserId = notification.recipientUserId();
		this.type = notification.type();
		this.ticketId = notification.ticketId();
		this.issueId = notification.issueId();
		this.actorUserId = notification.actorUserId();
		this.sourceEventId = notification.sourceEventId();
	}

	void markRead(Instant at) {
		if (this.readAt == null) {
			this.readAt = at;
		}
	}

	UUID getRecipientUserId() {
		return this.recipientUserId;
	}

	NotificationType getType() {
		return this.type;
	}

	UUID getTicketId() {
		return this.ticketId;
	}

	UUID getIssueId() {
		return this.issueId;
	}

	UUID getActorUserId() {
		return this.actorUserId;
	}

	Instant getReadAt() {
		return this.readAt;
	}

}
