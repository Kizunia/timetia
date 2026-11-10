package com.kibootia.backend.audit.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Where audit entries are kept. The log is append-only: this port can add an entry, read entries and remove expired
 * ones, and nothing else, so there is no update path to misuse (ADR 0046).
 */
public interface AuditEntryStore {

	void append(Entry entry);

	/** A page of the caller's tenant's entries that match the filter, newest first unless the page says otherwise. */
	Page<Stored> search(Filter filter, Pageable pageable);

	/** Removes the caller's tenant's entries that occurred before the cutoff and returns how many. */
	int deleteOccurredBefore(Instant cutoff);

	/** An entry as written. */
	record Entry(Instant occurredAt, UUID actorId, String action, String targetType, UUID targetId,
			Map<String, String> changes, String outcome, String traceId) {

	}

	/** An entry as read back. */
	record Stored(UUID id, Instant occurredAt, UUID actorId, String action, String targetType, UUID targetId,
			Map<String, String> changes, String outcome, String traceId) {

	}

	/**
	 * What to look for; a null field matches everything.
	 *
	 * @param actorId who did it
	 * @param actionPrefix the action code, or the start of one such as {@code access.} for a whole module
	 * @param targetType the kind of record acted on
	 * @param targetId the record acted on
	 * @param from the earliest time, inclusive
	 * @param to the latest time, exclusive
	 */
	record Filter(UUID actorId, String actionPrefix, String targetType, UUID targetId, Instant from, Instant to) {

	}

}
