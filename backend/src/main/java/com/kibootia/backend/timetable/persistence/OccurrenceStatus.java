package com.kibootia.backend.timetable.persistence;

/**
 * Whether a stored lecture still counts. A lecture a later version replaces is kept, marked superseded, rather than
 * deleted, so the history of what was published stays; only active lectures are ever shown or checked.
 */
enum OccurrenceStatus {

	ACTIVE, SUPERSEDED

}
