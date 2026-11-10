package com.kibootia.backend.platform.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * Fails startup with a message naming the setting when a required value is missing. Secret values are never
 * included in the message.
 */
class RequiredSettingsEnvironmentPostProcessor implements EnvironmentPostProcessor {

	/** Required setting mapped to the hint shown to the developer, checked in this order. */
	static final Map<String, String> REQUIRED = required();

	private static Map<String, String> required() {
		Map<String, String> required = new LinkedHashMap<>();
		required.put("spring.datasource.password",
				"Set MYSQL_PASSWORD (run 'pnpm bootstrap' to create .env from .env.example, or export the variable).");
		required.put("kibootia.identity.keycloak.client-secret",
				"Set KEYCLOAK_ADMIN_CLIENT_SECRET, the secret of the kibootia-admin service-account client (run "
						+ "'pnpm bootstrap' to create .env from .env.example, or export the variable).");
		return required;
	}

	/**
	 * Required only while the files module is on (ADR 0024): with {@code kibootia.files.enabled=false} the application
	 * starts without any storage setting.
	 */
	static final Map<String, String> REQUIRED_WITH_FILES = requiredWithFiles();

	private static Map<String, String> requiredWithFiles() {
		Map<String, String> required = new LinkedHashMap<>();
		required.put("kibootia.files.access-key",
				"Set KIBOOTIA_FILES_ACCESS_KEY, the access key of the object store (run 'pnpm bootstrap' to create .env "
						+ "from .env.example, export the variable, or set kibootia.files.enabled=false to run without files).");
		required.put("kibootia.files.secret-key",
				"Set KIBOOTIA_FILES_SECRET_KEY, the secret key of the object store (run 'pnpm bootstrap' to create .env "
						+ "from .env.example, export the variable, or set kibootia.files.enabled=false to run without files).");
		return required;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		REQUIRED.forEach((setting, hint) -> require(environment, setting, hint));
		if (filesEnabled(environment)) {
			REQUIRED_WITH_FILES.forEach((setting, hint) -> require(environment, setting, hint));
		}
	}

	private static void require(ConfigurableEnvironment environment, String setting, String hint) {
		if (!isPresent(environment, setting)) {
			throw new MissingRequiredSettingException(setting, hint);
		}
	}

	private static boolean filesEnabled(ConfigurableEnvironment environment) {
		return !"false".equalsIgnoreCase(environment.getProperty("kibootia.files.enabled", "true").strip());
	}

	private static boolean isPresent(ConfigurableEnvironment environment, String setting) {
		try {
			return StringUtils.hasText(environment.getProperty(setting));
		}
		catch (IllegalArgumentException unresolvedPlaceholder) {
			return false;
		}
	}

}
