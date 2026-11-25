package com.kibootia.backend.timetable;

import java.util.UUID;

/**
 * One reason a change cannot be made.
 *
 * @param occurrenceId the lecture the change concerns
 * @param message what is wrong, in words for the person who planned the change
 * @param conflictingOccurrenceId the lecture that holds what the change needs, when there is one
 * @param conflictingTicketId the ticket that put that lecture there, when a ticket did
 */
public record LectureConflict(ConflictType type, UUID occurrenceId, String message, UUID conflictingOccurrenceId,
		UUID conflictingTicketId) {

}
