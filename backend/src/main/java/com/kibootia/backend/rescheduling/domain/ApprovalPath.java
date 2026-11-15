package com.kibootia.backend.rescheduling.domain;

/** How a ticket came to be applied (data model {@code approval_path}); set when it is applied. */
public enum ApprovalPath {

	/** The author applied their own change, with no review. */
	SELF,

	/** Every person asked to review approved it. */
	REVIEWED

}
