package com.kibootia.backend.rescheduling.application;

import java.time.Instant;
import java.util.UUID;

import com.kibootia.backend.rescheduling.domain.ChangeOperation;
import com.kibootia.backend.rescheduling.domain.TicketType;
import com.kibootia.backend.timetable.LectureSlot;

/** What is needed to raise a ticket: the ticket, and the one change it makes. It starts as a draft. */
public record NewTicket(TicketType type, UUID authorUserId, UUID actingFacultyId, String description, LectureRef lecture,
		Instant expiresAt, Change change) {

	/** The change a new or edited ticket makes to a lecture. */
	public record Change(ChangeOperation operation, UUID sourceOccurrenceId, long sourceRevision, LectureSlot target,
			String reason) {

	}

}
