package com.kibootia.backend.academic;

import java.util.Set;
import java.util.UUID;

/** What the policy needs to know about a ticket: the lecture it concerns and the people on it. */
public interface TicketAccessFacts extends LectureAccessFacts {

	/** The author, whether a faculty member or an HOD acting for one. */
	UUID authorUserId();

	/** The faculty affected by the change, as user ids. */
	Set<UUID> affectedFacultyUserIds();

	/** The people asked to review, as user ids. */
	Set<UUID> requestedReviewerUserIds();

	/** Whether the change is permanent. A permanent change is reverted only by an HOD or Super HOD. */
	boolean permanentChange();

}
