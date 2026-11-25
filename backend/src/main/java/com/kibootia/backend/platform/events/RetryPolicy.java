package com.kibootia.backend.platform.events;

import java.time.Duration;
import java.time.Instant;

import org.springframework.modulith.events.EventPublication.Status;

/**
 * When the retry job redelivers an event (ADR 0031): a failed event waits longer after each attempt, doubling from
 * {@code initialBackoff} up to {@code maxBackoff}, and is given up on after {@code maxAttempts}. A delivery that never
 * finished and never failed (the instance died) is treated as lost once it is older than {@code staleAfter}. Given-up
 * events stay in the registry as failed, where an administrator can see and retry them.
 */
final class RetryPolicy {

	private final int maxAttempts;

	private final Duration initialBackoff;

	private final Duration maxBackoff;

	private final Duration staleAfter;

	RetryPolicy(EventsProperties properties) {
		this(properties.maxAttempts(), properties.initialBackoff(), properties.maxBackoff(), properties.staleAfter());
	}

	RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff, Duration staleAfter) {
		this.maxAttempts = maxAttempts;
		this.initialBackoff = initialBackoff;
		this.maxBackoff = maxBackoff;
		this.staleAfter = staleAfter;
	}

	/**
	 * @param attempts deliveries made so far, as the registry counts them
	 * @param lastAttemptAt when the latest delivery started
	 */
	boolean shouldRetry(Status status, int attempts, Instant lastAttemptAt, Instant now) {
		if (status == Status.COMPLETED || exhausted(attempts)) {
			return false;
		}
		Duration wait = (status == Status.FAILED) ? backoffAfter(attempts) : this.staleAfter;
		return !now.isBefore(lastAttemptAt.plus(wait));
	}

	/** True when the event has been tried as often as allowed. */
	boolean exhausted(int attempts) {
		return attempts >= this.maxAttempts;
	}

	/** The wait after the given number of failed attempts: the initial backoff, doubled for each earlier failure. */
	Duration backoffAfter(int attempts) {
		long doublings = Math.max(0, attempts - 1);
		Duration wait = this.initialBackoff;
		for (long i = 0; i < doublings && wait.compareTo(this.maxBackoff) < 0; i++) {
			wait = wait.multipliedBy(2);
		}
		return wait.compareTo(this.maxBackoff) > 0 ? this.maxBackoff : wait;
	}

}
