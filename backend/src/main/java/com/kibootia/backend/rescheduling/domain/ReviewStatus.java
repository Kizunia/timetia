package com.kibootia.backend.rescheduling.domain;

/** Where one reviewer's answer stands (data model {@code review_status}). A review decision is not a ticket state. */
public enum ReviewStatus {

	/** Asked, and has not answered. */
	PENDING,

	/** Agrees with the change. */
	APPROVED,

	/** Does not agree, and says why. One rejection from anyone rejects the ticket. */
	REJECTED,

	/** Has seen the change and has nothing to add. It is an answer, and it never blocks. */
	ACKNOWLEDGED

}
