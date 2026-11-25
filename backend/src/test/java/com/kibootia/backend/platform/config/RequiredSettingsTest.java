package com.kibootia.backend.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredSettingsTest {

	private final RequiredSettingsEnvironmentPostProcessor processor = new RequiredSettingsEnvironmentPostProcessor();

	/** An environment that has every required setting except the ones a test then leaves out or changes. */
	private static MockEnvironment complete() {
		return new MockEnvironment().withProperty("spring.datasource.password", "x")
			.withProperty("kibootia.identity.keycloak.client-secret", "y")
			.withProperty("kibootia.files.access-key", "a")
			.withProperty("kibootia.files.secret-key", "s");
	}

	@Test
	void missingStorageKeyFailsAndNamesTheSettingWhileFilesAreOn() {
		MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.password", "x")
			.withProperty("kibootia.identity.keycloak.client-secret", "y")
			.withProperty("kibootia.files.access-key", "a");

		assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
			.isInstanceOf(MissingRequiredSettingException.class)
			.hasMessageContaining("kibootia.files.secret-key")
			.hasMessageContaining("KIBOOTIA_FILES_SECRET_KEY");
	}

	@Test
	void storageKeysAreNotRequiredWhenFilesAreSwitchedOff() {
		MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.password", "x")
			.withProperty("kibootia.identity.keycloak.client-secret", "y")
			.withProperty("kibootia.files.enabled", "false");

		assertThatCode(() -> processor.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
	}

	@Test
	void anUnresolvedStoragePlaceholderFailsWhileFilesAreOn() {
		MockEnvironment environment = complete().withProperty("kibootia.files.access-key", "${NOT_SET_ANYWHERE}");

		assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
			.isInstanceOf(MissingRequiredSettingException.class)
			.hasMessageContaining("kibootia.files.access-key");
	}

	@Test
	void missingPasswordFailsAndNamesTheSetting() {
		MockEnvironment environment = new MockEnvironment().withProperty("kibootia.identity.keycloak.client-secret", "y")
			.withProperty("kibootia.files.enabled", "false");

		assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
				.isInstanceOf(MissingRequiredSettingException.class)
				.hasMessageContaining("spring.datasource.password");
	}

	@Test
	void blankPasswordFails() {
		MockEnvironment environment = complete().withProperty("spring.datasource.password", " ");

		assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
				.isInstanceOf(MissingRequiredSettingException.class);
	}

	@Test
	void unresolvedPlaceholderFails() {
		MockEnvironment environment = complete().withProperty("spring.datasource.password", "${DOES_NOT_EXIST_ANYWHERE}");

		assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
				.isInstanceOf(MissingRequiredSettingException.class);
	}

	@Test
	void everyRequiredSettingPresentPasses() {
		assertThatCode(() -> processor.postProcessEnvironment(complete(), null)).doesNotThrowAnyException();
	}

	@Test
	void missingKeycloakSecretFailsAndNamesTheSetting() {
		MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.password", "x")
			.withProperty("kibootia.files.enabled", "false");

		assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
				.isInstanceOf(MissingRequiredSettingException.class)
				.hasMessageContaining("kibootia.identity.keycloak.client-secret")
				.hasMessageContaining("KEYCLOAK_ADMIN_CLIENT_SECRET");
	}

	@Test
	void analyzerExplainsTheFailure() {
		MissingRequiredSettingException failure = new MissingRequiredSettingException("some.setting", "Set SOME_VAR.");

		FailureAnalysis analysis = new MissingRequiredSettingFailureAnalyzer().analyze(failure);

		assertThat(analysis.getDescription()).contains("some.setting");
		assertThat(analysis.getAction()).contains("SOME_VAR");
	}

	// --- the production guard (Timetia ADR 0005) -------------------------------------------------------------------------

	/** Every address a real deployment must set, and a management port apart from the API port. */
	private static MockEnvironment production() {
		return complete().withProperty("kibootia.environment", "production")
			.withProperty("kibootia.security.issuer", "https://auth.example.org/realms/timetia")
			.withProperty("kibootia.security.jwk-set-uri", "http://keycloak:8080/realms/timetia/protocol/openid-connect/certs")
			.withProperty("kibootia.identity.public-base-url", "https://timetia.example.org")
			.withProperty("kibootia.identity.keycloak.base-url", "http://keycloak:8080")
			.withProperty("management.server.port", "8090");
	}

	@Test
	void developmentDefaultsAreFineWhileTheEnvironmentIsLocal() {
		MockEnvironment environment = complete().withProperty("kibootia.security.issuer", "http://localhost:8180/realms/x")
			.withProperty("management.server.port", "8080");

		assertThatCode(() -> processor.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
	}

	@Test
	void aCompleteProductionEnvironmentStartsAndInternalKeyAddressesAreAllowed() {
		assertThatCode(() -> processor.postProcessEnvironment(production(), null)).doesNotThrowAnyException();
	}

	@Test
	void productionRefusesTheDevelopmentDefaultAddresses() {
		MockEnvironment environment = production()
			.withProperty("kibootia.security.issuer", "http://localhost:8180/realms/timetia")
			.withProperty("kibootia.security.jwk-set-uri", "http://localhost:8180/realms/timetia/protocol/openid-connect/certs")
			.withProperty("kibootia.identity.public-base-url", "http://localhost:3470")
			.withProperty("kibootia.identity.keycloak.base-url", "http://localhost:8180");

		assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null)).isInstanceOf(UnsafeSettingsException.class)
			.satisfies((failure) -> assertThat(((UnsafeSettingsException) failure).getProblems()).hasSize(4)
				.anyMatch((problem) -> problem.startsWith("kibootia.security.issuer"))
				.anyMatch((problem) -> problem.startsWith("kibootia.security.jwk-set-uri"))
				.anyMatch((problem) -> problem.startsWith("kibootia.identity.public-base-url"))
				.anyMatch((problem) -> problem.startsWith("kibootia.identity.keycloak.base-url")));
	}

	@Test
	void productionRefusesAnIssuerThatIsNotHttps() {
		MockEnvironment environment = production().withProperty("kibootia.security.issuer", "http://auth.example.org/realms/x");

		assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null)).isInstanceOf(UnsafeSettingsException.class)
			.hasMessageContaining("kibootia.security.issuer");
	}

	@Test
	void productionRefusesHealthOnTheApiPortOrNoManagementPort() {
		assertThatThrownBy(() -> processor.postProcessEnvironment(production().withProperty("management.server.port", "8080"), null))
			.isInstanceOf(UnsafeSettingsException.class)
			.hasMessageContaining("management.server.port");
		assertThatThrownBy(() -> processor.postProcessEnvironment(production().withProperty("server.port", "9000")
			.withProperty("management.server.port", "9000"), null)).isInstanceOf(UnsafeSettingsException.class);
		MockEnvironment none = complete().withProperty("kibootia.environment", "production")
			.withProperty("kibootia.security.issuer", "https://auth.example.org/realms/timetia")
			.withProperty("kibootia.security.jwk-set-uri", "http://keycloak:8080/certs")
			.withProperty("kibootia.identity.public-base-url", "https://timetia.example.org")
			.withProperty("kibootia.identity.keycloak.base-url", "http://keycloak:8080");
		assertThatThrownBy(() -> processor.postProcessEnvironment(none, null)).isInstanceOf(UnsafeSettingsException.class)
			.hasMessageContaining("management.server.port");
	}

	@Test
	void anUnknownEnvironmentNameFailsSoATypoCannotSwitchTheGuardOff() {
		MockEnvironment environment = complete().withProperty("kibootia.environment", "prod");

		assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null)).isInstanceOf(UnsafeSettingsException.class)
			.hasMessageContaining("kibootia.environment");
	}

	@Test
	void theProductionFailureNamesSettingsAndNeverTheirValues() {
		MockEnvironment environment = production().withProperty("kibootia.security.issuer", "http://secret-host.internal/realms/x");

		assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
			.satisfies((failure) -> assertThat(failure.getMessage()).doesNotContain("secret-host"));
		FailureAnalysis analysis = new UnsafeSettingsFailureAnalyzer()
			.analyze(new UnsafeSettingsException(java.util.List.of("some.setting must be changed")));
		assertThat(analysis.getDescription()).contains("some.setting");
		assertThat(analysis.getAction()).contains("kibootia.environment=local");
	}

}
