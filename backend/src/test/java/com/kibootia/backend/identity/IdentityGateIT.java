package com.kibootia.backend.identity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.Await;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The request gate and the signed-in user's own account (ADR 0041): a profile created on first use, the built-in role,
 * name and email refreshed from the token, a disabled or deleted user refused from the next request, and what an
 * unverified user may and may not do.
 */
class IdentityGateIT extends AdminApiTest {

	private String statusOf(UUID user) {
		return this.jdbc.queryForObject("select status from user_profile where id = unhex(?)", String.class, hex(user));
	}

	private static RequestPostProcessor tokenWith(UUID user, String email, String givenName, Instant issuedAt) {
		return jwt().jwt((token) -> token.subject(user.toString())
			.claim("email", email)
			.claim("email_verified", true)
			.claim("preferred_username", "alice")
			.claim("given_name", givenName)
			.claim("family_name", "Example")
			.issuedAt(issuedAt));
	}

	// --- first use -------------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("the first request creates the profile from the token and gives the user the built-in user role")
	void firstRequestCreatesTheProfile() throws Exception {
		get(tokenWith(ALICE, "alice@kibootia.test", "Alice", Instant.now()), "/api/v1/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(ALICE.toString()))
			.andExpect(jsonPath("$.email").value("alice@kibootia.test"))
			.andExpect(jsonPath("$.name").value("Alice Example"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.roles[*].name", contains("User")))
			.andExpect(jsonPath("$.permissions", hasItems("widgets.widgets.create", "widgets.widgets.view")))
			.andExpect(jsonPath("$.permissions", not(hasItem("identity.profiles.view"))));
		get(tokenWith(ALICE, "alice@kibootia.test", "Alice", Instant.now()), "/api/v1/me").andExpect(status().isOk());

		assertThat(count("select count(*) from user_profile where id = unhex(?)", hex(ALICE))).isEqualTo(1);
		assertThat(count("select count(*) from role_assignment where user_id = unhex(?)", hex(ALICE))).isEqualTo(1);
		assertThat(this.keycloak.countCalls("sendVerificationEmail")).as("a verified user gets no mail").isZero();
	}

	@Test
	@DisplayName("many first requests at the same moment still create one profile")
	void concurrentFirstRequestsCreateOneProfile() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			CountDownLatch go = new CountDownLatch(1);
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				results.add(pool.submit(() -> {
					go.await();
					return get(user(ALICE), "/api/v1/me").andReturn().getResponse().getStatus();
				}));
			}
			go.countDown();
			for (Future<Integer> result : results) {
				assertThat(result.get()).isEqualTo(200);
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(count("select count(*) from user_profile where id = unhex(?)", hex(ALICE))).isEqualTo(1);
		assertThat(count("select count(*) from role_assignment where user_id = unhex(?)", hex(ALICE))).isEqualTo(1);
	}

	@Test
	@DisplayName("a newer token's name and email are copied into the profile, an older one's are not")
	void profileIsRefreshedFromNewerTokensOnly() throws Exception {
		Instant now = Instant.now();
		get(tokenWith(ALICE, "alice@kibootia.test", "Alice", now), "/api/v1/me").andExpect(status().isOk());

		get(tokenWith(ALICE, "alice.new@kibootia.test", "Alicia", now.plusSeconds(60)), "/api/v1/me")
			.andExpect(status().isOk());
		assertThat(this.jdbc.queryForObject("select email from user_profile where id = unhex(?)", String.class, hex(ALICE)))
			.isEqualTo("alice.new@kibootia.test");

		get(tokenWith(ALICE, "alice.stale@kibootia.test", "Stale", now.plusSeconds(10)), "/api/v1/me")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("alice.new@kibootia.test"))
			.andExpect(jsonPath("$.name").value("Alicia Example"));
	}

	@Test
	@DisplayName("a user Keycloak does not know is 401, and a failing Keycloak is 503 and creates nothing")
	void keycloakDecidesWhoIsNew() throws Exception {
		this.keycloak.goneFromKeycloak(ALICE);
		get(user(ALICE), "/api/v1/me").andExpect(status().isUnauthorized());

		this.keycloak.register(BOB, false, true);
		get(user(BOB), "/api/v1/me").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("identity.account_disabled"));

		this.keycloak.failEverything();
		get(user(CAROL), "/api/v1/me").andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("platform.service_unavailable"));

		assertThat(count("select count(*) from user_profile")).isZero();
	}

	@Test
	@DisplayName("a user whose token subject is not an ID is 401")
	void subjectMustBeAnId() throws Exception {
		get(jwt().jwt((token) -> token.subject("not-a-uuid").claim("email_verified", true)), "/api/v1/me")
			.andExpect(status().isUnauthorized());
	}

	// --- the bootstrap administrator -----------------------------------------------------------------------------------

	@Test
	@DisplayName("the bootstrap address becomes the first administrator, audited, once, and only with a verified address")
	void bootstrapAdministrator() throws Exception {
		get(token(CAROL, ADMIN_EMAIL, false), "/api/v1/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.roles[*].name", contains("User")));
		get(token(BOB, "bob@kibootia.test", true), "/api/v1/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.roles[*].name", contains("User")));

		get(admin(), "/api/v1/me").andExpect(jsonPath("$.roles[*].name", hasItems("User", "Administrator")))
			.andExpect(jsonPath("$.permissions", hasItems("access.roles.manage", "audit.entries.view")));
		assertThat(count("select count(*) from audit_entry where action = 'identity.bootstrap-admin.granted' "
				+ "and target_id = unhex(?)", hex(ADMIN))).isEqualTo(1);

		// With an administrator in place, the address grants nothing more.
		get(token(SECOND_ADMIN, ADMIN_EMAIL, true), "/api/v1/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.roles[*].name", contains("User")));
	}

	@Test
	@DisplayName("a user who registered with the bootstrap address unverified becomes administrator when they verify it")
	void bootstrapAdministratorAfterVerification() throws Exception {
		get(token(ADMIN, ADMIN_EMAIL, false), "/api/v1/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.roles[*].name", contains("User")))
			.andExpect(jsonPath("$.permissions", empty()));

		get(admin(), "/api/v1/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.roles[*].name", hasItems("User", "Administrator")));
	}

	// --- disabled and deleted users ---------------------------------------------------------------------------------------

	@Test
	@DisplayName("a disabled user is refused from the next request and a re-enabled one is let back in")
	void disabledUserIsRefused() throws Exception {
		ensureAdmin();
		signIn(ALICE);

		post(admin(), "/api/v1/users/" + ALICE + "/disable", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("DISABLED"));
		get(user(ALICE), "/api/v1/me").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("identity.account_disabled"));
		get(user(ALICE), "/api/v1/widgets").andExpect(status().isForbidden());

		post(admin(), "/api/v1/users/" + ALICE + "/enable", null).andExpect(status().isOk());
		get(user(ALICE), "/api/v1/me").andExpect(status().isOk());
	}

	@Test
	@DisplayName("a deleted user is refused from the next request and a restored one is let back in")
	void deletedUserIsRefused() throws Exception {
		ensureAdmin();
		signIn(ALICE);

		delete(admin(), "/api/v1/users/" + ALICE).andExpect(status().isNoContent());
		get(user(ALICE), "/api/v1/me").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("identity.account_disabled"));
		assertThat(count("select count(*) from user_profile where id = unhex(?) and deleted_at is not null", hex(ALICE)))
			.as("the profile is soft-deleted, not removed").isEqualTo(1);

		post(admin(), "/api/v1/users/" + ALICE + "/restore", null).andExpect(status().isOk());
		get(user(ALICE), "/api/v1/me").andExpect(status().isOk());
	}

	// --- unverified users --------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("an unverified user may view their own profile and ask for the email again, and nothing else")
	void unverifiedUserIsLimited() throws Exception {
		this.keycloak.register(BOB, true, false);

		get(unverified(BOB), "/api/v1/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.emailVerified").value(false))
			.andExpect(jsonPath("$.permissions", empty()))
			.andExpect(jsonPath("$.roles[*].name", contains("User")));
		Await.until("the verification email to be requested", () -> this.keycloak.countCalls("sendVerificationEmail") == 1);

		get(unverified(BOB), "/api/v1/widgets").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("platform.email_not_verified"));
		put(unverified(BOB), "/api/v1/me/preferences", "{\"theme\":\"DARK\"}").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("platform.email_not_verified"));
		get(unverified(BOB), "/api/v1/users").andExpect(status().isForbidden());

		post(unverified(BOB), "/api/v1/me/verification-email", null).andExpect(status().isNoContent());
		assertThat(this.keycloak.countCalls("sendVerificationEmail")).isEqualTo(2);
	}

	@Test
	@DisplayName("asking for the verification email when Keycloak says the address is verified is 409, and a Keycloak outage is 503")
	void resendingVerification() throws Exception {
		signIn(ALICE);
		post(user(ALICE), "/api/v1/me/verification-email", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("identity.already_verified"));

		this.keycloak.register(BOB, true, false);
		get(unverified(BOB), "/api/v1/me").andExpect(status().isOk());
		this.keycloak.failNext("sendVerificationEmail", 1);
		post(unverified(BOB), "/api/v1/me/verification-email", null).andExpect(status().isServiceUnavailable());
	}

	@Test
	@DisplayName("a verified user's verification mail is not sent, and an unverified user gets it exactly once")
	void verificationMailIsSentOnce() throws Exception {
		this.keycloak.register(BOB, true, false);

		get(unverified(BOB), "/api/v1/me").andExpect(status().isOk());
		get(unverified(BOB), "/api/v1/me").andExpect(status().isOk());
		get(unverified(BOB), "/api/v1/me").andExpect(status().isOk());
		Await.until("the verification email to be requested", () -> this.keycloak.countCalls("sendVerificationEmail") >= 1);

		assertThat(this.keycloak.countCalls("sendVerificationEmail")).isEqualTo(1);
	}

	// --- preferences ----------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a user changes their own colour scheme and an unknown one is 400")
	void preferences() throws Exception {
		signIn(ALICE);

		put(user(ALICE), "/api/v1/me/preferences", "{\"theme\":\"DARK\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.theme").value("DARK"));
		get(user(ALICE), "/api/v1/me").andExpect(jsonPath("$.theme").value("DARK"));
		put(user(ALICE), "/api/v1/me/preferences", "{\"theme\":\"PINK\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("theme"));
		put(user(ALICE), "/api/v1/me/preferences", "{}").andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("seeing other users needs identity.profiles.view, which the user role does not hold")
	void otherUsersAreHiddenFromPlainUsers() throws Exception {
		ensureAdmin();
		signIn(ALICE);

		get(user(ALICE), "/api/v1/users").andExpect(status().isForbidden());
		get(user(ALICE), "/api/v1/users/" + ADMIN).andExpect(status().isForbidden());
		get(admin(), "/api/v1/users").andExpect(status().isOk());
		get(admin(), "/api/v1/users/" + ALICE).andExpect(status().isOk());
	}

}
