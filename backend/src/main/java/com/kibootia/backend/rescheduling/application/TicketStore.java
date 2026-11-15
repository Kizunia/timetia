package com.kibootia.backend.rescheduling.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.kibootia.backend.rescheduling.domain.CheckTrigger;
import com.kibootia.backend.timetable.LectureConflict;
import com.kibootia.backend.timetable.LectureSlot;

/**
 * Where tickets, their changes and the conflict checks made on them are kept. The use cases depend on this port, never on
 * JPA (ADR 0003). Reads and writes act on the current tenant's rows.
 */
public interface TicketStore {

	/** Stores a new draft ticket with its change. */
	Ticket insert(NewTicket ticket);

	Optional<Ticket> find(UUID id);

	/** The change a ticket makes. */
	TicketChange changeOf(UUID ticketId);

	/** The changes of these tickets, by ticket, so a page of tickets costs one query and not one each. */
	Map<UUID, TicketChange> changesOf(Collection<UUID> ticketIds);

	/**
	 * Saves the state of a ticket if it is still the version that was read; otherwise fails with an optimistic locking
	 * failure, so two people acting on one ticket cannot both succeed.
	 */
	Ticket update(Ticket ticket);

	/** Replaces the change of a draft, for an edit. */
	TicketChange replaceChange(UUID ticketId, NewTicket.Change change);

	/** Records where the lecture was before its change was applied. */
	void recordBefore(UUID ticketId, LectureSlot before);

	Page<Ticket> search(TicketQuery query, Pageable pageable);

	/** Records that the ticket's conflicts were checked, and what was found; an empty list means it was clear. */
	void recordCheck(UUID ticketId, CheckTrigger trigger, List<LectureConflict> conflicts);

	/** What the latest check of the ticket found; empty when it found nothing or there was none. */
	List<LectureConflict> latestCheck(UUID ticketId);

}
