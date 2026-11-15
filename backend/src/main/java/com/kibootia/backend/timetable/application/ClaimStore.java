package com.kibootia.backend.timetable.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kibootia.backend.timetable.domain.ClaimKey;

/**
 * Where what each lecture holds is kept (see {@link com.kibootia.backend.timetable.domain.Claims}). The use cases depend on
 * this port, never on JPA (ADR 0003). Two claims never have the same key: the store refuses the second, which is the last
 * defence against two changes racing for one slot.
 */
public interface ClaimStore {

	/** What one lecture holds. */
	record Claim(UUID occurrenceId, ClaimKey key) {

	}

	/** Stores the claims. Fails with a data integrity violation when one of the keys is already held. */
	void insertAll(List<Claim> claims);

	/** Releases everything these lectures hold. */
	void deleteOf(Collection<UUID> occurrenceIds);

	/** For each of these keys that is held, the lecture that holds it. */
	Map<ClaimKey, UUID> holders(Collection<ClaimKey> keys);

}
