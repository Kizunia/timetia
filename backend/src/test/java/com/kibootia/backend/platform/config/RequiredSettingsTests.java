package com.kibootia.backend.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredSettingsTests {

	private final RequiredSettingsEnvironmentPostProcessor processor = new RequiredSettingsEnvironmentPostProcessor();

	@Test
	void missingPasswordFailsAndNamesTheSetting() {
		MockEnvironment environment = new MockEnvironment();

		assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
				.isInstanceOf(MissingRequiredSettingException.class)
				.hasMessageContaining("spring.datasource.password");
	}

	@Test
	void blankPasswordFails() {
		MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.password", " ");

		assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
				.isInstanceOf(MissingRequiredSettingException.class);
	}

	@Test
	void unresolvedPlaceholderFails() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("spring.datasource.password", "${DOES_NOT_EXIST_ANYWHERE}");

		assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
				.isInstanceOf(MissingRequiredSettingException.class);
	}

	@Test
	void presentPasswordPasses() {
		MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.password", "x");

		assertThatCode(() -> processor.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
	}

	@Test
	void analyzerExplainsTheFailure() {
		MissingRequiredSettingException failure = new MissingRequiredSettingException("some.setting", "Set SOME_VAR.");

		FailureAnalysis analysis = new MissingRequiredSettingFailureAnalyzer().analyze(failure);

		assertThat(analysis.getDescription()).contains("some.setting");
		assertThat(analysis.getAction()).contains("SOME_VAR");
	}

}
