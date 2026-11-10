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
			.withProperty("kibootia.identity.keycloak.client-secret", "y");
	}

	@Test
	void missingPasswordFailsAndNamesTheSetting() {
		MockEnvironment environment = new MockEnvironment().withProperty("kibootia.identity.keycloak.client-secret", "y");

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
		MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.password", "x");

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

}
