package com.kibootia.backend.rescheduling.application;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.events.EventContextFactory;
import com.kibootia.backend.rescheduling.TicketChanged;
import com.kibootia.backend.rescheduling.TicketChanged.Kind;

/**
 * Tells the notifications module that something happened to a ticket, in the transaction that made it happen, so the notice
 * is kept until it is delivered and is lost with the change if that rolls back. Package-private: it is a helper of the use
 * cases, not a use case, so it checks no permission.
 */
@Component
class TicketNotices {

	private final TicketSupport support;

	private final ApplicationEventPublisher events;

	private final EventContextFactory eventContexts;

	private final Authorization authorization;

	TicketNotices(TicketSupport support, ApplicationEventPublisher events, EventContextFactory eventContexts,
			Authorization authorization) {
		this.support = support;
		this.events = events;
		this.eventContexts = eventContexts;
		this.authorization = authorization;
	}

	/** Publishes the event for the ticket as it is now, done by the signed-in person. */
	void publish(Kind kind, Ticket ticket) {
		TicketFacts facts = this.support.facts(ticket);
		Set<UUID> faculty = new HashSet<>(facts.teachingFacultyUserIds());
		faculty.addAll(facts.affectedFacultyUserIds());
		this.events.publishEvent(new TicketChanged(ticket.id(), kind, this.authorization.currentUserId(),
				ticket.authorUserId(), facts.requestedReviewerUserIds(), Set.copyOf(faculty), facts.departmentId(),
				facts.divisionId(), facts.batchIds(), facts.sessionType(), this.eventContexts.capture()));
	}

}
