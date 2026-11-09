package com.kibootia.backend.platform.events;

import java.util.List;
import java.util.UUID;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.kibootia.backend.Await;
import com.kibootia.backend.TestcontainersConfiguration;
import com.kibootia.backend.platform.events.FailedEventAdministration.FailedEvent;
import com.kibootia.backend.platform.events.FlakyListener.TestEvent;
import com.kibootia.backend.platform.persistence.UuidBytes;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Durable events on a real MySQL (ADR 0031, ADR 0039): tenant and trace restored in the handler, a repeated delivery
 * skipped, retry with backoff and a cap, the failed-event view, and a manual retry. Backoff is zero here so the retry job
 * can be driven by hand; {@link RetryPolicyTest} covers the waits.
 */
@SpringBootTest(properties = { "kibootia.events.initial-backoff=PT0S", "kibootia.events.max-backoff=PT0S",
		"kibootia.events.max-attempts=3", "kibootia.events.stale-after=PT1H" })
@Import({ TestcontainersConfiguration.class, FlakyListener.class })
class DurableEventsIT {

	static final UUID TENANT_A = TenantContext.DEFAULT_TENANT;

	static final UUID TENANT_B = UUID.fromString("00000000-0000-7000-8000-0000000000bb");

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	EventContextFactory contexts;

	@Autowired
	EventRetryJob retryJob;

	@Autowired
	FailedEventAdministration failedEvents;

	@Autowired
	Tracer tracer;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void clean() {
		Await.until("in-flight deliveries to finish", () -> jdbc.queryForObject(
				"select count(*) from event_publication where completion_date is null and status in "
						+ "('PUBLISHED', 'PROCESSING', 'RESUBMITTED')", Integer.class) == 0);
		jdbc.update("delete from event_publication");
		jdbc.update("delete from processed_event");
		FlakyListener.reset();
	}

	private UUID publish(UUID tenant, String label) {
		return TenantContext.callAs(tenant, () -> transactions.execute((status) -> {
			TestEvent event = new TestEvent(label, contexts.capture());
			publisher.publishEvent(event);
			return event.context().eventId();
		}));
	}

	private int completedPublications() {
		return jdbc.queryForObject("select count(*) from event_publication where completion_date is not null",
				Integer.class);
	}

	private int failedPublications() {
		return jdbc.queryForObject("select count(*) from event_publication where completion_date is null "
				+ "and status = 'FAILED'", Integer.class);
	}

	private int attemptsOfOnlyPublication() {
		Integer attempts = jdbc.queryForObject("select completion_attempts from event_publication", Integer.class);
		return (attempts != null) ? attempts : 0;
	}

	private UUID onlyFailedPublicationId() {
		return UuidBytes.fromBytes(jdbc.queryForObject("select id from event_publication", byte[].class));
	}

	private Page<FailedEvent> failedIn(UUID tenant) {
		return TenantContext.callAs(tenant, () -> failedEvents.list(PageRequest.of(0, 50)));
	}

	@Test
	@DisplayName("the handler runs in the event's tenant, in the publisher's trace, as the system")
	void contextIsRestored() {
		Span span = tracer.nextSpan().name("publisher").start();
		try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
			publish(TENANT_A, "propagation");
		}
		finally {
			span.end();
		}

		Await.until("the handler to run", () -> FlakyListener.SUCCESSES.get() == 1);
		FlakyListener.Seen seen = FlakyListener.LAST_SEEN.get();
		assertThat(seen.tenant()).contains(TENANT_A);
		assertThat(seen.traceId()).contains(span.context().traceId());
		assertThat(seen.actedAsSystem()).isTrue();
	}

	@Test
	@DisplayName("an event of another tenant is handled in that tenant")
	void otherTenantIsRestored() {
		publish(TENANT_B, "other tenant");

		Await.until("the handler to run", () -> FlakyListener.SUCCESSES.get() == 1);
		assertThat(FlakyListener.LAST_SEEN.get().tenant()).contains(TENANT_B);
	}

	@Test
	@DisplayName("a delivered event is marked completed")
	void deliveredEventCompletes() {
		publish(TENANT_A, "plain");

		Await.until("the event to complete", () -> completedPublications() == 1);
		assertThat(FlakyListener.SUCCESSES.get()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from processed_event", Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("the same event delivered twice runs the handler once")
	void repeatedDeliveryIsSkipped() {
		EventContext same = TenantContext.callAs(TENANT_A, contexts::capture);
		TenantContext.runAs(TENANT_A, () -> transactions.executeWithoutResult((status) -> {
			publisher.publishEvent(new TestEvent("first copy", same));
			publisher.publishEvent(new TestEvent("second copy", same));
		}));

		Await.until("both publications to complete", () -> completedPublications() == 2);
		assertThat(FlakyListener.CALLS.get()).as("the handler method was reached once").isEqualTo(1);
		assertThat(FlakyListener.SUCCESSES.get()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from processed_event", Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("a failing handler leaves the event failed and no mark that it was processed")
	void failureLeavesNoMark() {
		FlakyListener.FAILURES_LEFT.set(1);
		publish(TENANT_A, "fails once");

		Await.until("the event to be marked failed", () -> failedPublications() == 1);
		assertThat(completedPublications()).isZero();
		assertThat(jdbc.queryForObject("select count(*) from processed_event", Integer.class)).isZero();
		assertThat(attemptsOfOnlyPublication()).isEqualTo(1);
	}

	@Test
	@DisplayName("the retry job redelivers a failed event until it succeeds")
	void retryUntilSuccess() {
		FlakyListener.FAILURES_LEFT.set(2);
		publish(TENANT_A, "fails twice");
		Await.until("the first attempt to fail", () -> failedPublications() == 1 && FlakyListener.CALLS.get() == 1);

		Await.until("the event to be delivered", () -> {
			retryJob.runOnce();
			return completedPublications() == 1;
		});

		assertThat(FlakyListener.CALLS.get()).as("two failures and one success").isEqualTo(3);
		assertThat(FlakyListener.SUCCESSES.get()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from processed_event", Integer.class)).isEqualTo(1);
		assertThat(failedPublications()).isZero();
	}

	@Test
	@DisplayName("the retry job gives up after the maximum number of attempts and the event stays in the failed view")
	void givesUpAfterMaximumAttempts() {
		FlakyListener.FAILURES_LEFT.set(100);
		publish(TENANT_A, "always fails");
		Await.until("the first attempt to fail", () -> FlakyListener.CALLS.get() == 1 && failedPublications() == 1);

		Await.until("three attempts", () -> {
			retryJob.runOnce();
			return FlakyListener.CALLS.get() >= 3 && failedPublications() == 1 && attemptsOfOnlyPublication() >= 3;
		});

		assertThat(retryJob.runOnce()).as("nothing is due once the attempts are used up").isZero();
		assertThat(retryJob.runOnce()).isZero();
		assertThat(FlakyListener.CALLS.get()).isEqualTo(3);
		List<FailedEvent> failed = failedIn(TENANT_A).getContent();
		assertThat(failed).hasSize(1);
		assertThat(failed.get(0).retriesExhausted()).isTrue();
		assertThat(failed.get(0).attempts()).isEqualTo(3);
		assertThat(failed.get(0).eventType()).endsWith("FlakyListener$TestEvent");
		assertThat(failed.get(0).listener()).contains("FlakyListener");
	}

	@Test
	@DisplayName("an event still being retried is in the failed view but not marked as given up")
	void failedViewShowsEventsBeingRetried() {
		FlakyListener.FAILURES_LEFT.set(100);
		publish(TENANT_A, "failing");
		Await.until("the event to fail", () -> failedPublications() == 1);

		List<FailedEvent> failed = failedIn(TENANT_A).getContent();

		assertThat(failed).hasSize(1);
		assertThat(failed.get(0).retriesExhausted()).isFalse();
	}

	@Test
	@DisplayName("the failed view lists only the caller's tenant and cannot retry another tenant's event")
	void failedViewIsTenantScoped() {
		FlakyListener.FAILURES_LEFT.set(100);
		publish(TENANT_A, "tenant a");
		publish(TENANT_B, "tenant b");
		Await.until("both events to fail", () -> failedPublications() == 2);

		UUID idInB = failedIn(TENANT_B).getContent().get(0).id();

		assertThat(failedIn(TENANT_A).getContent()).hasSize(1);
		assertThat(failedIn(TENANT_B).getContent()).hasSize(1);
		assertThat(failedIn(TENANT_A).getContent().get(0).id()).isNotEqualTo(idInB);
		assertThat(TenantContext.callAs(TENANT_A, () -> failedEvents.find(idInB))).isEmpty();
		assertThat(TenantContext.callAs(TENANT_A, () -> failedEvents.retry(idInB))).isFalse();
		assertThat(TenantContext.callAs(TENANT_B, () -> failedEvents.find(idInB))).isPresent();
	}

	@Test
	@DisplayName("the failed view pages its results")
	void failedViewIsPaged() {
		FlakyListener.FAILURES_LEFT.set(100);
		for (int i = 0; i < 3; i++) {
			publish(TENANT_A, "failing " + i);
		}
		Await.until("all events to fail", () -> failedPublications() == 3);

		Page<FailedEvent> first = TenantContext.callAs(TENANT_A, () -> failedEvents.list(PageRequest.of(0, 2)));
		Page<FailedEvent> second = TenantContext.callAs(TENANT_A, () -> failedEvents.list(PageRequest.of(1, 2)));

		assertThat(first.getTotalElements()).isEqualTo(3);
		assertThat(first.getContent()).hasSize(2);
		assertThat(second.getContent()).hasSize(1);
		assertThat(first.getContent()).extracting(FailedEvent::id).doesNotContainAnyElementsOf(
				second.getContent().stream().map(FailedEvent::id).toList());
	}

	@Test
	@DisplayName("an administrator can retry an event the job gave up on")
	void manualRetryAfterGivingUp() {
		FlakyListener.FAILURES_LEFT.set(3);
		publish(TENANT_A, "fails three times");
		Await.until("the first attempt to fail", () -> FlakyListener.CALLS.get() == 1 && failedPublications() == 1);
		Await.until("the attempts to be used up", () -> {
			retryJob.runOnce();
			return FlakyListener.CALLS.get() == 3 && attemptsOfOnlyPublication() >= 3 && failedPublications() == 1;
		});
		UUID id = onlyFailedPublicationId();

		boolean retried = TenantContext.callAs(TENANT_A, () -> failedEvents.retry(id));

		assertThat(retried).isTrue();
		Await.until("the retried event to complete", () -> completedPublications() == 1);
		assertThat(FlakyListener.CALLS.get()).isEqualTo(4);
		assertThat(FlakyListener.SUCCESSES.get()).isEqualTo(1);
		assertThat(failedIn(TENANT_A).getContent()).isEmpty();
	}

	@Test
	@DisplayName("the failed view reports metadata only, never the event's payload")
	void payloadIsNotExposed() {
		assertThat(FailedEvent.class.getRecordComponents()).extracting((c) -> c.getName())
			.containsExactly("id", "eventType", "listener", "publishedAt", "attempts", "lastAttemptAt",
					"retriesExhausted");
	}

}
