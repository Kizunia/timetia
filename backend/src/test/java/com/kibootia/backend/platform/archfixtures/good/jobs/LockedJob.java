package com.kibootia.backend.platform.archfixtures.good.jobs;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.scheduling.annotation.Scheduled;

/** A scheduled method under a lock. Rule 9 must accept it. */
public class LockedJob {

	@Scheduled(fixedDelay = 1000)
	@SchedulerLock(name = "fixtures-locked-job")
	public void run() {
	}

}
