package com.kibootia.backend.platform.archfixtures.bad.jobs;

import org.springframework.scheduling.annotation.Scheduled;

/** A scheduled method with no lock: on several instances it would run on all of them at once. Rule 9 must name it. */
public class UnlockedJob {

	@Scheduled(fixedDelay = 1000)
	public void run() {
	}

}
