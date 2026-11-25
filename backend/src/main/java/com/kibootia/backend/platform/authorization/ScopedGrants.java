package com.kibootia.backend.platform.authorization;

import java.util.List;

/**
 * Where a caller holds a policy-decided permission, from the role assignments (Timetia ADR 0004). The {@code access}
 * module implements it from the stored roles. Without an implementation nobody holds a policy-decided permission.
 */
public interface ScopedGrants {

	/** Holds nothing. */
	ScopedGrants NONE = (caller, action) -> List.of();

	/**
	 * The places where the caller holds the action, one per assignment of a role that carries it. Empty when the caller
	 * does not hold it anywhere.
	 */
	List<GrantedScope> grantsOf(Caller caller, String action);

}
