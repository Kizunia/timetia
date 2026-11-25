package com.kibootia.backend.notifications.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kibootia.backend.issues.IssueMentioned;
import com.kibootia.backend.notifications.domain.NotificationType;
import com.kibootia.backend.platform.events.DurableEventListener;
import com.kibootia.backend.rescheduling.TicketChanged;

/**
 * Turns the events other modules publish into notifications. The methods are not public, so they are not use cases: each is
 * reached only through its event, as the system, in the event's tenant, once per event even if the delivery repeats, and
 * a handler that fails is retried until it succeeds.
 */
@Component
class NotificationEvents {

	private final NotificationStore notifications;

	private final Audience audience;

	NotificationEvents(NotificationStore notifications, Audience audience) {
		this.notifications = notifications;
		this.audience = audience;
	}

	/** Something happened to a ticket: the people it concerns are told. */
	@DurableEventListener
	void on(TicketChanged event) {
		NotificationType type = Audience.typeOf(event.kind());
		write(this.audience.forTicket(event), type, event.ticketId(), null, event.actorUserId(), event.context().eventId());
	}

	/** Faculty were mentioned on an issue: each is told. */
	@DurableEventListener
	void on(IssueMentioned event) {
		write(event.mentionedUserIds(), NotificationType.ISSUE_MENTION, null, event.issueId(), event.openerUserId(),
				event.context().eventId());
	}

	private void write(Set<UUID> people, NotificationType type, UUID ticketId, UUID issueId, UUID actor, UUID eventId) {
		List<NewNotification> toWrite = people.stream()
			.map((person) -> new NewNotification(person, type, ticketId, issueId, actor, eventId))
			.toList();
		this.notifications.insertAll(toWrite);
	}

}
