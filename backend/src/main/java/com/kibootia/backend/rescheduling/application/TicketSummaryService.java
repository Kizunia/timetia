package com.kibootia.backend.rescheduling.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.rescheduling.TicketSummaries;

/** Answers {@link TicketSummaries} with one read of the changes of all the tickets asked about. */
@Service
class TicketSummaryService implements TicketSummaries {

	private final TicketStore tickets;

	TicketSummaryService(TicketStore tickets) {
		this.tickets = tickets;
	}

	@Override
	@Transactional(readOnly = true)
	@NoAuthorization("only the kind and the lecture of tickets, used to describe the caller's own notifications")
	public Map<UUID, TicketSummary> summariesOf(Collection<UUID> ticketIds) {
		if (ticketIds.isEmpty()) {
			return Map.of();
		}
		Set<UUID> ids = Set.copyOf(ticketIds);
		Map<UUID, List<TicketChange>> changes = this.tickets.changesOf(ids);
		Map<UUID, TicketSummary> summaries = new HashMap<>();
		for (UUID id : ids) {
			this.tickets.find(id).ifPresent((ticket) -> {
				List<TicketChange> own = changes.getOrDefault(id, List.of());
				UUID lectureId = own.isEmpty() ? null : own.get(0).occurrenceId();
				summaries.put(id, new TicketSummary(id, ticket.type().name(), lectureId));
			});
		}
		return Map.copyOf(summaries);
	}

}
