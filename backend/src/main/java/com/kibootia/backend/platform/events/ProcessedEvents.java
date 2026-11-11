package com.kibootia.backend.platform.events;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.persistence.UuidBytes;

/**
 * Remembers which handler already processed which event (ADR 0031). The insert joins the handler's transaction, so a
 * handler that fails leaves no mark and its retry runs again.
 */
@Component
class ProcessedEvents {

	private final JdbcTemplate jdbc;

	private final Clock clock;

	ProcessedEvents(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** True when this is the first time the handler sees the event; false when it already processed it. */
	boolean markProcessed(UUID eventId, String handler, UUID tenantId) {
		int inserted = this.jdbc.update(
				"insert ignore into processed_event (event_id, handler, tenant_id, processed_at) values (?, ?, ?, ?)",
				UuidBytes.toBytes(eventId), handler, UuidBytes.toBytes(tenantId), utc(this.clock.instant()));
		return inserted == 1;
	}

	int deleteProcessedBefore(UUID tenantId, Instant cutoff) {
		return this.jdbc.update("delete from processed_event where tenant_id = ? and processed_at < ?",
				UuidBytes.toBytes(tenantId), utc(cutoff));
	}

	/** DATETIME(6) columns hold UTC wall time, as Hibernate writes them (spring.jpa.properties.hibernate.jdbc.time_zone). */
	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
