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
		return new MySQLContainer("mysql:8.4");
	}

}
