package com.kibootia.backend.rescheduling;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * What another module may say about tickets it already holds the ids of, without reading the tables of this module. The
 * notifications module uses it to describe a person's own notifications: they were told about the ticket, so its kind and
 * the lecture it is about are no secret from them. Nothing else of the ticket is given away.
 */
public interface TicketSummaries {

	/** The tickets among these ids, by id; one that does not exist is absent. */
	Map<UUID, TicketSummary> summariesOf(Collection<UUID> ticketIds);

	/**
	 * @param type the kind of ticket, as the API names it
	 * @param lectureId the lecture the ticket's first change is about; null when there is none yet (an extra lecture)
	 */
	record TicketSummary(UUID ticketId, String type, UUID lectureId) {

	}

}
