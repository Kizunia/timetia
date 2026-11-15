package com.kibootia.backend.timetable.application;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.kibootia.backend.timetable.LectureSlot;

/**
 * Where the dated lectures are kept. The use cases depend on this port, never on JPA (ADR 0003). Only active lectures are
 * ever returned; a lecture a later version replaced stays in the table, marked superseded, and is not seen. A cancelled
 * lecture is active: it is shown, marked, until the term ends.
 */
public interface OccurrenceStore {

	/** Stores the lectures, as the base timetable has them, and returns them in the same order. */
	List<Occurrence> insertAll(List<NewOccurrence> occurrences);

	/**
	 * Marks the division's active lectures whose base date is from {@code from} up to but not including {@code toExclusive}
	 * superseded, because a version that takes effect in that range replaces them, and returns their IDs.
	 */
	List<UUID> supersede(UUID divisionId, LocalDate from, LocalDate toExclusive);

	/** How many of those lectures have a change laid over them, which a new version must not discard. */
	long countChanged(UUID divisionId, LocalDate from, LocalDate toExclusive);

	Optional<Occurrence> find(UUID id);

	/** The active lectures that this timetable entry produced, from this base date on, in date order. */
	List<Occurrence> findOfEntryFrom(UUID entryId, LocalDate baseDate);

	/** The active lectures among these IDs. */
	List<Occurrence> findAll(Collection<UUID> ids);

	/** Like {@link #findAll}, but locks the lectures until the end of the transaction, in a fixed order, so changes queue. */
	List<Occurrence> lockAll(Collection<UUID> ids);

	/** Stores a lecture that a ticket creates, with no timetable entry behind it, and records the ticket. */
	Occurrence insertExtra(NewOccurrence occurrence, UUID ticketId);

	/** Moves the lecture to the slot and records the ticket. */
	Occurrence move(UUID id, LectureSlot slot, UUID ticketId);

	/** Cancels the lecture and records the ticket. */
	Occurrence cancel(UUID id, UUID ticketId);

	/**
	 * Puts the lecture back at the slot with the faculty member teaching it, not cancelled. It then shows as changed by the
	 * ticket only if that is not how the base timetable has it.
	 */
	Occurrence restore(UUID id, LectureSlot slot, UUID facultyId, UUID ticketId);

	/** Puts another faculty member in front of the lecture, without moving it. */
	Occurrence reassign(UUID id, UUID facultyId, UUID ticketId);

	/** A page of the active lectures the query allows, in date and time order unless the page asks otherwise. */
	Page<Occurrence> search(OccurrenceQuery query, Pageable pageable);

}
