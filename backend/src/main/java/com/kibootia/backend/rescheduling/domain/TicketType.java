package com.kibootia.backend.rescheduling.domain;

/**
 * What a ticket asks for (data model {@code ticket_type}). Each works on one dated lecture. Every type is built.
 */
public enum TicketType {

	/** Move one lecture to another date, time or room. */
	RESCHEDULE,

	/** Cancel one lecture, with a reason. */
	CANCEL,

	/** Have another faculty member take one lecture on its date. */
	SUBSTITUTE,

	/** Add one lecture of a teaching assignment on a date. */
	EXTRA,

	/** Two lectures of one division take each other's place. */
	EXCHANGE,

	/** Undo an applied ticket: bring its lectures back to where, and with whom, they were. */
	REVERT,

	/** Move a lecture of the weekly timetable, every week from a date onward. It always needs an HOD. */
	PERMANENT_CHANGE

}
