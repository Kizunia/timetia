package com.kibootia.backend.rescheduling.domain;

/** What one change of a ticket does to a lecture (data model {@code operation_type}). */
public enum ChangeOperation {

	/** Put the lecture at another date, time, room or mode. */
	MOVE,

	/** Cancel the lecture. */
	CANCEL

}
