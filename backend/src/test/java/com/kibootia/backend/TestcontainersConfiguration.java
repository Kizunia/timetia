package com.kibootia.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Real MySQL for integration tests, the same major version as {@code compose.yaml}. The connection replaces the
 * datasource settings, so no local password is needed.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer mysql() {
		// The performance schema is diagnostics tooling that tests do not read; leaving it off saves a few hundred MB
		// per container, which matters because several run at once (see spring.properties).
		return new MySQLContainer("mysql:8.4").withCommand("--performance-schema=OFF", "--innodb-buffer-pool-size=64M");
	}

}
