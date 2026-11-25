package com.kibootia.backend.timetable.domain;

/**
 * What a lecture holds for an hour. Two lectures can never hold the same thing for the same hour, which is how a teacher,
 * a room or a group of students is kept from being booked twice, whatever the order in which tickets are applied.
 */
public enum ClaimKind {

	FACULTY,

	ROOM,

	/**
	 * The whole division, held by a theory lecture. It keeps two theory lectures of a division apart even when the division
	 * has no batches yet, or has gained one since.
	 */
	DIVISION,

	/** One batch, held by a lab or a tutorial of the batch, and by every theory lecture of the batch's division. */
	STUDENT_BATCH

}
