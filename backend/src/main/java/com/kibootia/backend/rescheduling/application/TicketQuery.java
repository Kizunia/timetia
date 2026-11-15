package com.kibootia.backend.rescheduling.application;

import java.util.Set;
import java.util.UUID;

import com.kibootia.backend.rescheduling.domain.TicketStatus;

/**
 * Which tickets a list asks for: those the caller may see, narrowed by optional filters. The store turns it into one query,
 * so a page and its total are correct (the list is filtered before it is paged).
 *
 * @param status only tickets in this state, or all when null
 * @param lectureId only tickets that concern this lecture, or all when null
 * @param authoredBy only tickets this user wrote, or all when null
 */
public record TicketQuery(TicketStatus status, UUID lectureId, UUID authoredBy, Visibility visibility) {

	/**
	 * Which tickets the caller may see, as conditions on the ticket's own columns. A ticket is visible when any one holds.
	 * The session rule of the model applies to the scopes of a division and a batch: a CR sees the theory tickets of the
	 * division and a BR the lab and tutorial tickets of the batch; a department sees all of its tickets.
	 *
	 * @param everywhere the caller may see every ticket
	 * @param departments tickets of these departments, of any kind
	 * @param theoryOfDivisions theory tickets of these divisions
	 * @param labsOfBatches lab and tutorial tickets of these batches
	 * @param writtenBy tickets this user wrote, or null
	 * @param teachingFaculty tickets about lectures these faculty members teach
	 * @param reviewing tickets this user is asked to review, in the ticket's current revision, or null
	 */
	public record Visibility(boolean everywhere, Set<UUID> departments, Set<UUID> theoryOfDivisions,
			Set<UUID> labsOfBatches, UUID writtenBy, Set<UUID> teachingFaculty, UUID reviewing) {

		public boolean isEmpty() {
			return !this.everywhere && this.departments.isEmpty() && this.theoryOfDivisions.isEmpty()
					&& this.labsOfBatches.isEmpty() && this.writtenBy == null && this.teachingFaculty.isEmpty()
					&& this.reviewing == null;
		}

	}

}
