package com.kibootia.backend.platform.jobs;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link RetentionPurgeJob} once a day (UTC, at {@code kibootia.jobs.retention-cron}). It takes a database lock, so
 * with several instances one of them purges. Off when {@code kibootia.jobs.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "kibootia.jobs.enabled", havingValue = "true", matchIfMissing = true)
class RetentionPurgeSchedule {

	private final RetentionPurgeJob job;

	RetentionPurgeSchedule(RetentionPurgeJob job) {
		this.job = job;
	}

	@Scheduled(cron = "${kibootia.jobs.retention-cron:0 30 3 * * *}", zone = "UTC")
	@SchedulerLock(name = "retention-purge", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
	void run() {
		this.job.runOnce();
	}

}
