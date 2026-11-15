package com.kibootia.backend.notifications.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.errors.ApiException;

/**
 * Reading and marking one's own notifications. Every use case acts only on the signed-in person's own notifications, so none
 * needs a permission of its own; another person's notification is not found.
 */
@Service
public class NotificationService {

	public static final String NOT_FOUND = "notifications.notification_not_found";

	private static final String OWN_ONLY = "a person reads and marks only their own notifications";

	private final NotificationStore notifications;

	private final Authorization authorization;

	private final Clock clock;

	NotificationService(NotificationStore notifications, Authorization authorization, Clock clock) {
		this.notifications = notifications;
		this.authorization = authorization;
		this.clock = clock;
	}

	/** The caller's notifications, newest first, the unread ones only when asked. */
	@Transactional(readOnly = true)
	@NoAuthorization(OWN_ONLY)
	public Page<Notification> list(boolean unreadOnly, Pageable pageable) {
		return this.notifications.search(this.authorization.currentUserId(), unreadOnly, pageable);
	}

	@Transactional(readOnly = true)
	@NoAuthorization(OWN_ONLY)
	public long unreadCount() {
		return this.notifications.countUnread(this.authorization.currentUserId());
	}

	/** Marks one read; reading it again changes nothing. */
	@Transactional
	@NoAuthorization(OWN_ONLY)
	public Notification markRead(UUID id) {
		this.notifications.findOf(this.authorization.currentUserId(), id)
			.orElseThrow(() -> ApiException.notFound(NOT_FOUND, "There is no notification with that ID."));
		return this.notifications.markRead(id, this.clock.instant());
	}

	/** Marks every unread notification of the caller read; returns how many it marked. */
	@Transactional
	@NoAuthorization(OWN_ONLY)
	public int markAllRead() {
		return this.notifications.markAllRead(this.authorization.currentUserId(), this.clock.instant());
	}

}
