package com.kibootia.backend.platform.events;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link EventRetryJob} on a timer. It takes a database lock, so with several instances one of them retries. Off
 * when {@code kibootia.jobs.enabled=false}, which tests use so a job only runs when a test asks for it.
 */
@Component
@ConditionalOnProperty(name = "kibootia.jobs.enabled", havingValue = "true", matchIfMissing = true)
class EventRetrySchedule {

	private final EventRetryJob job;

	EventRetrySchedule(EventRetryJob job) {
		this.job = job;
	}

	@Scheduled(fixedDelayString = "${kibootia.events.poll-interval:PT30S}")
	@SchedulerLock(name = "event-retry", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
	void run() {
		this.job.runOnce();
	}

}
