package com.kibootia.backend.platform.fixtures;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Lets a test say what time it is. Whether a lecture has started, and whether a ticket has expired, depend on the time, and
 * the lectures a test builds are on fixed dates, so a test needs to put "now" before them, or after. The application reads
 * time from one {@link Clock} (see the platform's time configuration); this one takes its place wherever it is imported, and
 * is set back to a time before the test timetable by whoever uses it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MutableClockConfiguration {

	@Bean
	@Primary
	MutableClock mutableClock() {
		return new MutableClock(Instant.parse("2026-07-01T00:00:00Z"));
	}

	/** A clock that stands still at an instant until a test moves it. */
	public static final class MutableClock extends Clock {

		private final AtomicReference<Instant> now;

		MutableClock(Instant start) {
			this.now = new AtomicReference<>(start);
		}

		public void set(Instant instant) {
			this.now.set(instant);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.now.get();
		}

	}

}
