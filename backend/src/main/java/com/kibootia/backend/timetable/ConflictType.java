package com.kibootia.backend.timetable;

/**
 * Why a change cannot be made (data model {@code conflict_type}). Every one blocks; nobody can override a conflict
 * (conflict rules). Room fit (type and seats) is not checked yet, because the data it needs, what each kind of session
 * requires and the strength of a division, is not held.
 */
public enum ConflictType {

	/** The faculty member already has a lecture in that time. */
	FACULTY_BUSY,

	/** The division already has a lecture in that time. */
	DIVISION_BUSY,

	/** The batch already has a lab or tutorial, or its division a theory lecture, in that time. */
	BATCH_BUSY,

	/** The room is already in use in that time. */
	ROOM_OCCUPIED,

	/** Another ticket changed a lecture into that slot first. */
	SAME_SLOT_TICKET,

	/** A Sunday, or outside the term. Holidays and exam weeks are not held yet. */
	OUTSIDE_CALENDAR,

	/** Not on the hour, or outside 08:00 to 20:00. */
	OUTSIDE_SLOT_GRID,

	/** The lecture is gone, is cancelled, or was changed by another ticket since this change was planned. */
	OCCURRENCE_CHANGED

}
