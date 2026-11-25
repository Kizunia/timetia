package com.kibootia.backend.platform.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Turns {@link UnsafeSettingsException} into a short startup message instead of a stack trace. */
class UnsafeSettingsFailureAnalyzer extends AbstractFailureAnalyzer<UnsafeSettingsException> {

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, UnsafeSettingsException cause) {
		return new FailureAnalysis(
				"kibootia.environment is 'production' and these settings are not safe: "
						+ String.join("; ", cause.getProblems()) + ".",
				"Set each of them for the real deployment, or run with kibootia.environment=local.", cause);
	}

}
