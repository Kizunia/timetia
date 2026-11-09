package com.kibootia.backend.audit.web;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.kibootia.backend.Await;
import com.kibootia.backend.GrantedPermissions;
import com.kibootia.backend.TestcontainersConfiguration;
import com.kibootia.backend.audit.AuditPermissions;
import com.kibootia.backend.platform.events.EventContextFactory;
import com.kibootia.backend.platform.events.FlakyListener;
import com.kibootia.backend.platform.events.FlakyListener.TestEvent;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The failed-event API (ADR 0031): administrators see events that keep failing and retry them, every retry is audited,
 * and everyone else is refused. Each operation has an allowed and a denied case.
 */
@SpringBootTest(properties = { "kibootia.events.initial-backoff=PT0S", "kibootia.events.max-backoff=PT0S",
		"kibootia.events.max-attempts=3", "kibootia.events.stale-after=PT1H" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, GrantedPermissions.class, FlakyListener.class })
class FailedEventsApiIT {

	static final UUID ADMIN = UUID.fromString("44444444-4444-7444-8444-444444444444");

	static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-7000-8000-0000000000bb");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	EventContextFactory contexts;

	@BeforeEach
	@AfterEach
	void clean() {
		Await.until("in-flight deliveries to finish", () -> jdbc.queryForObject(
				"select count(*) from event_publication where completion_date is null and status in "
						+ "('PUBLISHED', 'PROCESSING', 'RESUBMITTED')", Integer.class) == 0);
		jdbc.update("delete from event_publication");
		jdbc.update("delete from processed_event");
		jdbc.update("delete from audit_entry");
		FlakyListener.reset();
		GrantedPermissions.reset();
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
		return jwt().jwt((token) -> token.subject(ADMIN.toString()));
	}

	private void publishFailing(UUID tenant) {
		FlakyListener.FAILURES_LEFT.addAndGet(1);
		TenantContext.runAs(tenant, () -> transactions.executeWithoutResult(
				(status) -> publisher.publishEvent(new TestEvent("must not leak", contexts.capture()))));
	}

	private int failedPublications() {
		return jdbc.queryForObject("select count(*) from event_publication where completion_date is null "
				+ "and status = 'FAILED'", Integer.class);
	}

	private String onlyPublicationId() {
		return jdbc.queryForObject("select lower(concat(substr(hex(id),1,8),'-',substr(hex(id),9,4),'-',"
				+ "substr(hex(id),13,4),'-',substr(hex(id),17,4),'-',substr(hex(id),21))) from event_publication",
				String.class);
	}

	@Test
	@DisplayName("an ordinary user may neither list nor retry failed events")
	void deniedWithoutPermission() throws Exception {
		mvc.perform(get("/api/v1/failed-events").with(admin()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("platform.forbidden"));
		mvc.perform(post("/api/v1/failed-events/" + UUID.randomUUID() + "/retry").with(admin()))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("without a token the API answers 401")
	void unauthenticated() throws Exception {
		mvc.perform(get("/api/v1/failed-events")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("an administrator sees the failed events of their tenant, without payloads")
	void listsFailedEvents() throws Exception {
		GrantedPermissions.grant(AuditPermissions.VIEW_FAILED_EVENTS);
		publishFailing(TenantContext.DEFAULT_TENANT);
		publishFailing(OTHER_TENANT);
		Await.until("both events to fail", () -> failedPublications() == 2);

		mvc.perform(get("/api/v1/failed-events").with(admin()))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].id").isString())
			.andExpect(jsonPath("$.items[0].eventType").value(org.hamcrest.Matchers.endsWith("TestEvent")))
			.andExpect(jsonPath("$.items[0].attempts").value(1))
			.andExpect(jsonPath("$.items[0].retriesExhausted").value(false))
			.andExpect(jsonPath("$.items[0].publishedAt").isString())
			.andExpect(jsonPath("$.page.totalElements").value(1))
			.andExpect(jsonPath("$.page.size").value(20))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("must not leak"))))
			.andExpect(jsonPath("$.items[0].serializedEvent").doesNotExist());
	}

	@Test
	@DisplayName("the list rejects an unsupported sort and an oversize page")
	void listValidatesItsParameters() throws Exception {
		GrantedPermissions.grant(AuditPermissions.VIEW_FAILED_EVENTS);

		mvc.perform(get("/api/v1/failed-events?sort=attempts,desc").with(admin()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("sort"));
		mvc.perform(get("/api/v1/failed-events?size=101").with(admin())).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("a retry is accepted, audited with the administrator's ID, and redelivers the event")
	void retryIsAcceptedAndAudited() throws Exception {
		GrantedPermissions.grant(AuditPermissions.RETRY_FAILED_EVENTS);
		publishFailing(TenantContext.DEFAULT_TENANT);
		Await.until("the event to fail", () -> failedPublications() == 1);
		String id = onlyPublicationId();

		mvc.perform(post("/api/v1/failed-events/" + id + "/retry").with(admin())).andExpect(status().isAccepted());

		Await.until("the event to be delivered", () -> failedPublications() == 0
				&& jdbc.queryForObject("select count(*) from event_publication where completion_date is not null",
						Integer.class) == 1);
		assertThat(FlakyListener.SUCCESSES.get()).isEqualTo(1);
		var entry = jdbc.queryForMap("select hex(actor_id) actor, action, target_type, outcome, changes from audit_entry");
		assertThat(entry.get("actor")).isEqualTo(ADMIN.toString().replace("-", "").toUpperCase());
		assertThat(entry.get("action")).isEqualTo("audit.failed-event.retried");
		assertThat(entry.get("target_type")).isEqualTo("failed-event");
		assertThat(entry.get("outcome")).isEqualTo("success");
		assertThat((String) entry.get("changes")).contains("\"attempts\":\"1\"").doesNotContain("must not leak");
	}

	@Test
	@DisplayName("retrying an event that does not exist is 404 and writes no audit entry")
	void retryUnknownEvent() throws Exception {
		GrantedPermissions.grant(AuditPermissions.RETRY_FAILED_EVENTS);

		mvc.perform(post("/api/v1/failed-events/" + UUID.randomUUID() + "/retry").with(admin()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("audit.failed_event_not_found"));

		assertThat(jdbc.queryForObject("select count(*) from audit_entry", Integer.class)).isZero();
	}

	@Test
	@DisplayName("another tenant's failed event cannot be retried and looks like it does not exist")
	void cannotRetryAnotherTenantsEvent() throws Exception {
		GrantedPermissions.grant(AuditPermissions.RETRY_FAILED_EVENTS);
		publishFailing(OTHER_TENANT);
		Await.until("the event to fail", () -> failedPublications() == 1);

		mvc.perform(post("/api/v1/failed-events/" + onlyPublicationId() + "/retry").with(admin()))
			.andExpect(status().isNotFound());

		assertThat(failedPublications()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from audit_entry", Integer.class)).isZero();
	}

	@Test
	@DisplayName("an id that is not a UUID is 400")
	void malformedId() throws Exception {
		GrantedPermissions.grant(AuditPermissions.RETRY_FAILED_EVENTS);

		mvc.perform(post("/api/v1/failed-events/not-a-uuid/retry").with(admin())).andExpect(status().isBadRequest());
	}

}
