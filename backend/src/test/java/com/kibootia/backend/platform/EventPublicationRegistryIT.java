package com.kibootia.backend.platform;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.kibootia.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Spring Modulith event publication registry works on MySQL with the Flyway-created table: an event
 * published in a transaction is stored, delivered to its listener and marked completed.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, EventPublicationRegistryIT.Listener.class })
class EventPublicationRegistryIT {

	record Ping(String name) {
	}

	@Component
	static class Listener {

		static final AtomicInteger RECEIVED = new AtomicInteger();

		@ApplicationModuleListener
		void on(Ping ping) {
			RECEIVED.incrementAndGet();
		}

	}

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void publishedEventIsStoredDeliveredAndCompleted() throws InterruptedException {
		transactions.executeWithoutResult(status -> publisher.publishEvent(new Ping("registry")));

		long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		Integer completed = 0;
		while (System.nanoTime() < deadline) {
			completed = jdbc.queryForObject(
					"select count(*) from event_publication where completion_date is not null", Integer.class);
			if (completed != null && completed > 0) {
				break;
			}
			Thread.sleep(100);
		}

		assertThat(Listener.RECEIVED.get()).isEqualTo(1);
		assertThat(completed).isEqualTo(1);
	}

}
