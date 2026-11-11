package com.kibootia.backend.platform.config;

/**
 * Thrown at startup when a required setting is absent or cannot be resolved.
 */
public class MissingRequiredSettingException extends RuntimeException {

	private final String setting;

	private final String hint;

	public MissingRequiredSettingException(String setting, String hint) {
		super("Required setting '" + setting + "' is missing. " + hint);
		this.setting = setting;
		this.hint = hint;
	}

	public String getSetting() {
		return this.setting;
	}

	public String getHint() {
		return this.hint;
	}

}
