package com.kibootia.backend.audit.application;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.Await;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The audit log viewer (ADR 0046): only for those who may view it, newest first, filterable, with names looked up when an
 * entry is shown and a user who was erased shown as erased.
 */
class AuditViewerIT extends AdminApiTest {

	@Test
	@DisplayName("only someone with the audit permission may read the log")
	void viewingIsProtected() throws Exception {
		ensureAdmin();
		signIn(ALICE);

		get(user(ALICE), "/api/v1/audit-entries").andExpect(status().isForbidden());
		get(admin(), "/api/v1/audit-entries").andExpect(status().isOk());

		String auditors = createRole("Auditors", "audit.entries.view");
		setRoles(ALICE, builtInRoleId("USER"), auditors);
		get(user(ALICE), "/api/v1/audit-entries").andExpect(status().isOk());
	}

	@Test
	@DisplayName("entries are newest first and show the current names of the actor and of the user acted on")
	void showsNames() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		String reviewers = createRole("Reviewers");
		setRoles(ALICE, builtInRoleId("USER"), reviewers);

		get(admin(), "/api/v1/audit-entries?size=100").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].action").value("access.role.assigned"))
			.andExpect(jsonPath("$.items[0].actor.kind").value("USER"))
			.andExpect(jsonPath("$.items[0].actor.name").value("Given a000"))
			.andExpect(jsonPath("$.items[0].target.kind").value("USER"))
			.andExpect(jsonPath("$.items[0].target.name").value("Given 1111"))
			.andExpect(jsonPath("$.items[0].targetId").value(ALICE.toString()))
			.andExpect(jsonPath("$.items[0].changes.role").value("Reviewers"))
			.andExpect(jsonPath("$.items[0].outcome").value("success"))
			.andExpect(jsonPath("$.items[*].action", hasItems("access.role.created", "identity.bootstrap-admin.granted")));
	}

	@Test
	@DisplayName("an erased user shows as erased, work no user started shows as the system, and no email address is in the log")
	void erasedUsersAndTheSystem() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		delete(admin(), "/api/v1/users/" + ALICE).andExpect(status().isNoContent());
		get(admin(), "/api/v1/audit-entries?targetId=" + ALICE).andExpect(jsonPath("$.items[0].target.kind").value("USER"));

		post(admin(), "/api/v1/users/" + ALICE + "/erase", null).andExpect(status().isAccepted());
		Await.until("the erasure to be audited", () -> count("select count(*) from audit_entry "
				+ "where action = 'identity.user.erased'") == 1);

		get(admin(), "/api/v1/audit-entries?targetId=" + ALICE + "&size=100").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].target.kind", everyItem(is("ERASED"))))
			.andExpect(jsonPath("$.items[*].target.name", everyItem(nullValue())))
			.andExpect(jsonPath("$.items[*].target.id", everyItem(is(ALICE.toString()))))
			.andExpect(jsonPath("$.items[?(@.action == 'identity.user.erased')].actor.kind", contains("SYSTEM")))
			.andExpect(jsonPath("$.items[?(@.action == 'identity.user.deleted')].actor.kind", contains("USER")))
			.andExpect(jsonPath("$.items[?(@.action == 'identity.user.deleted')].actor.name", contains("Given a000")));
		org.assertj.core.api.Assertions.assertThat(count("select count(*) from audit_entry where changes like '%@%'")).isZero();
	}

	@Test
	@DisplayName("entries can be filtered by actor, action prefix, target and time, and paged")
	void filtering() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		signIn(BOB);
		String reviewers = createRole("Reviewers");
		setRoles(ALICE, builtInRoleId("USER"), reviewers);
		setRoles(BOB, builtInRoleId("USER"), reviewers);

		get(admin(), "/api/v1/audit-entries?action=access.role.assigned&size=100").andExpect(jsonPath("$.page.totalElements")
			.value(2)).andExpect(jsonPath("$.items[*].action", everyItem(is("access.role.assigned"))));
		get(admin(), "/api/v1/audit-entries?action=access.&size=100")
			.andExpect(jsonPath("$.items[*].action", everyItem(startsWith("access."))));
		get(admin(), "/api/v1/audit-entries?targetId=" + BOB).andExpect(jsonPath("$.page.totalElements").value(1));
		get(admin(), "/api/v1/audit-entries?targetType=role").andExpect(jsonPath("$.page.totalElements").value(1));
		get(admin(), "/api/v1/audit-entries?actorId=" + ADMIN + "&size=100")
			.andExpect(jsonPath("$.items[*].actor.id", everyItem(is(ADMIN.toString()))));
		get(admin(), "/api/v1/audit-entries?actorId=" + UUID.randomUUID()).andExpect(jsonPath("$.page.totalElements").value(0));
		get(admin(), "/api/v1/audit-entries?from=2000-01-01T00:00:00Z").andExpect(status().isOk());
		get(admin(), "/api/v1/audit-entries?to=2000-01-01T00:00:00Z").andExpect(jsonPath("$.page.totalElements").value(0));
		get(admin(), "/api/v1/audit-entries?size=2&page=0&sort=occurredAt,asc")
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].action").value("identity.bootstrap-admin.granted"));
		get(admin(), "/api/v1/audit-entries?sort=changes,asc").andExpect(status().isBadRequest());
		get(admin(), "/api/v1/audit-entries?action=%25").andExpect(jsonPath("$.page.totalElements").value(0));
		get(admin(), "/api/v1/audit-entries?size=100").andExpect(jsonPath("$.items[*].action", hasItem("access.role.created")));
	}

}
