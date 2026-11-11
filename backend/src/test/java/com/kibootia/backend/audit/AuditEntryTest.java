package com.kibootia.backend.audit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AuditEntryTest {

	static final UUID TARGET = UUID.fromString("33333333-3333-7333-8333-333333333333");

	@Test
	@DisplayName("a plain entry is a success with no changes")
	void plainEntry() {
		AuditEntry entry = AuditEntry.of("access.role.assigned", "user", TARGET);

		assertThat(entry.outcome()).isEqualTo(AuditEntry.SUCCESS);
		assertThat(entry.changes()).isEmpty();
		assertThat(entry.targetId()).isEqualTo(TARGET);
	}

	@Test
	@DisplayName("changes and a failure code can be added without altering the original")
	void builders() {
		AuditEntry base = AuditEntry.of("access.role.assigned", "user", TARGET);
		AuditEntry changed = base.withChange("roles", "+editor").failedWith("access.last_admin");

		assertThat(base.changes()).isEmpty();
		assertThat(changed.changes()).containsEntry("roles", "+editor");
		assertThat(changed.outcome()).isEqualTo("access.last_admin");
	}

	@Test
	@DisplayName("a target is optional")
	void targetIsOptional() {
		assertThat(AuditEntry.of("audit.entries.purged", "audit-entry", null).targetId()).isNull();
	}

	@Test
	@DisplayName("an action must look like <module>.<resource>.<verb>")
	void actionFormat() {
		for (String bad : List.of("assigned", "access.assigned", "Access.role.assigned", "access.role.Assigned",
				"access.role.assigned.again", "", "access role assigned")) {
			assertThatIllegalArgumentException().as(bad)
				.isThrownBy(() -> AuditEntry.of(bad, "user", TARGET));
		}
		assertThat(AuditEntry.of("identity.user.erased", "user", TARGET).action()).isEqualTo("identity.user.erased");
	}

	@Test
	@DisplayName("a null action or target type is rejected")
	void nullParts() {
		assertThatIllegalArgumentException().isThrownBy(() -> AuditEntry.of(null, "user", TARGET));
		assertThatIllegalArgumentException().isThrownBy(() -> AuditEntry.of("access.role.assigned", null, TARGET));
	}

	@Test
	@DisplayName("a change on a field named like personal data or a secret is refused")
	void personalDataFieldsAreRefused() {
		AuditEntry entry = AuditEntry.of("identity.user.updated", "user", TARGET);
		for (String field : List.of("email", "emailAddress", "e-mail", "name", "firstName", "last_name", "username",
				"displayName", "phone", "address", "password", "secret", "token", "accessToken", "apiKey")) {
			String valid = field.replace("-", "").replace("_", "");
			assertThatIllegalArgumentException().as(field)
				.isThrownBy(() -> entry.withChange(field.equals("e-mail") ? "e-mail" : valid, "x"))
				.withMessageContaining("personal data");
		}
	}

	@Test
	@DisplayName("a value that looks like an email address is refused")
	void emailValuesAreRefused() {
		AuditEntry entry = AuditEntry.of("identity.user.updated", "user", TARGET);

		assertThatIllegalArgumentException().isThrownBy(() -> entry.withChange("contact", "ann@example.com"))
			.withMessageContaining("email");
	}

	@Test
	@DisplayName("field names and values are bounded")
	void bounds() {
		AuditEntry entry = AuditEntry.of("identity.user.updated", "user", TARGET);

		assertThatIllegalArgumentException().isThrownBy(() -> entry.withChange("Roles", "x"));
		assertThatIllegalArgumentException().isThrownBy(() -> entry.withChange("roles", " "));
		assertThatIllegalArgumentException().isThrownBy(() -> entry.withChange("roles", "x".repeat(201)));
		assertThat(entry.withChange("roles", "x".repeat(200)).changes()).hasSize(1);

		Map<String, String> many = new HashMap<>();
		for (int i = 0; i < 21; i++) {
			many.put("field" + i, "v");
		}
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new AuditEntry("identity.user.updated", "user", TARGET, many, AuditEntry.SUCCESS));
	}

	@Test
	@DisplayName("the changes cannot be altered after the entry is created")
	void changesAreImmutable() {
		AuditEntry entry = AuditEntry.of("access.role.assigned", "user", TARGET).withChange("roles", "+editor");

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> entry.changes().put("x", "y"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

}
