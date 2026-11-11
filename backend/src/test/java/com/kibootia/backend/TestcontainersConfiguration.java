package com.kibootia.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Real MySQL for integration tests, the same major version as {@code compose.yaml}, and a fake in place of Keycloak. The
 * connection replaces the datasource settings, so no local password is needed; the fake means no test needs a Keycloak
 * unless it is about the Keycloak adapter itself.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/** Takes the place of the Keycloak adapter wherever an {@code IdentityProviderAdmin} is injected. */
	@Bean
	@Primary
	FakeIdentityProvider fakeIdentityProvider() {
		return new FakeIdentityProvider();
	}

	/**
	 * Takes the place of the S3 adapter wherever an {@code ObjectStorage} is injected, so no test needs a storage server
	 * unless it is about the adapter itself ({@code S3ObjectStorageContractIT}).
	 */
	@Bean
	@Primary
	FakeObjectStorage fakeObjectStorage() {
		return new FakeObjectStorage();
	}

	@Bean
	@ServiceConnection
	MySQLContainer mysql() {
		// The performance schema is diagnostics tooling that tests do not read; leaving it off saves a few hundred MB
		// per container, which matters because several run at once (see spring.properties).
		return new MySQLContainer("mysql:8.4").withCommand("--performance-schema=OFF", "--innodb-buffer-pool-size=64M");
	}

}
