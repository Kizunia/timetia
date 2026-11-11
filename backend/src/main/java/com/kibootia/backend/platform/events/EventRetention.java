package com.kibootia.backend.platform.events;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.jobs.RetentionPurgeable;
import com.kibootia.backend.platform.tenancy.TenantContext;

/** Removes events that were delivered long ago and the marks that stopped a repeated delivery (ADR 0031). */
final class EventRetention {

	private EventRetention() {
	}

	/** Delivered events are kept briefly for diagnosis, then removed from the registry. */
	@Component
	static class CompletedEvents implements RetentionPurgeable {

		private final CompletedEventPublications completed;

		private final JdbcTemplate jdbc;

		private final Clock clock;

		private final EventsProperties properties;

		CompletedEvents(CompletedEventPublications completed, JdbcTemplate jdbc, Clock clock,
				EventsProperties properties) {
			this.completed = completed;
			this.jdbc = jdbc;
			this.clock = clock;
			this.properties = properties;
		}

		@Override
		public String name() {
			return "completed-events";
		}

		@Override
		public int purge() {
			Duration age = Duration.ofDays(this.properties.completedRetentionDays());
			LocalDateTime cutoff = LocalDateTime.ofInstant(this.clock.instant().minus(age), ZoneOffset.UTC);
			Integer expired = this.jdbc.queryForObject(
					"select count(*) from event_publication where completion_date is not null and completion_date < ?",
					Integer.class, cutoff);
			this.completed.deletePublicationsOlderThan(age);
			return (expired != null) ? expired : 0;
		}

	}

	/** The "already handled" marks outlive the longest retry window, then go. */
	@Component
	static class ProcessedMarks implements RetentionPurgeable {

		private final ProcessedEvents processed;

		private final Clock clock;

		private final EventsProperties properties;

		ProcessedMarks(ProcessedEvents processed, Clock clock, EventsProperties properties) {
			this.processed = processed;
			this.clock = clock;
			this.properties = properties;
		}

		@Override
		public String name() {
			return "processed-event-marks";
		}

		@Override
		public int purge() {
			return this.processed.deleteProcessedBefore(TenantContext.require(),
					this.clock.instant().minus(Duration.ofDays(this.properties.processedRetentionDays())));
		}

	}

}
