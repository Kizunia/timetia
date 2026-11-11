package com.kibootia.backend.platform.events;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.persistence.UuidBytes;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * Reads and retries the events whose delivery keeps failing (ADR 0031). It reports metadata only, never the event's
 * payload, which can hold personal data. Callers authorize and audit: this is the mechanism, the {@code audit} module
 * owns the administrative use case.
 *
 * <p>The registry table has no tenant column; the tenant is read from the {@code context} the event carries inside its
 * JSON payload. Events that are not {@link DomainEvent}s have none and are never listed.
 */
@Component
public class FailedEventAdministration {

	/** The tenant of the event, read from its serialised payload; NULL when the payload has no context. */
	private static final String TENANT_OF_EVENT = "case when json_valid(serialized_event) then "
			+ "json_unquote(json_extract(serialized_event, '$.context.tenantId')) end";

	private static final String FROM_FAILED = " from event_publication where completion_date is null "
			+ "and status = 'FAILED' and " + TENANT_OF_EVENT + " = ?";

	private final JdbcTemplate jdbc;

	private final IncompleteEventPublications incomplete;

	private final RetryPolicy policy;

	FailedEventAdministration(JdbcTemplate jdbc, IncompleteEventPublications incomplete,
			EventsProperties properties) {
		this.jdbc = jdbc;
		this.incomplete = incomplete;
		this.policy = new RetryPolicy(properties);
	}

	/**
	 * An event whose delivery failed.
	 *
	 * @param id the publication, used to retry it
	 * @param eventType the event's class
	 * @param listener the handler that failed
	 * @param attempts how many deliveries have been made
	 * @param retriesExhausted true when the retry job has stopped trying and only a manual retry will deliver it
	 */
	public record FailedEvent(UUID id, String eventType, String listener, Instant publishedAt, int attempts,
			Instant lastAttemptAt, boolean retriesExhausted) {

	}

	/** The failed events of the current tenant, oldest first. */
	public Page<FailedEvent> list(Pageable pageable) {
		String tenant = TenantContext.require().toString();
		Long total = this.jdbc.queryForObject("select count(*)" + FROM_FAILED, Long.class, tenant);
		List<FailedEvent> events = this.jdbc.query(
				"select id, event_type, listener_id, publication_date, completion_attempts, last_resubmission_date"
						+ FROM_FAILED + " order by publication_date, id limit ? offset ?",
				this::map, tenant, pageable.getPageSize(), pageable.getOffset());
		return new PageImpl<>(events, pageable, (total != null) ? total : 0);
	}

	public Optional<FailedEvent> find(UUID id) {
		return this.jdbc
			.query("select id, event_type, listener_id, publication_date, completion_attempts, last_resubmission_date"
					+ FROM_FAILED + " and id = ?", this::map, TenantContext.require().toString(), UuidBytes.toBytes(id))
			.stream()
			.findFirst();
	}

	/**
	 * Redelivers one failed event of the current tenant now, whatever its attempt count.
	 *
	 * @return false when there is no such failed event for this tenant
	 */
	public boolean retry(UUID id) {
		if (find(id).isEmpty()) {
			return false;
		}
		this.incomplete.resubmitIncompletePublications((publication) -> publication.getIdentifier().equals(id));
		return true;
	}

	private FailedEvent map(ResultSet row, int index) throws SQLException {
		Instant published = instant(row, "publication_date");
		Instant lastResubmitted = instant(row, "last_resubmission_date");
		int attempts = row.getInt("completion_attempts");
		return new FailedEvent(UuidBytes.fromBytes(row.getBytes("id")), row.getString("event_type"),
				row.getString("listener_id"), published, attempts, (lastResubmitted != null) ? lastResubmitted : published,
				this.policy.exhausted(attempts));
	}

	/** The registry's TIMESTAMP columns hold UTC, as Hibernate writes them. */
	private static Instant instant(ResultSet row, String column) throws SQLException {
		LocalDateTime value = row.getObject(column, LocalDateTime.class);
		return (value != null) ? value.toInstant(ZoneOffset.UTC) : null;
	}

}
