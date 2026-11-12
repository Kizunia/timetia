package com.kibootia.backend.identity.application;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The one rule for what counts as an email address and how it is compared, so an invitation, an import and a lookup
 * agree on who is the same person. Deliberately simple: Keycloak has the final say, and the address is only used to
 * find the account.
 */
final class EmailAddresses {

	private static final Pattern EMAIL = Pattern.compile("[^@\\s]{1,64}@[^@\\s]{1,255}");

	private EmailAddresses() {
	}

	/** The address stripped and lower-cased, or empty when it is not shaped like an email address. */
	static Optional<String> normalize(String raw) {
		String normal = (raw == null) ? "" : raw.strip().toLowerCase(Locale.ROOT);
		return EMAIL.matcher(normal).matches() ? Optional.of(normal) : Optional.empty();
	}

}
