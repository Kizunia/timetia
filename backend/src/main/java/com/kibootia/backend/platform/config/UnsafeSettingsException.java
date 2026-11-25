package com.kibootia.backend.platform.config;

import java.util.List;

/**
 * Thrown at startup when the application is told it runs in production and a setting is still the development default
 * or would expose something (Timetia ADR 0005). The messages name settings, never their values.
 */
public class UnsafeSettingsException extends RuntimeException {

	private final List<String> problems;

	public UnsafeSettingsException(List<String> problems) {
		super("Unsafe settings for production: " + String.join("; ", problems));
		this.problems = List.copyOf(problems);
	}

	public List<String> getProblems() {
		return this.problems;
	}

}
