package com.kibootia.backend.platform.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one clock the application reads time from. Code takes a {@link Clock} instead of calling {@code Instant.now()},
 * so tests can fix or move time (retention, expiry, backoff). All timestamps are UTC.
 */
@Configuration(proxyBeanMethods = false)
class TimeConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
