package com.kibootia.backend.audit.application;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.kibootia.backend.AdminApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refusals of signed-in users are recorded as security events (Timetia ADR 0005): who, which permission or code, and
 * nothing personal. An unauthenticated request is not recorded, because anyone could cause unlimited writes.
 */
class SecurityEventsIT extends AdminApiTest {

	private List<Map<String, Object>> events() {
		return this.jdbc.queryForList("""
				select hex(actor_id) as actor, action, target_type, hex(target_id) as target, outcome, changes
				from audit_entry where action like 'security.%' order by occurred_at
				""");
	}

	@Test
	@DisplayName("a refused permission is recorded once, with the user and the permission and no personal data")
	void refusedPermission() throws Exception {
		ensureAdmin();
		signIn(ALICE);

		get(user(ALICE), "/api/v1/roles").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("platform.forbidden"));

		assertThat(events()).hasSize(1).first().satisfies((event) -> {
			assertThat(event.get("actor")).asString().isEqualToIgnoringCase(hex(ALICE));
			assertThat(event.get("action")).isEqualTo("security.access.denied");
			assertThat(event.get("outcome")).isEqualTo("forbidden");
			assertThat(event.get("changes")).asString().contains("access.roles.view");
		});
		assertThat(count("select count(*) from audit_entry where changes like '%@%'")).as("no personal data").isZero();
	}

	@Test
	@DisplayName("a caller who has not verified their email address is refused and the refusal says why")
	void unverifiedCaller() throws Exception {
		this.keycloak.register(BOB, true, false);
		get(unverified(BOB), "/api/v1/me").andExpect(status().isOk());

		get(unverified(BOB), "/api/v1/roles").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("platform.email_not_verified"));

		assertThat(events()).hasSize(1).first().satisfies((event) -> {
			assertThat(event.get("actor")).asString().isEqualToIgnoringCase(hex(BOB));
			assertThat(event.get("outcome")).isEqualTo("email_not_verified");
		});
	}

	@Test
	@DisplayName("a disabled account that tries again is refused at the gate and the refusal is recorded against the account")
	void disabledAccount() throws Exception {
		ensureAdmin();
		signIn(CAROL);
		this.keycloak.register(CAROL, true, true);
		post(admin(), "/api/v1/users/" + CAROL + "/disable", null).andExpect(status().isOk());

		get(user(CAROL), "/api/v1/me").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("identity.account_disabled"));

		assertThat(events()).hasSize(1).first().satisfies((event) -> {
			assertThat(event.get("actor")).asString().isEqualToIgnoringCase(hex(CAROL));
			assertThat(event.get("action")).isEqualTo("security.account.refused");
			assertThat(event.get("target")).asString().isEqualToIgnoringCase(hex(CAROL));
			assertThat(event.get("outcome")).isEqualTo("identity.account_disabled");
		});
	}

	@Test
	@DisplayName("a request without a token is 401 and writes nothing")
	void unauthenticatedRequestsAreNotRecorded() throws Exception {
		this.mvc.perform(MockMvcRequestBuilders.get("/api/v1/roles")).andExpect(status().isUnauthorized());
		this.mvc.perform(MockMvcRequestBuilders.get("/api/v1/roles").header("Authorization", "Bearer not-a-token"))
			.andExpect(status().isUnauthorized());

		assertThat(count("select count(*) from audit_entry")).isZero();
	}

	@Test
	@DisplayName("an allowed request is not recorded as a security event")
	void allowedRequestsAreNotRecorded() throws Exception {
		ensureAdmin();
		get(admin(), "/api/v1/roles").andExpect(status().isOk());

		assertThat(events()).isEmpty();
	}

}
