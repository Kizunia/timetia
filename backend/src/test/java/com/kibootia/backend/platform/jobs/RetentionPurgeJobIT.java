package com.kibootia.backend.platform.jobs;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.kibootia.backend.TestcontainersConfiguration;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The retention purge (ADR 0034, ADR 0046): one job removes expired audit entries, delivered events and processed marks
 * as the system, in the default tenant, and a purge that fails does not stop the others.
 */
@SpringBootTest(properties = { "kibootia.audit.retention-days=30", "kibootia.events.completed-retention-days=7",
		"kibootia.events.processed-retention-days=30" })
@Import({ TestcontainersConfiguration.class, RetentionPurgeJobIT.FailingPurge.class })
class RetentionPurgeJobIT {

	static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-7000-8000-0000000000bb");

	static final AtomicInteger FAILING_PURGE_CALLS = new AtomicInteger();

	@TestComponent
	static class FailingPurge implements RetentionPurgeable {

		@Override
		public String name() {
			return "always-fails";
		}

		@Override
		public int purge() {
			FAILING_PURGE_CALLS.incrementAndGet();
			throw new IllegalStateException("this purge breaks on purpose");
		}

	}

	@Autowired
	RetentionPurgeJob job;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void clean() {
		jdbc.update("delete from audit_entry");
		jdbc.update("delete from event_publication");
		jdbc.update("delete from processed_event");
		FAILING_PURGE_CALLS.set(0);
	}

	private static String hex(UUID id) {
		return id.toString().replace("-", "");
	}

	private void auditEntry(UUID tenant, int daysOld) {
		jdbc.update("insert into audit_entry (id, tenant_id, occurred_at, actor_id, action, target_type, outcome) "
				+ "values (unhex(?), unhex(?), now(6) - interval ? day, unhex(?), 'test.widget.created', 'widget', "
				+ "'success')", hex(UUID.randomUUID()), hex(tenant), daysOld, hex(UUID.randomUUID()));
	}

	private void completedEvent(int completedDaysAgo) {
		jdbc.update("insert into event_publication (id, listener_id, event_type, serialized_event, publication_date, "
				+ "completion_date, status, completion_attempts) values (unhex(?), 'l', 't', '{}', "
				+ "now(6) - interval ? day, now(6) - interval ? day, 'COMPLETED', 1)", hex(UUID.randomUUID()),
				completedDaysAgo + 1, completedDaysAgo);
	}

	private void processedMark(UUID tenant, int daysOld) {
		jdbc.update("insert into processed_event (event_id, handler, tenant_id, processed_at) values (unhex(?), 'h', "
				+ "unhex(?), now(6) - interval ? day)", hex(UUID.randomUUID()), hex(tenant), daysOld);
	}

	private int count(String table) {
		return jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

	@Test
	@DisplayName("expired audit entries, delivered events and processed marks are removed; newer ones stay")
	void removesWhatHasExpired() {
		auditEntry(TenantContext.DEFAULT_TENANT, 40);
		auditEntry(TenantContext.DEFAULT_TENANT, 10);
		completedEvent(10);
		completedEvent(2);
		processedMark(TenantContext.DEFAULT_TENANT, 40);
		processedMark(TenantContext.DEFAULT_TENANT, 10);

		int removed = job.runOnce();

		assertThat(removed).isEqualTo(3);
		assertThat(count("audit_entry")).isEqualTo(1);
		assertThat(count("event_publication")).isEqualTo(1);
		assertThat(count("processed_event")).isEqualTo(1);
	}

	@Test
	@DisplayName("data of another tenant is left alone")
	void otherTenantsAreLeftAlone() {
		auditEntry(OTHER_TENANT, 400);
		processedMark(OTHER_TENANT, 400);

		assertThat(job.runOnce()).isZero();

		assertThat(count("audit_entry")).isEqualTo(1);
		assertThat(count("processed_event")).isEqualTo(1);
	}

	@Test
	@DisplayName("a purge that fails is logged and the others still run")
	void oneFailureDoesNotStopTheRest() {
		auditEntry(TenantContext.DEFAULT_TENANT, 400);

		int removed = job.runOnce();

		assertThat(FAILING_PURGE_CALLS.get()).isEqualTo(1);
		assertThat(removed).isEqualTo(1);
		assertThat(count("audit_entry")).isZero();
	}

	@Test
	@DisplayName("running it again with nothing expired removes nothing")
	void idempotent() {
		auditEntry(TenantContext.DEFAULT_TENANT, 400);

		assertThat(job.runOnce()).isEqualTo(1);
		assertThat(job.runOnce()).isZero();
	}

}
