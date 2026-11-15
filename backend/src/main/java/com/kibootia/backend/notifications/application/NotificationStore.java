package com.kibootia.backend.notifications.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Where notifications are kept. The use cases depend on this port, never on JPA (ADR 0003). */
public interface NotificationStore {

	/** Writes the notifications; one that was already written for the same person, kind and event is skipped. */
	void insertAll(List<NewNotification> notifications);

	/** The notifications of one person, newest first, only the unread ones when asked. */
	Page<Notification> search(UUID recipient, boolean unreadOnly, Pageable pageable);

	long countUnread(UUID recipient);

	/** The notification, if it is this person's. */
	Optional<Notification> findOf(UUID recipient, UUID id);

	/** Marks it read at that time if it is not already; returns the notification as it is then. */
	Notification markRead(UUID id, java.time.Instant at);

	/** Marks every unread notification of the person read; returns how many it marked. */
	int markAllRead(UUID recipient, java.time.Instant at);

}
