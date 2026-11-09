package com.kibootia.backend.audit;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * What a module asks the audit log to record (ADR 0046). People are referred to by user ID only: an entry never holds a
 * name, an email address, a secret or a token, so erasing a user needs no rewrite of the append-only log. The actor, the
 * time, the tenant and the trace ID are added by the log itself.
 *
 * <p>The constructor rejects the mistakes that would put personal data in the log: a change on a field named like
 * personal data, and a value that looks like an email address. This is a guard against slips, not a substitute for
 * choosing what to record.
 *
 * @param action a stable code, {@code <module>.<resource>.<verb>}, for example {@code access.role.assigned}
 * @param targetType the kind of record acted on, for example {@code user}
 * @param targetId the record acted on, when there is one
 * @param changes changed fields as field name to non-personal value, for example {@code roles} to {@code +editor}
 * @param outcome {@link #SUCCESS} or the code of the failure
 */
public record AuditEntry(String action, String targetType, UUID targetId, Map<String, String> changes, String outcome) {

	public static final String SUCCESS = "success";

	static final int MAX_CHANGES = 20;

	static final int MAX_VALUE_LENGTH = 200;

	private static final Pattern ACTION = Pattern.compile("[a-z][a-z0-9]*\\.[a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*");

	private static final Pattern TARGET_TYPE = Pattern.compile("[a-z][a-z0-9-]{0,49}");

	private static final Pattern FIELD = Pattern.compile("[a-z][a-zA-Z0-9_.-]{0,49}");

	private static final Pattern OUTCOME = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,99}");

	/** Field names that hold personal data or secrets, in lower case with separators removed. */
	private static final Set<String> FORBIDDEN_FIELDS = Set.of("email", "emailaddress", "mail", "name", "firstname",
			"lastname", "fullname", "displayname", "username", "phone", "phonenumber", "address", "password", "secret",
			"token", "accesstoken", "refreshtoken", "apikey");

	public AuditEntry {
		require(matches(ACTION, action),
				"The action must look like <module>.<resource>.<verb>, for example access.role.assigned");
		require(matches(TARGET_TYPE, targetType), "The target type must be lower-case words joined by hyphens");
		require(matches(OUTCOME, outcome), "The outcome must be 'success' or a failure code");
		changes = (changes == null) ? Map.of() : validated(changes);
	}

	/** A successful action on a record. */
	public static AuditEntry of(String action, String targetType, UUID targetId) {
		return new AuditEntry(action, targetType, targetId, Map.of(), SUCCESS);
	}

	/** The same entry with one more changed field. */
	public AuditEntry withChange(String field, String value) {
		Map<String, String> more = new LinkedHashMap<>(this.changes);
		more.put(field, value);
		return new AuditEntry(this.action, this.targetType, this.targetId, more, this.outcome);
	}

	/** The same entry recorded as failed with the given code. */
	public AuditEntry failedWith(String code) {
		return new AuditEntry(this.action, this.targetType, this.targetId, this.changes, code);
	}

	private static Map<String, String> validated(Map<String, String> changes) {
		require(changes.size() <= MAX_CHANGES, "An entry holds at most " + MAX_CHANGES + " changes");
		Map<String, String> copy = new LinkedHashMap<>();
		changes.forEach((field, value) -> {
			require(field != null && FIELD.matcher(field).matches(),
					"A changed field must be a short camelCase or kebab-case name");
			String normalised = field.replaceAll("[-_.]", "").toLowerCase(Locale.ROOT);
			require(!FORBIDDEN_FIELDS.contains(normalised),
					"'" + field + "' is personal data or a secret and is never recorded; refer to people by user ID");
			require(value != null && !value.isBlank() && value.length() <= MAX_VALUE_LENGTH,
					"The value of '" + field + "' must be 1 to " + MAX_VALUE_LENGTH + " characters");
			require(value.indexOf('@') < 0, "The value of '" + field + "' looks like an email address");
			copy.put(field, value);
		});
		return Map.copyOf(copy);
	}

	private static boolean matches(Pattern pattern, String value) {
		return value != null && pattern.matcher(value).matches();
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

}
