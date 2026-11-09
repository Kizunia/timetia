package com.kibootia.backend.platform.authorization;

/**
 * Which records of a kind a caller may see or change for an action (ADR 0044). A module turns the scope into a query
 * condition, so the database filters and paging stays correct.
 */
public enum Scope {

	/** Every record of the caller's tenant. */
	ALL,

	/** Only records the caller owns. */
	OWN,

	/** No record: the caller does not hold the permission. */
	NONE

}
