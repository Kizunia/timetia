package com.kibootia.backend.platform.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns {@link MissingRequiredSettingException} into a short startup message instead of a stack trace.
 */
class MissingRequiredSettingFailureAnalyzer extends AbstractFailureAnalyzer<MissingRequiredSettingException> {

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, MissingRequiredSettingException cause) {
		return new FailureAnalysis(
				"Required setting '" + cause.getSetting() + "' is not set.",
				cause.getHint(),
				cause);
	}

}
