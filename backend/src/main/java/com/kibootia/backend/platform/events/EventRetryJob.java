package com.kibootia.backend.platform.events;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.stereotype.Component;

/**
 * Redelivers events whose handler failed or was lost (ADR 0031), with backoff and a cap on attempts. {@link
 * EventRetrySchedule} runs it on a timer under a database lock. Redelivery goes through the same {@link DurableEventListener} wrapper,
 * which restores the tenant and trace and skips work already done.
 */
@Component
public class EventRetryJob {

	private static final Logger log = LoggerFactory.getLogger(EventRetryJob.class);

	private final IncompleteEventPublications incomplete;

	private final RetryPolicy policy;

	private final Clock clock;

	EventRetryJob(IncompleteEventPublications incomplete, EventsProperties properties, Clock clock) {
		this.incomplete = incomplete;
		this.policy = new RetryPolicy(properties);
		this.clock = clock;
	}

	/** Redelivers every event that is due and returns how many it resubmitted. */
	public int runOnce() {
		AtomicInteger resubmitted = new AtomicInteger();
		this.incomplete.resubmitIncompletePublications(ResubmissionOptions.defaults().withBatchSize(100).withFilter(
				(EventPublication publication) -> {
					boolean due = this.policy.shouldRetry(publication.getStatus(), publication.getCompletionAttempts(),
							publication.getLastPublicationDate(), this.clock.instant());
					if (due) {
						resubmitted.incrementAndGet();
					}
					return due;
				}));
		if (resubmitted.get() > 0) {
			log.info("Resubmitted {} events for redelivery", resubmitted.get());
		}
		return resubmitted.get();
	}

}
