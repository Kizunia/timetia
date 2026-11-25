package com.kibootia.backend.platform.persistence;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * Generates version 7 UUIDs (RFC 9562) for identifiers that are needed before a row exists, such as an event ID. Rows
 * get their key from Hibernate's UUIDv7 generator ({@link BaseEntity}); this produces the same kind of value for
 * everything else. The first 48 bits are the Unix time in milliseconds, so values sort by creation time.
 */
public final class UuidV7 {

	private static final SecureRandom RANDOM = new SecureRandom();

	private UuidV7() {
	}

	public static UUID next(Clock clock) {
		long millis = clock.millis();
		long msb = (millis << 16) | 0x7000L | (RANDOM.nextLong() & 0x0FFFL);
		long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
		return new UUID(msb, lsb);
	}

}
