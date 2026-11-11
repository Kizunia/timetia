package com.kibootia.backend.platform.authorization;

import java.util.List;

/**
 * A module's rule for one kind of record and the policy-decided permissions that apply to it (Timetia ADR 0004). The
 * decision point calls it only when the caller holds the permission somewhere, and passes where, so a rule that decides
 * wrongly can narrow but never grant a permission the caller does not hold. The first policy that supports the record
 * decides; with none, the answer is no.
 */
public interface ScopedResourcePolicy {

	boolean supports(Object resource);

	/**
	 * @param grants where the caller holds the action; never empty
	 */
	boolean allows(Caller caller, String action, Object resource, List<GrantedScope> grants);

}
