package com.kibootia.backend.platform.jobs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.kibootia.backend.Await;
import com.kibootia.backend.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/** With jobs switched on, the scheduled jobs really take their lock in the database. */
@SpringBootTest(properties = "kibootia.jobs.enabled=true")
@Import(TestcontainersConfiguration.class)
class JobsEnabledIT {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	@DisplayName("a scheduled job runs under a lock row in the database")
	void scheduledJobTakesItsLock() {
		Await.until("the event retry job to take its lock", () -> jdbc
			.queryForObject("select count(*) from shedlock where name = 'event-retry'", Integer.class) >= 1);

		assertThat(jdbc.queryForObject("select locked_by from shedlock where name = 'event-retry'", String.class))
			.isNotBlank();
	}

}
