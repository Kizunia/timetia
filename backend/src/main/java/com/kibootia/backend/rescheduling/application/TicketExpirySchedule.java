package com.kibootia.backend.rescheduling.application;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link TicketExpiryJob} every five minutes (UTC, at {@code kibootia.rescheduling.expiry-cron}). It takes a database
 * lock, so with several instances one of them expires. Off when {@code kibootia.jobs.enabled=false}. A ticket is also expired
 * on the spot when someone acts on it too late, so the interval only decides how soon an untouched ticket shows as expired.
 */
@Component
@ConditionalOnProperty(name = "kibootia.jobs.enabled", havingValue = "true", matchIfMissing = true)
class TicketExpirySchedule {

	private final TicketExpiryJob job;

	TicketExpirySchedule(TicketExpiryJob job) {
		this.job = job;
	}

	@Scheduled(cron = "${kibootia.rescheduling.expiry-cron:0 */5 * * * *}", zone = "UTC")
	@SchedulerLock(name = "ticket-expiry", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
	void run() {
		this.job.runOnce();
	}

}
