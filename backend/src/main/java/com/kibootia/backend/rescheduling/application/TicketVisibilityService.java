package com.kibootia.backend.rescheduling.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.rescheduling.TicketVisibility;

/** Answers the issues module's question whether a ticket is visible to the caller, with the same rules as reading it. */
@Service
public class TicketVisibilityService implements TicketVisibility {

	private final TicketStore tickets;

	private final TicketSupport support;

	private final Authorization authorization;

	TicketVisibilityService(TicketStore tickets, TicketSupport support, Authorization authorization) {
		this.tickets = tickets;
		this.support = support;
		this.authorization = authorization;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean visibleToCaller(UUID ticketId) {
		return this.tickets.find(ticketId).map((ticket) -> {
			TicketFacts facts = this.support.facts(ticket);
			return this.authorization.can(AcademicPermissions.TICKETS_VIEW, facts)
					|| this.authorization.can(AcademicPermissions.TICKETS_VIEW_IN_SCOPE, facts);
		}).orElse(false);
	}

}
