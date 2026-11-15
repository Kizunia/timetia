package com.kibootia.backend.notifications.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.kibootia.backend.notifications.application.NewNotification;
import com.kibootia.backend.notifications.application.Notification;
import com.kibootia.backend.notifications.application.NotificationStore;

/** Keeps notifications in MySQL through JPA. */
@Component
class JpaNotificationStore implements NotificationStore {

	private final NotificationRepository notifications;

	JpaNotificationStore(NotificationRepository notifications) {
		this.notifications = notifications;
	}

	@Override
	public void insertAll(List<NewNotification> toWrite) {
		for (NewNotification notification : toWrite) {
			// A person is told once of an event, even if the event is delivered again.
			if (!this.notifications.existsBySourceEventIdAndRecipientUserIdAndType(notification.sourceEventId(),
					notification.recipientUserId(), notification.type())) {
				this.notifications.save(new NotificationEntity(notification));
			}
		}
		this.notifications.flush();
	}

	@Override
	public Page<Notification> search(UUID recipient, boolean unreadOnly, Pageable pageable) {
		return this.notifications.ofRecipient(recipient, unreadOnly, pageable).map(JpaNotificationStore::toModel);
	}

	@Override
	public long countUnread(UUID recipient) {
		return this.notifications.countByRecipientUserIdAndReadAtIsNull(recipient);
	}

	@Override
	public Optional<Notification> findOf(UUID recipient, UUID id) {
		return this.notifications.findById(id)
			.filter((entity) -> entity.getRecipientUserId().equals(recipient))
			.map(JpaNotificationStore::toModel);
	}

	@Override
	public Notification markRead(UUID id, Instant at) {
		NotificationEntity entity = this.notifications.findById(id).orElseThrow();
		entity.markRead(at);
		return toModel(this.notifications.saveAndFlush(entity));
	}

	@Override
	public int markAllRead(UUID recipient, Instant at) {
		return this.notifications.markAllRead(recipient, at);
	}

	private static Notification toModel(NotificationEntity entity) {
		return new Notification(entity.getId(), entity.getRecipientUserId(), entity.getType(), entity.getTicketId(),
				entity.getIssueId(), entity.getActorUserId(), entity.getCreatedAt(), entity.getReadAt());
	}

}
