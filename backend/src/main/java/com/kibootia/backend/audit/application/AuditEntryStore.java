package com.kibootia.backend.audit.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Where audit entries are kept. The log is append-only: this port can add an entry and remove expired ones, and nothing
 * else, so there is no update path to misuse (ADR 0046).
 */
public interface AuditEntryStore {

	void append(Entry entry);

	/** Removes the caller's tenant's entries that occurred before the cutoff and returns how many. */
	int deleteOccurredBefore(Instant cutoff);

	/** An entry as stored. */
	record Entry(Instant occurredAt, UUID actorId, String action, String targetType, UUID targetId,
			Map<String, String> changes, String outcome, String traceId) {

	}

}
