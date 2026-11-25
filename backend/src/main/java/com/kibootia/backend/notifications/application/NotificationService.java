package com.kibootia.backend.notifications.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.directory.UserDirectory;
import com.kibootia.backend.platform.directory.UserSummary;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.rescheduling.TicketSummaries;
import com.kibootia.backend.rescheduling.TicketSummaries.TicketSummary;
import com.kibootia.backend.rescheduling.TicketVisibility;

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

	private final UserDirectory users;

	private final TicketSummaries tickets;

	private final TicketVisibility visibility;

	NotificationService(NotificationStore notifications, Authorization authorization, Clock clock, UserDirectory users,
			TicketSummaries tickets, TicketVisibility visibility) {
		this.notifications = notifications;
		this.authorization = authorization;
		this.clock = clock;
		this.users = users;
		this.tickets = tickets;
		this.visibility = visibility;
	}

	/** The caller's notifications, newest first, the unread ones only when asked. */
	@Transactional(readOnly = true)
	@NoAuthorization(OWN_ONLY)
	public Page<NotificationView> list(boolean unreadOnly, Pageable pageable) {
		Page<Notification> page = this.notifications.search(this.authorization.currentUserId(), unreadOnly, pageable);
		Map<UUID, NotificationView> views = viewsOf(page.getContent());
		return page.map((notification) -> views.get(notification.id()));
	}

	@Transactional(readOnly = true)
	@NoAuthorization(OWN_ONLY)
	public long unreadCount() {
		return this.notifications.countUnread(this.authorization.currentUserId());
	}

	/** Marks one read; reading it again changes nothing. */
	@Transactional
	@NoAuthorization(OWN_ONLY)
	public NotificationView markRead(UUID id) {
		this.notifications.findOf(this.authorization.currentUserId(), id)
			.orElseThrow(() -> ApiException.notFound(NOT_FOUND, "There is no notification with that ID."));
		Notification read = this.notifications.markRead(id, this.clock.instant());
		return viewsOf(List.of(read)).get(read.id());
	}

	/** Marks every unread notification of the caller read; returns how many it marked. */
	@Transactional
	@NoAuthorization(OWN_ONLY)
	public int markAllRead() {
		return this.notifications.markAllRead(this.authorization.currentUserId(), this.clock.instant());
	}

	/**
	 * Adds the actor's name, the ticket's kind and lecture, and whether the caller may open it. Names and tickets are one
	 * lookup each for all of them; visibility is asked once for each ticket on the page, with the rules for reading it.
	 */
	private Map<UUID, NotificationView> viewsOf(List<Notification> found) {
		Set<UUID> actorIds = found.stream().map(Notification::actorUserId).filter(Objects::nonNull).collect(Collectors.toSet());
		Set<UUID> ticketIds = found.stream().map(Notification::ticketId).filter(Objects::nonNull).collect(Collectors.toSet());
		Map<UUID, UserSummary> actors = this.users.find(actorIds);
		Map<UUID, TicketSummary> summaries = this.tickets.summariesOf(ticketIds);
		Set<UUID> visible = ticketIds.stream().filter(this.visibility::visibleToCaller).collect(Collectors.toSet());
		return found.stream().collect(Collectors.toMap(Notification::id, (notification) -> {
			UserSummary actor = (notification.actorUserId() == null) ? null : actors.get(notification.actorUserId());
			TicketSummary ticket = (notification.ticketId() == null) ? null : summaries.get(notification.ticketId());
			return new NotificationView(notification, (actor == null) ? null : actor.displayName(),
					(ticket == null) ? null : ticket.type(), (ticket == null) ? null : ticket.lectureId(),
					notification.ticketId() != null && visible.contains(notification.ticketId()));
		}));
	}

}
