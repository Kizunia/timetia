package com.kibootia.backend.identity;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.Await;
import com.kibootia.backend.identity.application.UserAccountChanged;
import com.kibootia.backend.platform.events.EventContextFactory;
import com.kibootia.backend.platform.events.EventRetryJob;
import com.kibootia.backend.platform.jobs.RetentionPurgeJob;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What an administrator does with other people's accounts (ADR 0041): inviting, disabling, deleting, restoring and
 * erasing, each with an allowed and a denied case, audited once, with Keycloak brought in line by durable events that
 * survive an outage and repeat harmlessly.
 */
class UserAdministrationIT extends AdminApiTest {

	@Autowired
	RetentionPurgeJob retention;

	@Autowired
	EventRetryJob retryJob;

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	EventContextFactory contexts;

	private int audited(String action, UUID target) {
		return count("select count(*) from audit_entry where action = ? and target_id = unhex(?)", action, hex(target));
	}

	private boolean profileExists(UUID id) {
		return count("select count(*) from user_profile where id = unhex(?)", hex(id)) == 1;
	}

	private UUID invite(String email, String... roleIds) throws Exception {
		String roles = (roleIds.length == 0) ? "" : ",\"roleIds\":[\"" + String.join("\",\"", roleIds) + "\"]";
		return UUID.fromString(read(post(admin(), "/api/v1/users", "{\"email\":\"" + email + "\"" + roles + "}")
			.andExpect(status().isCreated()), "$.id"));
	}

	// --- inviting -------------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("inviting creates the account, the profile, the roles, one audit entry and, afterwards, the email")
	void inviting() throws Exception {
		ensureAdmin();
		String reviewers = createRole("Reviewers");

		String body = "{\"email\":\"New.Person@kibootia.test\",\"roleIds\":[\"" + reviewers + "\"]}";
		var result = post(admin(), "/api/v1/users", body).andExpect(status().isCreated())
			.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/users/")))
			.andExpect(jsonPath("$.state").value("INVITED"))
			.andExpect(jsonPath("$.email").value("new.person@kibootia.test"))
			.andExpect(jsonPath("$.emailVerified").value(true))
			.andExpect(jsonPath("$.roles[*].name", containsInAnyOrder("User", "Reviewers")));
		UUID id = UUID.fromString(read(result, "$.id"));

		assertThat(this.keycloak.countCalls("createUser")).isEqualTo(1);
		Await.until("the invitation to be sent", () -> this.keycloak.calls().contains("sendInvitation:" + id));
		assertThat(audited("identity.user.invited", id)).isEqualTo(1);
		assertThat(audited("access.role.assigned", id)).isEqualTo(1);
		assertThat(count("select count(*) from audit_entry where changes like '%@%'")).as("no personal data").isZero();

		// Following the link in the email, the user signs in for the first time and becomes active.
		get(user(id), "/api/v1/me").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	@DisplayName("only someone with the invite permission may invite, and naming roles needs the permission to assign them")
	void invitingIsProtected() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		String reviewers = createRole("Reviewers");
		String inviters = createRole("Inviters", "identity.users.invite");

		post(user(ALICE), "/api/v1/users", "{\"email\":\"x@kibootia.test\"}").andExpect(status().isForbidden());
		setRoles(ALICE, builtInRoleId("USER"), inviters);

		post(user(ALICE), "/api/v1/users", "{\"email\":\"x@kibootia.test\",\"roleIds\":[\"" + reviewers + "\"]}")
			.andExpect(status().isForbidden());
		assertThat(this.keycloak.countCalls("createUser")).as("refused before an account exists").isZero();
		post(user(ALICE), "/api/v1/users", "{\"email\":\"x@kibootia.test\"}").andExpect(status().isCreated());
	}

	@Test
	@DisplayName("an invitation cannot name a role whose permissions the inviter does not hold, and leaves no account behind")
	void invitingCannotGrantMoreThanTheInviterHolds() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		String powerful = createRole("Powerful", "identity.users.erase");
		String inviters = createRole("Inviters", "identity.users.invite", "access.roles.assign");
		setRoles(ALICE, builtInRoleId("USER"), inviters);

		post(user(ALICE), "/api/v1/users", "{\"email\":\"x@kibootia.test\",\"roleIds\":[\"" + powerful + "\"]}")
			.andExpect(status().isForbidden());
		assertThat(this.keycloak.countCalls("delete")).as("the account was taken back").isEqualTo(1);
		assertThat(count("select count(*) from user_profile")).as("no profile was left behind").isEqualTo(2);

		post(admin(), "/api/v1/users", "{\"email\":\"y@kibootia.test\",\"roleIds\":[\"" + powerful + "\"]}")
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("an address that is taken, malformed or blank is refused, and a Keycloak outage is 503 with nothing left behind")
	void invitingRefusals() throws Exception {
		ensureAdmin();
		invite("taken@kibootia.test");

		post(admin(), "/api/v1/users", "{\"email\":\"taken@kibootia.test\"}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("identity.email_in_use"));
		this.keycloak.createUser("elsewhere@kibootia.test", true, java.util.List.of());
		post(admin(), "/api/v1/users", "{\"email\":\"elsewhere@kibootia.test\"}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("identity.email_in_use"));
		post(admin(), "/api/v1/users", "{\"email\":\"not an address\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("email"));
		post(admin(), "/api/v1/users", "{\"email\":\"\"}").andExpect(status().isBadRequest());

		int profiles = count("select count(*) from user_profile");
		this.keycloak.failNext("createUser", 1);
		post(admin(), "/api/v1/users", "{\"email\":\"later@kibootia.test\"}").andExpect(status().isServiceUnavailable());
		assertThat(count("select count(*) from user_profile")).isEqualTo(profiles);
	}

	@Test
	@DisplayName("when Kibootia's part of an invitation fails, the Keycloak account is removed again")
	void failedInvitationLeavesNothing() throws Exception {
		ensureAdmin();
		int profiles = count("select count(*) from user_profile");

		post(admin(), "/api/v1/users", "{\"email\":\"doomed@kibootia.test\",\"roleIds\":[\"" + UUID.randomUUID() + "\"]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("unknown_role"));

		assertThat(this.keycloak.countCalls("createUser")).isEqualTo(1);
		assertThat(this.keycloak.countCalls("delete")).as("the account was taken back").isEqualTo(1);
		assertThat(count("select count(*) from user_profile")).isEqualTo(profiles);
		assertThat(count("select count(*) from audit_entry where action = 'identity.user.invited'")).isZero();
	}

	@Test
	@DisplayName("the invitation can be sent again to a user who has not accepted it, and only to such a user")
	void resendingTheInvitation() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		UUID invited = invite("pending@kibootia.test");
		Await.until("the invitation to be sent", () -> this.keycloak.countCalls("sendInvitation") == 1);

		post(user(ALICE), "/api/v1/users/" + invited + "/invitation", null).andExpect(status().isForbidden());
		post(admin(), "/api/v1/users/" + invited + "/invitation", null).andExpect(status().isAccepted());
		Await.until("the invitation to be sent again", () -> this.keycloak.countCalls("sendInvitation") == 2);
		assertThat(audited("identity.user.invitation-resent", invited)).isEqualTo(1);

		post(admin(), "/api/v1/users/" + ALICE + "/invitation", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("identity.not_invited"));
		post(admin(), "/api/v1/users/" + UUID.randomUUID() + "/invitation", null).andExpect(status().isNotFound());
	}

	// --- disabling ------------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("disabling is audited once, is repeatable, and disables the Keycloak account and ends its sessions")
	void disabling() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		this.keycloak.register(ALICE, true, true);

		post(user(ALICE), "/api/v1/users/" + BOB + "/disable", null).andExpect(status().isForbidden());
		post(user(ALICE), "/api/v1/users/" + BOB + "/enable", null).andExpect(status().isForbidden());
		post(admin(), "/api/v1/users/" + ALICE + "/disable", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("DISABLED"));
		post(admin(), "/api/v1/users/" + ALICE + "/disable", null).andExpect(status().isOk());

		Await.until("Keycloak to be brought in line", () -> this.keycloak.account(ALICE).map((a) -> !a.enabled()).orElse(false)
				&& this.keycloak.countCalls("endSessions:" + ALICE) >= 1);
		assertThat(audited("identity.user.disabled", ALICE)).as("a repeated disable changes nothing").isEqualTo(1);

		post(admin(), "/api/v1/users/" + ALICE + "/enable", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("ACTIVE"));
		Await.until("Keycloak to be enabled again", () -> this.keycloak.account(ALICE).map((a) -> a.enabled()).orElse(false));
		assertThat(audited("identity.user.enabled", ALICE)).isEqualTo(1);
		post(admin(), "/api/v1/users/" + ALICE + "/enable", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("identity.not_disabled"));
		post(admin(), "/api/v1/users/" + UUID.randomUUID() + "/disable", null).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("nobody can disable or delete their own account")
	void notYourself() throws Exception {
		ensureAdmin();
		signIn(SECOND_ADMIN);
		setRoles(SECOND_ADMIN, builtInRoleId("USER"), builtInRoleId("ADMIN"));

		post(admin(), "/api/v1/users/" + ADMIN + "/disable", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("identity.cannot_change_self"));
		delete(admin(), "/api/v1/users/" + ADMIN).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("identity.cannot_change_self"));
	}

	@Test
	@DisplayName("only an administrator can disable, enable, delete, restore or erase an administrator, whatever else they may do")
	void onlyAnAdministratorActsOnAnAdministrator() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		signIn(BOB);
		signIn(SECOND_ADMIN);
		String managers = createRole("User managers", "identity.users.disable", "identity.users.delete",
				"identity.users.restore", "identity.users.erase");
		setRoles(ALICE, builtInRoleId("USER"), managers);
		setRoles(SECOND_ADMIN, builtInRoleId("USER"), builtInRoleId("ADMIN"));
		String target = "/api/v1/users/" + SECOND_ADMIN;

		// Alice holds every account permission and still cannot touch an administrator, however she asks.
		post(user(ALICE), target + "/disable", null).andExpect(status().isForbidden());
		post(user(ALICE), target + "/enable", null).andExpect(status().isForbidden());
		delete(user(ALICE), target).andExpect(status().isForbidden());
		post(user(ALICE), target + "/restore", null).andExpect(status().isForbidden());
		post(user(ALICE), target + "/erase", null).andExpect(status().isForbidden());
		assertThat(count("select count(*) from user_profile where id = unhex(?) and status = 'ACTIVE' and deleted_at is null",
				hex(SECOND_ADMIN))).as("the administrator is untouched").isEqualTo(1);

		// The same permissions still work on an ordinary user.
		post(user(ALICE), "/api/v1/users/" + BOB + "/disable", null).andExpect(status().isOk());

		// An administrator can act on another administrator while someone else can still sign in.
		post(admin(), target + "/disable", null).andExpect(status().isOk());
		post(admin(), target + "/enable", null).andExpect(status().isOk());
	}

	// --- deleting, restoring, erasing -------------------------------------------------------------------------------------

	@Test
	@DisplayName("deleting soft-deletes the profile, disables the Keycloak account and can be undone by restoring")
	void deletingAndRestoring() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		this.keycloak.register(ALICE, true, true);

		delete(user(ALICE), "/api/v1/users/" + BOB).andExpect(status().isForbidden());
		delete(admin(), "/api/v1/users/" + ALICE).andExpect(status().isNoContent());
		Await.until("Keycloak to be brought in line", () -> this.keycloak.account(ALICE).map((a) -> !a.enabled()).orElse(false));
		assertThat(audited("identity.user.deleted", ALICE)).isEqualTo(1);

		get(admin(), "/api/v1/users/" + ALICE).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("DELETED"));
		get(admin(), "/api/v1/users?state=DELETED").andExpect(jsonPath("$.items[*].id", contains(ALICE.toString())));
		get(admin(), "/api/v1/users").andExpect(jsonPath("$.items[*].id", org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.hasItem(ALICE.toString()))));
		delete(admin(), "/api/v1/users/" + ALICE).andExpect(status().isNotFound());

		post(user(BOB), "/api/v1/users/" + ALICE + "/restore", null).andExpect(status().isForbidden());
		post(admin(), "/api/v1/users/" + ALICE + "/restore", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("ACTIVE"));
		Await.until("Keycloak to be enabled again", () -> this.keycloak.account(ALICE).map((a) -> a.enabled()).orElse(false));
		assertThat(audited("identity.user.restored", ALICE)).isEqualTo(1);
		post(admin(), "/api/v1/users/" + ALICE + "/restore", null).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("erasing a deleted user removes their Keycloak account, profile and roles and leaves only their ID in the log")
	void erasing() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		setRoles(ALICE, builtInRoleId("USER"), createRole("Reviewers"));

		post(admin(), "/api/v1/users/" + ALICE + "/erase", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("identity.not_deleted"));
		delete(admin(), "/api/v1/users/" + ALICE).andExpect(status().isNoContent());
		post(user(BOB), "/api/v1/users/" + ALICE + "/erase", null).andExpect(status().isForbidden());
		post(admin(), "/api/v1/users/" + ALICE + "/erase", null).andExpect(status().isAccepted());

		Await.until("the user to be erased", () -> !profileExists(ALICE));
		assertThat(this.keycloak.calls()).contains("delete:" + ALICE);
		assertThat(count("select count(*) from role_assignment where user_id = unhex(?)", hex(ALICE))).isZero();
		assertThat(audited("identity.user.erasure-requested", ALICE)).isEqualTo(1);
		Await.until("the erasure to be audited", () -> audited("identity.user.erased", ALICE) == 1);
		get(admin(), "/api/v1/users/" + ALICE).andExpect(status().isNotFound());
		assertThat(count("select count(*) from audit_entry where changes like '%@%'")).isZero();

		// The old token of the erased user does not bring the profile back.
		this.keycloak.goneFromKeycloak(ALICE);
		get(user(ALICE), "/api/v1/me").andExpect(status().isUnauthorized());
		assertThat(profileExists(ALICE)).isFalse();
	}

	@Test
	@DisplayName("the retention job begins erasing users deleted longer ago than the retention period, and no others")
	void retentionErasesOldDeletedUsers() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		signIn(BOB);
		delete(admin(), "/api/v1/users/" + ALICE).andExpect(status().isNoContent());
		delete(admin(), "/api/v1/users/" + BOB).andExpect(status().isNoContent());
		this.jdbc.update("update user_profile set deleted_at = date_sub(utc_timestamp(6), interval 40 day) "
				+ "where id = unhex(?)", hex(ALICE));

		this.retention.runOnce();

		Await.until("the old deleted user to be erased", () -> !profileExists(ALICE));
		assertThat(profileExists(BOB)).as("recently deleted").isTrue();
		assertThat(audited("identity.user.erasure-requested", ALICE)).isEqualTo(1);
		assertThat(this.keycloak.calls()).contains("delete:" + ALICE);

		// A user whose erasure already began is not picked again.
		this.jdbc.update("update user_profile set deleted_at = date_sub(utc_timestamp(6), interval 40 day), "
				+ "erasure_requested_at = utc_timestamp(6) where id = unhex(?)", hex(BOB));
		this.retention.runOnce();
		assertThat(audited("identity.user.erasure-requested", BOB)).isZero();
	}

	// --- Keycloak outages and repeats -----------------------------------------------------------------------------------

	@Test
	@DisplayName("Kibootia refuses a disabled user at once while Keycloak is down, and Keycloak catches up when it is back")
	void keycloakOutageOnlyDelaysTheChange() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		this.keycloak.register(ALICE, true, true);
		this.keycloak.failNext("setEnabled", 1);

		post(admin(), "/api/v1/users/" + ALICE + "/disable", null).andExpect(status().isOk());
		get(user(ALICE), "/api/v1/me").andExpect(status().isForbidden());
		Await.until("the first attempt to fail", () -> this.keycloak.countCalls("setEnabled:" + ALICE) >= 1);
		assertThat(this.keycloak.account(ALICE).orElseThrow().enabled()).as("Keycloak is not yet in line").isTrue();

		Await.until("the retry to bring Keycloak in line", () -> {
			this.retryJob.runOnce();
			return !this.keycloak.account(ALICE).orElseThrow().enabled();
		});
	}

	@Test
	@DisplayName("delivering the same account change again leaves Keycloak as it should be")
	void accountChangesAreIdempotent() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		this.keycloak.register(ALICE, true, true);
		post(admin(), "/api/v1/users/" + ALICE + "/disable", null).andExpect(status().isOk());
		Await.until("the first delivery", () -> !this.keycloak.account(ALICE).orElseThrow().enabled());

		for (int i = 0; i < 2; i++) {
			TenantContext.callAs(TenantContext.DEFAULT_TENANT, () -> this.transactions.execute((status) -> {
				this.publisher.publishEvent(new UserAccountChanged(ALICE, this.contexts.capture()));
				return null;
			}));
		}
		Await.until("the repeats to be handled", () -> count("select count(*) from event_publication "
				+ "where completion_date is null") == 0);

		assertThat(this.keycloak.account(ALICE).orElseThrow().enabled()).isFalse();
	}

	// --- listing -----------------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("the list filters by text and state, pages, sorts by an allowed field only, and shows each user's roles")
	void listing() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		signIn(BOB);
		invite("carol.pending@kibootia.test");
		post(admin(), "/api/v1/users/" + BOB + "/disable", null).andExpect(status().isOk());

		get(admin(), "/api/v1/users?size=100").andExpect(status().isOk())
			.andExpect(jsonPath("$.page.totalElements").value(4))
			.andExpect(jsonPath("$.items[?(@.id == '" + ADMIN + "')].roles[*].name", hasItems("User", "Administrator")));
		get(admin(), "/api/v1/users?state=DISABLED").andExpect(jsonPath("$.items[*].id", contains(BOB.toString())));
		get(admin(), "/api/v1/users?state=INVITED").andExpect(jsonPath("$.page.totalElements").value(1));
		get(admin(), "/api/v1/users?q=carol.pending").andExpect(jsonPath("$.page.totalElements").value(1));
		get(admin(), "/api/v1/users?q=zzz-nobody").andExpect(jsonPath("$.page.totalElements").value(0));
		get(admin(), "/api/v1/users?size=2&page=1&sort=email,desc").andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.page.totalPages").value(2));
		get(admin(), "/api/v1/users?sort=password,asc").andExpect(status().isBadRequest());
		get(user(ALICE), "/api/v1/users").andExpect(status().isForbidden());
	}

}
