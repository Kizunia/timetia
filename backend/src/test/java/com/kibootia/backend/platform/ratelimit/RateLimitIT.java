package com.kibootia.backend.platform.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.kibootia.backend.AdminApiTest;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The in-app rate limiter on the sensitive endpoints (API conventions, threat model): invitations, verification mail and
 * administrative actions answer 429 with {@code Retry-After} once a user's allowance is used up, and one user's allowance
 * does not affect another's.
 */
@SpringBootTest(properties = { "kibootia.identity.bootstrap-admin-email=" + AdminApiTest.ADMIN_EMAIL,
		"kibootia.identity.bootstrap-invite.enabled=false",
		"kibootia.rate-limit.limits.invitations.capacity=2", "kibootia.rate-limit.limits.invitations.period=PT1H",
		"kibootia.rate-limit.limits.verification-resend.capacity=1",
		"kibootia.rate-limit.limits.verification-resend.period=PT1H", "kibootia.rate-limit.limits.admin-actions.capacity=3",
		"kibootia.rate-limit.limits.admin-actions.period=PT1H", "kibootia.events.initial-backoff=PT0S",
		"kibootia.events.max-backoff=PT0S" })
class RateLimitIT extends AdminApiTest {

	@Test
	@DisplayName("the third invitation within the allowance is 429 with Retry-After, and another user is not affected")
	void invitations() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		String inviters = createRole("Inviters", "identity.users.invite");
		setRoles(ALICE, builtInRoleId("USER"), inviters);

		post(admin(), "/api/v1/users", "{\"email\":\"one@kibootia.test\"}").andExpect(status().isCreated());
		post(admin(), "/api/v1/users", "{\"email\":\"two@kibootia.test\"}").andExpect(status().isCreated());
		post(admin(), "/api/v1/users", "{\"email\":\"three@kibootia.test\"}").andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"))
			.andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")))
			.andExpect(jsonPath("$.code").value("platform.rate_limited"));

		org.assertj.core.api.Assertions.assertThat(count("select count(*) from audit_entry "
				+ "where action = 'security.rate-limit.exceeded' and changes like '%invitations%'"))
			.as("the refusal is recorded as a security event")
			.isEqualTo(1);

		post(user(ALICE), "/api/v1/users", "{\"email\":\"four@kibootia.test\"}").andExpect(status().isCreated());
		org.assertj.core.api.Assertions.assertThat(this.keycloak.countCalls("createUser")).as("a refused invitation creates nothing")
			.isEqualTo(3);
	}

	@Test
	@DisplayName("asking for the verification email twice is 429 the second time")
	void verificationResend() throws Exception {
		this.keycloak.register(BOB, true, false);
		get(unverified(BOB), "/api/v1/me").andExpect(status().isOk());

		post(unverified(BOB), "/api/v1/me/verification-email", null).andExpect(status().isNoContent());
		post(unverified(BOB), "/api/v1/me/verification-email", null).andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"));
	}

	@Test
	@DisplayName("administrative actions are limited per user, and a refused one changes nothing")
	void administrativeActions() throws Exception {
		ensureAdmin();
		signIn(SECOND_ADMIN);
		makeAdministrator(SECOND_ADMIN);
		var second = user(SECOND_ADMIN);
		for (int i = 1; i <= 3; i++) {
			post(second, "/api/v1/roles", "{\"name\":\"Role " + i + "\",\"description\":\"\",\"permissions\":[]}")
				.andExpect(status().isCreated());
		}

		post(second, "/api/v1/roles", "{\"name\":\"Role 4\",\"description\":\"\",\"permissions\":[]}")
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")));
		org.assertj.core.api.Assertions.assertThat(count("select count(*) from role where name = 'Role 4'")).isZero();
		// Reading is not an administrative action and is not limited.
		get(second, "/api/v1/roles").andExpect(status().isOk());
	}

}
