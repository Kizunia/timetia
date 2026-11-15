package com.kibootia.backend.access;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who holds a role in a place, for a module that has to tell people about something that happened there (Timetia ADR 0004).
 * The notifications module tells the HOD of a department, the CR of a division and the BR and students of a batch; it asks
 * here, by the kind of place and its ID, and never reads the assignments. Only roles that carry a scope type count, and a
 * deleted role grants nothing. The answer is about the caller's tenant.
 */
public interface ScopeHolders {

	/**
	 * For each of these places of the kind (for example {@code DEPARTMENT}), the users who hold any scoped role in it. A
	 * place nobody holds is absent.
	 */
	Map<UUID, Set<UUID>> usersIn(String scopeType, Collection<UUID> scopeIds);

}
