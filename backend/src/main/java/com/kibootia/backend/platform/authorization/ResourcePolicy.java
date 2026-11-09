package com.kibootia.backend.platform.authorization;

/**
 * A module's own rule for one kind of record, for cases the permission-and-ownership convention cannot express (a
 * record shared with named users, a state-dependent rule). It is consulted instead of the convention when it supports
 * the record, and only after the caller is known to hold the permission.
 */
public interface ResourcePolicy {

	boolean supports(Object resource);

	/**
	 * @param scope what the caller's permissions allow for this action: {@link Scope#ALL} or {@link Scope#OWN}
	 */
	boolean allows(Caller caller, String action, Object resource, Scope scope);

}
