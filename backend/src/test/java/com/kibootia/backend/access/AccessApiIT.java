package com.kibootia.backend.access;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.platform.jobs.RetentionPurgeJob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Roles, permissions and assignments through the real stack (ADR 0025, ADR 0044): every operation has an allowed and a
 * denied case, role editing cannot be used to escalate, the administrator safeguards hold, and a change applies on the
 * next request.
 */
class AccessApiIT extends AdminApiTest {

	private static final String FORBIDDEN = "platform.forbidden";

	@Autowired
	RetentionPurgeJob retention;


	// --- who may do what ---------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a plain user is denied every roles endpoint and an administrator is allowed")
	void everyEndpointHasAnAllowedAndADeniedCase() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		String roleId = createRole("Reviewers");

		get(user(ALICE), "/api/v1/roles").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(FORBIDDEN));
		get(user(ALICE), "/api/v1/roles/" + roleId).andExpect(status().isForbidden());
		get(user(ALICE), "/api/v1/permissions").andExpect(status().isForbidden());
		post(user(ALICE), "/api/v1/roles", "{\"name\":\"X\",\"description\":\"\",\"permissions\":[]}")
			.andExpect(status().isForbidden());
		put(user(ALICE), "/api/v1/roles/" + roleId,
				"{\"name\":\"X\",\"description\":\"\",\"permissions\":[],\"version\":0}")
			.andExpect(status().isForbidden());
		delete(user(ALICE), "/api/v1/roles/" + roleId).andExpect(status().isForbidden());
		get(user(ALICE), "/api/v1/users/" + BOB + "/roles").andExpect(status().isForbidden());
		put(user(ALICE), "/api/v1/users/" + BOB + "/roles", "{\"roleIds\":[]}").andExpect(status().isForbidden());

		get(admin(), "/api/v1/roles").andExpect(status().isOk());
		get(admin(), "/api/v1/roles/" + roleId).andExpect(status().isOk());
		get(admin(), "/api/v1/permissions").andExpect(status().isOk());
		get(admin(), "/api/v1/users/" + ALICE + "/roles").andExpect(status().isOk());
		put(admin(), "/api/v1/users/" + ALICE + "/roles", "{\"roleIds\":[]}").andExpect(status().isOk());
		delete(admin(), "/api/v1/roles/" + roleId).andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("an administrator whose email address is not verified is refused with email_not_verified")
	void unverifiedAdministratorIsRefused() throws Exception {
		ensureAdmin();

		get(token(ADMIN, ADMIN_EMAIL, false), "/api/v1/roles").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("platform.email_not_verified"));
	}

	// --- the built-in roles ------------------------------------------------------------------------------------------

	@Test
	@DisplayName("the administrator role lists every declared permission and can be neither edited nor deleted")
	void administratorRoleIsProtected() throws Exception {
		ensureAdmin();
		String adminRole = builtInRoleId("ADMIN");

		get(admin(), "/api/v1/roles/" + adminRole).andExpect(status().isOk())
			.andExpect(jsonPath("$.builtIn").value("ADMIN"))
			.andExpect(jsonPath("$.permissions", hasItems("access.roles.manage", "identity.users.invite",
					"audit.entries.view", "notes.notes.restore")));
		put(admin(), "/api/v1/roles/" + adminRole,
				"{\"name\":\"Administrator\",\"description\":\"x\",\"permissions\":[],\"version\":0}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("access.role_protected"));
		delete(admin(), "/api/v1/roles/" + adminRole).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("access.role_protected"));
	}

	@Test
	@DisplayName("the user role holds what modules flag for it; its permissions are editable, its name and existence are not")
	void userRoleCanBeEditedButNotRenamedOrDeleted() throws Exception {
		ensureAdmin();
		String userRole = builtInRoleId("USER");

		String current = get(admin(), "/api/v1/roles/" + userRole).andExpect(status().isOk())
			.andExpect(jsonPath("$.builtIn").value("USER"))
			.andExpect(jsonPath("$.permissions", hasItems("notes.notes.create", "notes.notes.view")))
			.andExpect(jsonPath("$.permissions", not(hasItem("identity.profiles.view"))))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long version = ((Number) JsonPath.read(current, "$.version")).longValue();

		put(admin(), "/api/v1/roles/" + userRole, "{\"name\":\"Everyone\",\"description\":\"x\",\"permissions\":[],\"version\":"
				+ version + "}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("access.role_protected"));
		put(admin(), "/api/v1/roles/" + userRole,
				"{\"name\":\"User\",\"description\":\"Edited\",\"permissions\":[\"notes.notes.view\"],\"version\":" + version
						+ "}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.permissions", containsInAnyOrder("notes.notes.view")));
		delete(admin(), "/api/v1/roles/" + userRole).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("access.role_protected"));

		// The edit applies to the next request of everyone who holds the role.
		signIn(ALICE);
		post(user(ALICE), "/api/v1/notes", "{\"title\":\"t\",\"body\":\"b\"}").andExpect(status().isForbidden());
	}

	// --- managing roles ------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("creating, changing and deleting a role works, is audited once each, and checks names and permissions")
	void roleLifecycle() throws Exception {
		ensureAdmin();

		String created = post(admin(), "/api/v1/roles",
				"{\"name\":\"Editors\",\"description\":\"Edit notes\",\"permissions\":[\"notes.notes.view-any\"]}")
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/roles/")))
			.andExpect(jsonPath("$.name").value("Editors"))
			.andExpect(jsonPath("$.builtIn").doesNotExist())
			.andExpect(jsonPath("$.assignedUsers").value(0))
			.andExpect(jsonPath("$.version").value(0))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(created, "$.id");

		post(admin(), "/api/v1/roles", "{\"name\":\"Editors\",\"description\":\"\",\"permissions\":[]}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("access.role_name_in_use"));
		post(admin(), "/api/v1/roles", "{\"name\":\"a@b\",\"description\":\"\",\"permissions\":[]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("name"));
		post(admin(), "/api/v1/roles", "{\"name\":\"Other\",\"description\":\"\",\"permissions\":[\"notes.notes.typo\"]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("unknown_permission"));

		put(admin(), "/api/v1/roles/" + id,
				"{\"name\":\"Editors\",\"description\":\"Edit notes\",\"permissions\":[\"notes.notes.view-any\",\"notes.notes.update-any\"],\"version\":0}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(1))
			.andExpect(jsonPath("$.permissions", containsInAnyOrder("notes.notes.view-any", "notes.notes.update-any")));
		put(admin(), "/api/v1/roles/" + id,
				"{\"name\":\"Editors\",\"description\":\"Stale\",\"permissions\":[],\"version\":0}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("platform.concurrent_modification"));

		delete(admin(), "/api/v1/roles/" + id).andExpect(status().isNoContent());
		get(admin(), "/api/v1/roles/" + id).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("access.role_not_found"));
		delete(admin(), "/api/v1/roles/" + id).andExpect(status().isNotFound());

		assertThat(count("select count(*) from audit_entry where action = 'access.role.created'")).isEqualTo(1);
		assertThat(count("select count(*) from audit_entry where action = 'access.role.updated'")).isEqualTo(1);
		assertThat(count("select count(*) from audit_entry where action = 'access.role.deleted'")).isEqualTo(1);
	}

	@Test
	@DisplayName("a deleted role is purged with what it allowed once the retention period has passed, and not before")
	void deletedRolesArePurged() throws Exception {
		ensureAdmin();
		String recent = createRole("Recently deleted", "notes.notes.view-any");
		String old = createRole("Long deleted", "notes.notes.view-any", "notes.notes.update-any");
		delete(admin(), "/api/v1/roles/" + recent).andExpect(status().isNoContent());
		delete(admin(), "/api/v1/roles/" + old).andExpect(status().isNoContent());
		this.jdbc.update("update role set deleted_at = date_sub(utc_timestamp(6), interval 40 day) where id = unhex(?)",
				old.replace("-", ""));

		this.retention.runOnce();

		assertThat(count("select count(*) from role where id = unhex(?)", old.replace("-", ""))).isZero();
		assertThat(count("select count(*) from role_permission where role_id = unhex(?)", old.replace("-", ""))).isZero();
		assertThat(count("select count(*) from role where id = unhex(?)", recent.replace("-", ""))).isEqualTo(1);
		assertThat(count("select count(*) from role_permission where role_id = unhex(?)", recent.replace("-", "")))
			.isEqualTo(1);
		assertThat(count("select count(*) from audit_entry where action = 'access.role.deleted'")).as("the log stays")
			.isEqualTo(2);
	}

	@Test
	@DisplayName("changing only what a role allows still advances its version, so a stale editor is refused")
	void permissionOnlyChangeAdvancesTheVersion() throws Exception {
		ensureAdmin();
		String id = createRole("Viewers", "notes.notes.view-any");

		put(admin(), "/api/v1/roles/" + id,
				"{\"name\":\"Viewers\",\"description\":\"Test role\",\"permissions\":[],\"version\":0}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(1));
	}

	@Test
	@DisplayName("a role that is still held cannot be deleted")
	void roleInUseCannotBeDeleted() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		String id = createRole("Held");
		setRoles(ALICE, id);

		delete(admin(), "/api/v1/roles/" + id).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("access.role_in_use"));
		get(admin(), "/api/v1/roles/" + id).andExpect(jsonPath("$.assignedUsers").value(1));
	}

	@Test
	@DisplayName("a role manager cannot give a role a permission they do not hold themselves")
	void roleEditingCannotEscalate() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		String manager = createRole("Role managers", "access.roles.view", "access.roles.manage");
		setRoles(ALICE, builtInRoleId("USER"), manager);

		post(user(ALICE), "/api/v1/roles",
				"{\"name\":\"Inviters\",\"description\":\"\",\"permissions\":[\"identity.users.invite\"]}")
			.andExpect(status().isForbidden());
		post(user(ALICE), "/api/v1/roles",
				"{\"name\":\"Viewers\",\"description\":\"\",\"permissions\":[\"access.roles.view\"]}")
			.andExpect(status().isCreated());
		assertThat(count("select count(*) from role where name = 'Inviters'")).isZero();
	}

	// --- assigning roles -----------------------------------------------------------------------------------------------

	@Test
	@DisplayName("assigning roles needs its own permission, is audited per role and replaces the whole set")
	void assigningRoles() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		signIn(BOB);
		String userRole = builtInRoleId("USER");
		String reviewers = createRole("Reviewers");
		String editors = createRole("Editors");

		// Bob may view roles but not assign them.
		String viewer = createRole("Role viewers", "access.roles.view");
		setRoles(BOB, userRole, viewer);
		put(user(BOB), "/api/v1/users/" + ALICE + "/roles", "{\"roleIds\":[\"" + reviewers + "\"]}")
			.andExpect(status().isForbidden());
		get(user(BOB), "/api/v1/users/" + ALICE + "/roles").andExpect(status().isOk());

		put(admin(), "/api/v1/users/" + ALICE + "/roles", "{\"roleIds\":[\"" + userRole + "\",\"" + reviewers + "\"]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name", containsInAnyOrder("User", "Reviewers")));
		put(admin(), "/api/v1/users/" + ALICE + "/roles", "{\"roleIds\":[\"" + userRole + "\",\"" + editors + "\"]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name", containsInAnyOrder("User", "Editors")));

		assertThat(count("select count(*) from audit_entry where action = 'access.role.assigned' and target_id = unhex(?)",
				hex(ALICE))).isEqualTo(2);
		assertThat(count("select count(*) from audit_entry where action = 'access.role.unassigned' and target_id = unhex(?)",
				hex(ALICE))).isEqualTo(1);
		put(admin(), "/api/v1/users/" + ALICE + "/roles", "{\"roleIds\":[\"" + UUID.randomUUID() + "\"]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("unknown_role"));
		put(admin(), "/api/v1/users/" + UUID.randomUUID() + "/roles", "{\"roleIds\":[]}").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("access.user_not_found"));
	}

	@Test
	@DisplayName("only an administrator can give the administrator role")
	void onlyAnAdministratorGivesTheAdministratorRole() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		signIn(BOB);
		String adminRole = builtInRoleId("ADMIN");
		String assigner = createRole("Assigners", "access.roles.view", "access.roles.assign");
		setRoles(ALICE, builtInRoleId("USER"), assigner);

		put(user(ALICE), "/api/v1/users/" + BOB + "/roles", "{\"roleIds\":[\"" + adminRole + "\"]}")
			.andExpect(status().isForbidden());
		assertThat(count("select count(*) from role_assignment where user_id = unhex(?) and role_id = unhex(?)", hex(BOB),
				adminRole.replace("-", ""))).isZero();

		put(admin(), "/api/v1/users/" + BOB + "/roles", "{\"roleIds\":[\"" + adminRole + "\"]}")
			.andExpect(status().isOk());
		get(user(BOB), "/api/v1/me").andExpect(jsonPath("$.permissions", hasItem("access.roles.manage")));
	}

	// --- the last administrator ----------------------------------------------------------------------------------------

	@Test
	@DisplayName("the last administrator cannot lose the role, but can once another administrator exists")
	void lastAdministratorSafeguard() throws Exception {
		ensureAdmin();
		signIn(SECOND_ADMIN);
		String userRole = builtInRoleId("USER");
		String adminRole = builtInRoleId("ADMIN");

		put(admin(), "/api/v1/users/" + ADMIN + "/roles", "{\"roleIds\":[\"" + userRole + "\"]}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("access.last_administrator"));
		get(admin(), "/api/v1/me").andExpect(jsonPath("$.permissions", hasItem("access.roles.manage")));

		setRoles(SECOND_ADMIN, userRole, adminRole);
		put(admin(), "/api/v1/users/" + ADMIN + "/roles", "{\"roleIds\":[\"" + userRole + "\"]}")
			.andExpect(status().isOk());

		// Now SECOND_ADMIN is the last one.
		put(user(SECOND_ADMIN), "/api/v1/users/" + SECOND_ADMIN + "/roles", "{\"roleIds\":[\"" + userRole + "\"]}")
			.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("an administrator who cannot sign in does not count towards the last-administrator safeguard")
	void aDisabledAdministratorDoesNotCount() throws Exception {
		ensureAdmin();
		signIn(SECOND_ADMIN);
		String userRole = builtInRoleId("USER");
		setRoles(SECOND_ADMIN, userRole, builtInRoleId("ADMIN"));
		this.jdbc.update("update user_profile set status = 'DISABLED' where id = unhex(?)", hex(SECOND_ADMIN));

		put(admin(), "/api/v1/users/" + ADMIN + "/roles", "{\"roleIds\":[\"" + userRole + "\"]}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("access.last_administrator"));
	}

	@Test
	@DisplayName("two administrators removing each other's role at the same moment leave exactly one administrator")
	void concurrentRemovalLeavesOneAdministrator() throws Exception {
		ensureAdmin();
		signIn(SECOND_ADMIN);
		String userRole = builtInRoleId("USER");
		String adminRole = builtInRoleId("ADMIN");
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			for (int round = 0; round < 5; round++) {
				makeAdministrator(ADMIN);
				makeAdministrator(SECOND_ADMIN);
				CountDownLatch go = new CountDownLatch(1);
				List<Future<Integer>> results = new ArrayList<>();
				results.add(pool.submit(removeRole(go, ADMIN, SECOND_ADMIN, userRole)));
				results.add(pool.submit(removeRole(go, SECOND_ADMIN, ADMIN, userRole)));
				go.countDown();
				List<Integer> statuses = new ArrayList<>();
				for (Future<Integer> result : results) {
					statuses.add(result.get());
				}
				assertThat(statuses).as("round " + round).containsExactlyInAnyOrder(200, 409);
				assertThat(count("select count(*) from role_assignment where role_id = unhex(?)",
						adminRole.replace("-", ""))).as("administrators left in round " + round).isEqualTo(1);
			}
		}
		finally {
			pool.shutdownNow();
		}
	}

	private Callable<Integer> removeRole(CountDownLatch go, UUID caller, UUID target, String keepRole) {
		return () -> {
			go.await();
			return put(token(caller, caller == ADMIN ? ADMIN_EMAIL : "second@kibootia.test", true),
					"/api/v1/users/" + target + "/roles", "{\"roleIds\":[\"" + keepRole + "\"]}")
				.andReturn()
				.getResponse()
				.getStatus();
		};
	}

	// --- freshness -------------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a role change applies on the very next request: granting allows, taking away denies")
	void roleChangesApplyOnTheNextRequest() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		String viewers = createRole("Role viewers", "access.roles.view");

		get(user(ALICE), "/api/v1/roles").andExpect(status().isForbidden());
		setRoles(ALICE, builtInRoleId("USER"), viewers);
		get(user(ALICE), "/api/v1/roles").andExpect(status().isOk());
		get(user(ALICE), "/api/v1/me").andExpect(jsonPath("$.permissions", hasItem("access.roles.view")))
			.andExpect(jsonPath("$.roles[*].name", hasItem("Role viewers")));
		setRoles(ALICE, builtInRoleId("USER"));
		get(user(ALICE), "/api/v1/roles").andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("editing what a role allows applies to everyone who holds it on their next request")
	void editingARoleAppliesToItsHolders() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		String viewers = createRole("Role viewers", "access.roles.view");
		setRoles(ALICE, builtInRoleId("USER"), viewers);
		get(user(ALICE), "/api/v1/roles").andExpect(status().isOk());

		put(admin(), "/api/v1/roles/" + viewers,
				"{\"name\":\"Role viewers\",\"description\":\"Test role\",\"permissions\":[],\"version\":0}")
			.andExpect(status().isOk());

		get(user(ALICE), "/api/v1/roles").andExpect(status().isForbidden());
	}

}
