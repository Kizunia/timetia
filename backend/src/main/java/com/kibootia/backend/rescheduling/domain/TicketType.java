package com.kibootia.backend.rescheduling.domain;

/**
 * What a ticket asks for (data model {@code ticket_type}). Each works on one dated lecture. Exchange, extra lecture,
 * substitute, permanent change and revert are not built yet.
 */
public enum TicketType {

	/** Move one lecture to another date, time or room. */
	RESCHEDULE,

	/** Cancel one lecture, with a reason. */
	CANCEL

}
