package com.kibootia.backend.rescheduling.application;

import java.util.UUID;

import com.kibootia.backend.rescheduling.domain.ChangeOperation;
import com.kibootia.backend.timetable.LectureSlot;

/**
 * What a ticket does to one lecture (data model {@code TicketChangeItem}).
 *
 * @param sourceRevision the state of the lecture the change was planned against; if it has changed since, the change is
 *        refused
 * @param target where a move puts the lecture; null for a cancel
 * @param reason why a lecture is cancelled; required for a cancel
 * @param before where the lecture was before the change was applied, kept so that it can be undone; null until applied
 */
public record TicketChange(UUID id, UUID ticketId, ChangeOperation operation, UUID sourceOccurrenceId, long sourceRevision,
		LectureSlot target, String reason, LectureSlot before) {

}
