package com.kibootia.backend.platform.events;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.EventPublication.Status;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

	static final Instant T0 = Instant.parse("2026-11-09T10:00:00Z");

	// 5 attempts, waits of 30s, 60s, 120s, ... capped at 10 minutes; a delivery counts as lost after 5 minutes.
	final RetryPolicy policy = new RetryPolicy(5, Duration.ofSeconds(30), Duration.ofMinutes(10), Duration.ofMinutes(5));

	@Test
	@DisplayName("the wait doubles after each failed attempt")
	void backoffDoubles() {
		assertThat(policy.backoffAfter(1)).isEqualTo(Duration.ofSeconds(30));
		assertThat(policy.backoffAfter(2)).isEqualTo(Duration.ofSeconds(60));
		assertThat(policy.backoffAfter(3)).isEqualTo(Duration.ofSeconds(120));
		assertThat(policy.backoffAfter(4)).isEqualTo(Duration.ofSeconds(240));
	}

	@Test
	@DisplayName("the wait never exceeds the maximum")
	void backoffIsCapped() {
		RetryPolicy longer = new RetryPolicy(100, Duration.ofSeconds(30), Duration.ofMinutes(10), Duration.ofMinutes(5));

		assertThat(longer.backoffAfter(6)).isEqualTo(Duration.ofMinutes(10));
		assertThat(longer.backoffAfter(60)).isEqualTo(Duration.ofMinutes(10));
	}

	@Test
	@DisplayName("a failed event is not retried before its wait is over")
	void waitsBeforeRetrying() {
		assertThat(policy.shouldRetry(Status.FAILED, 1, T0, T0.plusSeconds(29))).isFalse();
		assertThat(policy.shouldRetry(Status.FAILED, 1, T0, T0.plusSeconds(30))).isTrue();
		assertThat(policy.shouldRetry(Status.FAILED, 2, T0, T0.plusSeconds(59))).isFalse();
		assertThat(policy.shouldRetry(Status.FAILED, 2, T0, T0.plusSeconds(60))).isTrue();
	}

	@Test
	@DisplayName("an event is given up on after the maximum number of attempts")
	void givesUp() {
		assertThat(policy.shouldRetry(Status.FAILED, 4, T0, T0.plus(Duration.ofDays(1)))).isTrue();
		assertThat(policy.shouldRetry(Status.FAILED, 5, T0, T0.plus(Duration.ofDays(1)))).isFalse();
		assertThat(policy.exhausted(4)).isFalse();
		assertThat(policy.exhausted(5)).isTrue();
	}

	@Test
	@DisplayName("a delivery that never finished is retried only once it is old enough to be lost")
	void staleDeliveries() {
		assertThat(policy.shouldRetry(Status.PUBLISHED, 0, T0, T0.plusSeconds(299))).isFalse();
		assertThat(policy.shouldRetry(Status.PUBLISHED, 0, T0, T0.plusSeconds(300))).isTrue();
		assertThat(policy.shouldRetry(Status.PROCESSING, 1, T0, T0.plusSeconds(120))).isFalse();
		assertThat(policy.shouldRetry(Status.RESUBMITTED, 1, T0, T0.plusSeconds(300))).isTrue();
	}

	@Test
	@DisplayName("a completed event is never retried")
	void completedIsNeverRetried() {
		assertThat(policy.shouldRetry(Status.COMPLETED, 1, T0, T0.plus(Duration.ofDays(1)))).isFalse();
	}

}
