package com.kibootia.backend.platform.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
				"Set KEYCLOAK_ADMIN_CLIENT_SECRET, the secret of the timetia-admin service-account client (run "
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

	/** {@code local} (the default) or {@code production}. In production the settings are checked for development defaults. */
	static final String ENVIRONMENT = "kibootia.environment";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		REQUIRED.forEach((setting, hint) -> require(environment, setting, hint));
		if (filesEnabled(environment)) {
			REQUIRED_WITH_FILES.forEach((setting, hint) -> require(environment, setting, hint));
		}
		requireSafeForProduction(environment);
	}

	/**
	 * With {@code kibootia.environment=production}, refuses to start on the development defaults (Timetia ADR 0005): a
	 * token issuer or web address that is not https or points at this machine, key and Keycloak addresses that point at
	 * this machine, or health served on the API port. The value must be {@code local} or {@code production}, so a typo
	 * cannot switch the check off.
	 */
	private static void requireSafeForProduction(ConfigurableEnvironment environment) {
		String mode = environment.getProperty(ENVIRONMENT, "local").strip().toLowerCase(Locale.ROOT);
		if (mode.equals("local")) {
			return;
		}
		List<String> problems = new ArrayList<>();
		if (!mode.equals("production")) {
			problems.add(ENVIRONMENT + " must be 'local' or 'production'");
		}
		else {
			requirePublicHttps(environment, "kibootia.security.issuer", problems);
			requirePublicHttps(environment, "kibootia.identity.public-base-url", problems);
			requireNotLocal(environment, "kibootia.security.jwk-set-uri", problems);
			requireNotLocal(environment, "kibootia.identity.keycloak.base-url", problems);
			String management = environment.getProperty("management.server.port", "");
			String server = environment.getProperty("server.port", "8080");
			if (!StringUtils.hasText(management) || management.strip().equals(server.strip())) {
				problems.add("management.server.port must be set and different from server.port, so health stays off the API port");
			}
		}
		if (!problems.isEmpty()) {
			throw new UnsafeSettingsException(problems);
		}
	}

	private static void requirePublicHttps(ConfigurableEnvironment environment, String setting, List<String> problems) {
		URI uri = uriOf(environment, setting);
		if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || isLocal(uri)) {
			problems.add(setting + " must be an https address that is not on this machine");
		}
	}

	private static void requireNotLocal(ConfigurableEnvironment environment, String setting, List<String> problems) {
		URI uri = uriOf(environment, setting);
		if (uri == null || isLocal(uri)) {
			problems.add(setting + " must be an address that is not on this machine");
		}
	}

	private static URI uriOf(ConfigurableEnvironment environment, String setting) {
		try {
			String value = environment.getProperty(setting);
			return StringUtils.hasText(value) ? URI.create(value.strip()) : null;
		}
		catch (IllegalArgumentException invalidUriOrPlaceholder) {
			return null;
		}
	}

	private static boolean isLocal(URI uri) {
		String host = (uri.getHost() == null) ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		return host.isEmpty() || host.equals("localhost") || host.equals("127.0.0.1") || host.equals("0.0.0.0")
				|| host.equals("[::1]") || host.equals("::1") || host.endsWith(".localhost");
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
