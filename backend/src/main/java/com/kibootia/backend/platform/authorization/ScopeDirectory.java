package com.kibootia.backend.platform.authorization;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Says which places a role can be assigned in exist (Timetia ADR 0004), for example departments, divisions and batches.
 * The module that owns the data implements it. Without an implementation no scoped role can be assigned, so nothing is
 * silently unvalidated.
 *
 * <p>A place has an id and a <em>code</em>, the short name people use in files and screens (for example {@code CSE} or
 * {@code CSE-A}). The contract the owner implements: within a tenant and a kind, a code names at most one live place, and
 * how codes are compared (case, spaces) is the owner's rule, applied the same way every time. The caller passes the code
 * as it was written and never normalises it.
 */
public interface ScopeDirectory {

	/** Whether there is a live place of this kind with this id in the current tenant. */
	boolean exists(String kind, UUID id);

	/**
	 * The id of the live place of this kind with this code in the current tenant, or empty when there is none. Empty is
	 * the answer for a code that names nothing, and the caller treats it as an unknown place.
	 */
	Optional<UUID> idOf(String kind, String code);

	/**
	 * The codes of the places of this kind with these ids in the current tenant, for showing an assignment. An id with no
	 * live place is absent from the map.
	 */
	Map<UUID, String> codesOf(String kind, Collection<UUID> ids);

}
