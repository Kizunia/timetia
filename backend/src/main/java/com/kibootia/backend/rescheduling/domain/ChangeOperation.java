package com.kibootia.backend.rescheduling.domain;

/** What one change of a ticket does to a lecture (data model {@code operation_type}). */
public enum ChangeOperation {

	/** Put the lecture at another date, time, room or mode. */
	MOVE,

	/** Cancel the lecture. */
	CANCEL,

	/** Another faculty member takes the lecture, in the same slot. */
	REASSIGN_FACULTY,

	/** Add a lecture of a teaching assignment. */
	CREATE_EXTRA,

	/** Put the lecture back at a slot, with a teacher, not cancelled: one change of a revert. */
	RESTORE

}
