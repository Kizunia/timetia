package com.kibootia.backend.timetable.application;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kibootia.backend.timetable.domain.ClaimKey;
import com.kibootia.backend.timetable.domain.ClaimKind;

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

	/**
	 * What is held among these resources of one kind on one date, in these hours, answered with a single query however many
	 * resources are asked about (for example every room, to find the free ones). Keys that are not held are absent.
	 */
	Map<ClaimKey, UUID> holdersOf(ClaimKind kind, Collection<UUID> resourceIds, LocalDate date, Collection<Integer> hours);

}
