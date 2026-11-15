package com.kibootia.backend.rescheduling.domain;

/** What made a conflict check run (data model {@code check_trigger}). */
public enum CheckTrigger {

	/** The ticket was raised, or edited. */
	SUBMISSION,

	/** The ticket was being applied. */
	APPLY

}
