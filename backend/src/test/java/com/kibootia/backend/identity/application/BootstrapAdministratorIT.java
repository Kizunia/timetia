package com.kibootia.backend.identity.application;

import java.nio.ByteBuffer;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.Await;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * How a fresh environment gets its first Super Admin without anyone touching the database (Timetia ADR 0006, W3.3):
 * the application invites the configured address at startup when nobody is an administrator, and the person becomes
 * administrator when they first sign in, once. The runner has already run once when this context started, against an
 * empty database; each test cleans up and runs it again, as a restart would.
 */
@SpringBootTest(properties = { "kibootia.identity.bootstrap-admin-email=" + BootstrapAdministratorIT.FOUNDER,
		"kibootia.identity.bootstrap-invite.enabled=true", "kibootia.events.initial-backoff=PT0S",
		"kibootia.events.max-backoff=PT0S", "kibootia.events.max-attempts=4", "kibootia.events.stale-after=PT1H" })
class BootstrapAdministratorIT extends AdminApiTest {

	static final String FOUNDER = "founder@college.test";

	@Autowired
	BootstrapAdministratorInvitation bootstrap;

	private UUID founderId() {
		ByteBuffer id = ByteBuffer.wrap(this.jdbc.queryForObject("select id from user_profile where email = ?", byte[].class, FOUNDER));
		return new UUID(id.getLong(), id.getLong());
	}

	private int audits(String action) {
		return count("select count(*) from audit_entry where action = ?", action);
	}

	private int administrators() {
		return count("select count(*) from role_assignment ra join role r on r.id = ra.role_id where r.built_in = 'ADMIN'");
	}

	@Test
	@DisplayName("with no administrator the address is invited once, nobody is made administrator yet, and a restart before they sign in sends the link again")
	void invitesTheFounder() {
		this.bootstrap.invite();

		assertThat(this.keycloak.countCalls("createUser")).isEqualTo(1);
		assertThat(count("select count(*) from user_profile where email = ? and status = 'INVITED'", FOUNDER)).isEqualTo(1);
		assertThat(audits("identity.bootstrap-admin.invited")).isEqualTo(1);
		assertThat(audits("identity.user.invited")).isZero();
		assertThat(administrators()).isZero();
		Await.until("the invitation to be sent", () -> this.keycloak.countCalls("sendInvitation") == 1);
		assertThat(count("select count(*) from audit_entry where changes like '%@%' or changes like '%college%'")).isZero();

		// Restarting before they have signed in sends the link again, and creates nothing.
		this.bootstrap.invite();
		assertThat(this.keycloak.countCalls("createUser")).isEqualTo(1);
		assertThat(count("select count(*) from user_profile")).isEqualTo(1);
		assertThat(audits("identity.bootstrap-admin.invitation-resent")).isEqualTo(1);
		Await.until("the invitation to be sent again", () -> this.keycloak.countCalls("sendInvitation") == 2);
	}

	@Test
	@DisplayName("the invited person becomes administrator at their first sign-in with the verified address, once and audited, and the setting then does nothing")
	void becomesAdministrator() throws Exception {
		this.bootstrap.invite();
		UUID founder = founderId();

		get(token(founder, FOUNDER, true), "/api/v1/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.administrator").value(true));
		assertThat(administrators()).isEqualTo(1);
		assertThat(audits("identity.bootstrap-admin.granted")).isEqualTo(1);

		// Later requests and restarts change nothing.
		get(token(founder, FOUNDER, true), "/api/v1/me").andExpect(status().isOk());
		this.bootstrap.invite();
		assertThat(administrators()).isEqualTo(1);
		assertThat(audits("identity.bootstrap-admin.granted")).isEqualTo(1);
		assertThat(this.keycloak.countCalls("createUser")).isEqualTo(1);
		assertThat(audits("identity.bootstrap-admin.invitation-resent")).isZero();
	}

	@Test
	@DisplayName("an unverified sign-in with the address, or anyone else, is not made administrator")
	void onlyTheVerifiedAddress() throws Exception {
		this.bootstrap.invite();
		UUID founder = founderId();

		// The token does not vouch for the address yet.
		get(token(founder, FOUNDER, false), "/api/v1/me").andExpect(status().isOk()).andExpect(jsonPath("$.administrator").value(false));
		assertThat(administrators()).isZero();

		// Someone else is never made administrator by the setting.
		signIn(ALICE);
		assertThat(administrators()).isZero();
		get(user(ALICE), "/api/v1/me").andExpect(jsonPath("$.administrator").value(false));
	}

	@Test
	@DisplayName("once any administrator exists the setting invites nobody, whoever it names")
	void nothingWhenAnAdministratorExists() throws Exception {
		signIn(ALICE);
		makeAdministrator(ALICE);

		this.bootstrap.invite();

		assertThat(this.keycloak.countCalls("createUser")).isZero();
		assertThat(count("select count(*) from user_profile where email = ?", FOUNDER)).isZero();
	}

	@Test
	@DisplayName("an address the identity provider already has (the local demo administrator, an account made by hand) is left alone and becomes administrator at their first sign-in")
	void accountKnownToTheIdentityProvider() throws Exception {
		this.keycloak.knowsEmail(FOUNDER);

		this.bootstrap.invite();

		assertThat(count("select count(*) from user_profile")).isZero();
		assertThat(audits("identity.bootstrap-admin.invited")).isZero();
		UUID founder = UUID.randomUUID();
		get(token(founder, FOUNDER, true), "/api/v1/me").andExpect(status().isOk()).andExpect(jsonPath("$.administrator").value(true));
		assertThat(administrators()).isEqualTo(1);
	}

	@Test
	@DisplayName("when the identity provider is down the application still starts, invites nobody, and the next start tries again")
	void identityProviderDown() {
		this.keycloak.failEverything();

		this.bootstrap.invite();
		assertThat(count("select count(*) from user_profile")).isZero();

		this.keycloak.reset();
		this.bootstrap.invite();
		assertThat(count("select count(*) from user_profile where status = 'INVITED'")).isEqualTo(1);
	}

}
