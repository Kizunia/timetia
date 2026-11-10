package com.kibootia.backend.files.application;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link ReconciliationJob} once a day (UTC, at {@code kibootia.files.reconcile-cron}). It takes a database lock, so
 * with several instances one of them reconciles. Off, like every job, when {@code kibootia.jobs.enabled=false}.
 */
@Component
@ConditionalOnFilesEnabled
@ConditionalOnProperty(name = "kibootia.jobs.enabled", havingValue = "true", matchIfMissing = true)
class ReconciliationSchedule {

	private final ReconciliationJob job;

	ReconciliationSchedule(ReconciliationJob job) {
		this.job = job;
	}

	@Scheduled(cron = "${kibootia.files.reconcile-cron:0 0 4 * * *}", zone = "UTC")
	@SchedulerLock(name = "files-reconciliation", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
	void run() {
		this.job.runOnce();
	}

}
