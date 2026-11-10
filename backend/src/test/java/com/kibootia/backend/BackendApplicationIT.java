package com.kibootia.backend;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the whole application against MySQL: Flyway migrates from an empty database and Hibernate validates the
 * mapped tables (including the event publication registry) against the migrated schema.
 */
@Tag("startup")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BackendApplicationIT {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void contextLoadsAndMigratesFromEmpty() {
		Integer applied = jdbc.queryForObject(
				"select count(*) from flyway_schema_history where success = 1", Integer.class);
		assertThat(applied).isGreaterThanOrEqualTo(1);
	}

}
