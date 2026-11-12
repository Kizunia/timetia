package com.kibootia.backend.access;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.jayway.jsonpath.JsonPath;
import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.academic.AcademicScopes;
import com.kibootia.backend.academic.Facts;
import com.kibootia.backend.academic.application.RoleTemplateSeeder;
import com.kibootia.backend.platform.fixtures.ScopeFixtures;
import com.kibootia.backend.platform.fixtures.ScopeFixtures.FakeScopeDirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Giving roles within a scope, and the rules that keep scoped roles apart from platform roles (Timetia ADR 0004): who may
 * give them, what a request must say, what is refused and what is audited.
 */
@Import(ScopeFixtures.class)
class ScopedAssignmentsApiIT extends AdminApiTest {

	@Autowired
	FakeScopeDirectory directory;

	@Autowired
	RoleTemplateSeeder seeder;

	@BeforeEach
	void seedRolesAndPlaces() throws Exception {
		this.directory.clear();
		this.directory.add(AcademicScopes.DEPARTMENT, Facts.DEPARTMENT_A);
		this.directory.add(AcademicScopes.DIVISION, Facts.DIVISION_1);
		this.seeder.seed();
		ensureAdmin();
		signIn(ALICE);
		signIn(BOB);
	}

	private String roleId(String name) throws Exception {
		String json = get(admin(), "/api/v1/roles?size=100").andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(json, "$.items[?(@.name == '" + name + "')].id");
		return ids.get(0);
	}

	private String scopedRoles(UUID person) {
		return "/api/v1/users/" + person + "/scoped-roles";
	}

	private String body(String... assignments) {
		return "{\"assignments\":[" + String.join(",", assignments) + "]}";
	}

	private String assignment(String roleId, UUID place) {
		return "{\"roleId\":\"" + roleId + "\"" + ((place == null) ? "" : ",\"scopeId\":\"" + place + "\"") + "}";
	}

	private String roleBody(String name, String scopeType, String... permissions) {
		return "{\"name\":\"" + name + "\",\"description\":\"Test\"," + ((scopeType == null) ? "" : "\"scopeType\":\"" + scopeType + "\",")
				+ "\"permissions\":[" + (permissions.length == 0 ? "" : "\"" + String.join("\",\"", permissions) + "\"") + "]}";
	}

	// --- giving a role within a scope ---------------------------------------------------------------------------------

	@Test
	@DisplayName("an administrator gives a role within a scope, reads it back and ends it; each change is audited once with ids only")
	void assignAndEnd() throws Exception {
		String hod = roleId("HOD");

		put(admin(), scopedRoles(ALICE), body(assignment(hod, Facts.DEPARTMENT_A))).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].roleName").value("HOD"))
			.andExpect(jsonPath("$[0].scopeType").value("DEPARTMENT"))
			.andExpect(jsonPath("$[0].scopeId").value(Facts.DEPARTMENT_A.toString()));
		get(admin(), scopedRoles(ALICE)).andExpect(status().isOk()).andExpect(jsonPath("$[0].roleName").value("HOD"));
		assertThat(count("select count(*) from audit_entry where action = 'access.role.assigned' and target_id = unhex(?) "
				+ "and changes like '%DEPARTMENT%' and changes like ?", hex(ALICE), "%" + Facts.DEPARTMENT_A + "%")).isEqualTo(1);

		// Asking for the same again changes nothing and audits nothing.
		put(admin(), scopedRoles(ALICE), body(assignment(hod, Facts.DEPARTMENT_A))).andExpect(status().isOk());
		assertThat(count("select count(*) from audit_entry where action = 'access.role.assigned'")).isEqualTo(1);

		put(admin(), scopedRoles(ALICE), body()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		assertThat(count("select count(*) from audit_entry where action = 'access.role.unassigned' and target_id = unhex(?)",
				hex(ALICE))).isEqualTo(1);
		assertThat(count("select count(*) from audit_entry where changes like '%@%'")).as("no personal data").isZero();
	}

	@Test
	@DisplayName("a role that applies everywhere is given without a scope")
	void globalRole() throws Exception {
		put(admin(), scopedRoles(ALICE), body(assignment(roleId("Faculty"), null))).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].roleName").value("Faculty"))
			.andExpect(jsonPath("$[0].scopeType").value("GLOBAL"))
			.andExpect(jsonPath("$[0].scopeId").doesNotExist());
	}

	@Test
	@DisplayName("what a request must say: a scoped role, the right scope for it, a place that exists, a role that exists")
	void requestRules() throws Exception {
		String hod = roleId("HOD");
		String faculty = roleId("Faculty");

		// A platform role is not given here.
		put(admin(), scopedRoles(ALICE), body(assignment(builtInRoleId("USER"), null))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("role_not_scoped"));
		// A role for a department needs one; a role for everywhere takes none.
		put(admin(), scopedRoles(ALICE), body(assignment(hod, null))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("scope_required"));
		put(admin(), scopedRoles(ALICE), body(assignment(faculty, Facts.DEPARTMENT_A))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("scope_not_allowed"));
		// A place nobody knows, and a place of the wrong kind.
		put(admin(), scopedRoles(ALICE), body(assignment(hod, UUID.randomUUID()))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("unknown_scope"));
		put(admin(), scopedRoles(ALICE), body(assignment(hod, Facts.DIVISION_1))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("unknown_scope"));
		put(admin(), scopedRoles(ALICE), body(assignment(UUID.randomUUID().toString(), null))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("unknown_role"));
		put(admin(), scopedRoles(UUID.randomUUID()), body()).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("access.user_not_found"));

		// Nothing was given by any of them.
		get(admin(), scopedRoles(ALICE)).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@DisplayName("only an administrator gives a role within a scope, even with the permission to assign roles; viewing needs the view permission")
	void onlyAdministrators() throws Exception {
		String assigners = createRole("Assigners", "access.roles.view", "access.roles.assign");
		setRoles(ALICE, builtInRoleId("USER"), assigners);
		String hod = roleId("HOD");

		put(user(ALICE), scopedRoles(BOB), body(assignment(hod, Facts.DEPARTMENT_A))).andExpect(status().isForbidden());
		put(user(ALICE), scopedRoles(ALICE), body(assignment(hod, Facts.DEPARTMENT_A))).andExpect(status().isForbidden());
		put(user(BOB), scopedRoles(BOB), body(assignment(hod, Facts.DEPARTMENT_A))).andExpect(status().isForbidden());
		get(user(BOB), scopedRoles(BOB)).andExpect(status().isForbidden());
		get(user(ALICE), scopedRoles(BOB)).andExpect(status().isOk());
		assertThat(count("select count(*) from role_assignment where role_id = unhex(?)", hod.replace("-", ""))).isZero();
	}

	@Test
	@DisplayName("the roles of the platform are given as before and cannot take a scoped role, to a user or on an invitation")
	void platformEndpointsRefuseScopedRoles() throws Exception {
		String hod = roleId("HOD");

		put(admin(), "/api/v1/users/" + ALICE + "/roles", "{\"roleIds\":[\"" + hod + "\"]}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("scope_required"));
		post(admin(), "/api/v1/users", "{\"email\":\"new@kibootia.test\",\"roleIds\":[\"" + hod + "\"]}")
			.andExpect(status().isBadRequest());
		assertThat(this.keycloak.countCalls("delete")).as("the account made for the invitation was taken back").isEqualTo(1);
		assertThat(count("select count(*) from role_assignment where role_id = unhex(?)", hod.replace("-", ""))).isZero();
	}

	@Test
	@DisplayName("setting a user's platform roles leaves their scoped roles alone, and scoped roles are not in the platform list")
	void thePlatformListIsSeparate() throws Exception {
		String hod = roleId("HOD");
		put(admin(), scopedRoles(ALICE), body(assignment(hod, Facts.DEPARTMENT_A))).andExpect(status().isOk());

		get(admin(), "/api/v1/users/" + ALICE + "/roles").andExpect(jsonPath("$[*].name", not(hasItem("HOD"))));
		setRoles(ALICE, builtInRoleId("USER"));
		get(admin(), scopedRoles(ALICE)).andExpect(jsonPath("$[0].roleName").value("HOD"));
		setRoles(ALICE);
		get(admin(), scopedRoles(ALICE)).andExpect(jsonPath("$[0].roleName").value("HOD"));
	}

	// --- one assignment at a time, and places named by code ----------------------------------------------------------------

	@Test
	@DisplayName("adding or removing one assignment leaves the others alone, so a screen that is out of date cannot undo another change; a repeat adds nothing")
	void oneAtATime() throws Exception {
		String hod = roleId("HOD");
		String faculty = roleId("Faculty");
		put(admin(), scopedRoles(ALICE), body(assignment(hod, Facts.DEPARTMENT_A))).andExpect(status().isOk());

		post(admin(), scopedRoles(ALICE), "{\"roleId\":\"" + faculty + "\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));
		post(admin(), scopedRoles(ALICE), "{\"roleId\":\"" + faculty + "\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));
		assertThat(count("select count(*) from audit_entry where action = 'access.role.assigned'")).as("HOD and Faculty, once each")
			.isEqualTo(2);

		delete(admin(), scopedRoles(ALICE) + "/" + hod + "?scopeId=" + Facts.DEPARTMENT_A).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].roleName").value("Faculty"));
		assertThat(count("select count(*) from audit_entry where action = 'access.role.unassigned'")).isEqualTo(1);
		delete(admin(), scopedRoles(ALICE) + "/" + hod + "?scopeId=" + Facts.DEPARTMENT_A).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("access.assignment_not_found"));
		// A global role is removed without a place; naming one finds nothing.
		delete(admin(), scopedRoles(ALICE) + "/" + faculty + "?scopeId=" + Facts.DEPARTMENT_A).andExpect(status().isNotFound());
		delete(admin(), scopedRoles(ALICE) + "/" + faculty).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@DisplayName("a place can be named by its code: the same rules as by id, the code is shown back, and a code of another kind finds nothing")
	void byCode() throws Exception {
		this.directory.add(AcademicScopes.DEPARTMENT, Facts.DEPARTMENT_B, "ME");
		this.directory.add(AcademicScopes.DIVISION, Facts.DIVISION_2, "CSE-B");
		String hod = roleId("HOD");
		String faculty = roleId("Faculty");

		post(admin(), scopedRoles(ALICE), "{\"roleId\":\"" + hod + "\",\"scopeCode\":\"ME\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$[0].scopeId").value(Facts.DEPARTMENT_B.toString()))
			.andExpect(jsonPath("$[0].scopeCode").value("ME"));
		get(admin(), scopedRoles(ALICE)).andExpect(jsonPath("$[0].scopeCode").value("ME"));
		// A place that has no code is still shown by its id.
		put(admin(), scopedRoles(ALICE), body(assignment(hod, Facts.DEPARTMENT_A))).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].scopeId").value(Facts.DEPARTMENT_A.toString()))
			.andExpect(jsonPath("$[0].scopeCode").doesNotExist());

		post(admin(), scopedRoles(BOB), "{\"roleId\":\"" + hod + "\",\"scopeCode\":\"NOPE\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("unknown_scope"));
		post(admin(), scopedRoles(BOB), "{\"roleId\":\"" + hod + "\",\"scopeCode\":\"CSE-B\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("unknown_scope"));
		post(admin(), scopedRoles(BOB), "{\"roleId\":\"" + hod + "\",\"scopeCode\":\"ME\",\"scopeId\":\"" + Facts.DEPARTMENT_B + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("scope_ambiguous"));
		post(admin(), scopedRoles(BOB), "{\"roleId\":\"" + hod + "\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("scope_required"));
		post(admin(), scopedRoles(BOB), "{\"roleId\":\"" + faculty + "\",\"scopeCode\":\"ME\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("scope_not_allowed"));
		post(admin(), scopedRoles(BOB), "{\"roleId\":\"" + builtInRoleId("USER") + "\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("role_not_scoped"));
		post(admin(), scopedRoles(UUID.randomUUID()), "{\"roleId\":\"" + faculty + "\"}").andExpect(status().isNotFound());
		get(admin(), scopedRoles(BOB)).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@DisplayName("only an administrator adds or removes one, even with the permission to assign roles")
	void oneAtATimeOnlyAdministrators() throws Exception {
		String assigners = createRole("Assigners", "access.roles.view", "access.roles.assign");
		setRoles(ALICE, builtInRoleId("USER"), assigners);
		String faculty = roleId("Faculty");
		put(admin(), scopedRoles(BOB), body(assignment(faculty, null))).andExpect(status().isOk());

		post(user(ALICE), scopedRoles(ALICE), "{\"roleId\":\"" + faculty + "\"}").andExpect(status().isForbidden());
		post(user(ALICE), scopedRoles(BOB), "{\"roleId\":\"" + faculty + "\"}").andExpect(status().isForbidden());
		delete(user(ALICE), scopedRoles(BOB) + "/" + faculty).andExpect(status().isForbidden());
		delete(user(BOB), scopedRoles(BOB) + "/" + faculty).andExpect(status().isForbidden());
		get(admin(), scopedRoles(BOB)).andExpect(jsonPath("$.length()").value(1));
		get(admin(), scopedRoles(ALICE)).andExpect(jsonPath("$.length()").value(0));
	}

	// --- the rules for roles ------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a role that holds a policy-decided permission needs a scope type, and a role with one holds only those")
	void scopeTypeRules() throws Exception {
		post(admin(), "/api/v1/roles", roleBody("No scope", null, AcademicPermissions.TICKETS_REVIEW))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("scope_type_required"));
		post(admin(), "/api/v1/roles", roleBody("Flat in a scope", "DEPARTMENT", "access.roles.view"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("not_scopeable"));
		post(admin(), "/api/v1/roles", roleBody("Unknown kind", "PLANET", AcademicPermissions.TICKETS_REVIEW))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("unknown_scope_type"));

		post(admin(), "/api/v1/roles", roleBody("Invigilator", "DEPARTMENT", AcademicPermissions.TICKETS_VIEW_IN_SCOPE))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.scopeType").value("DEPARTMENT"));
		// A platform role is created as before.
		post(admin(), "/api/v1/roles", roleBody("Plain", null, "access.roles.view")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.scopeType").doesNotExist());
	}

	@Test
	@DisplayName("editing a role keeps the rules: no flat permission in a scoped role, no scoped permission in a platform role")
	void editingKeepsTheRules() throws Exception {
		String scoped = read(post(admin(), "/api/v1/roles", roleBody("Invigilator", "DEPARTMENT",
				AcademicPermissions.TICKETS_VIEW_IN_SCOPE)).andExpect(status().isCreated()), "$.id");
		String plain = createRole("Plain", "access.roles.view");
		String edit = "\"description\":\"Test\",\"version\":0";

		put(admin(), "/api/v1/roles/" + scoped, "{\"name\":\"Invigilator\"," + edit + ",\"permissions\":[\""
				+ AcademicPermissions.TICKETS_VIEW_IN_SCOPE + "\",\"access.roles.view\"]}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("not_scopeable"));
		put(admin(), "/api/v1/roles/" + plain, "{\"name\":\"Plain\"," + edit + ",\"permissions\":[\"access.roles.view\",\""
				+ AcademicPermissions.TICKETS_REVIEW + "\"]}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("scope_type_required"));
		put(admin(), "/api/v1/roles/" + scoped, "{\"name\":\"Invigilator\"," + edit + ",\"permissions\":[\""
				+ AcademicPermissions.TICKETS_VIEW_IN_SCOPE + "\",\"" + AcademicPermissions.ISSUES_OPEN + "\"]}")
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("only an administrator puts a policy-decided permission in a role, even with the permission to manage roles")
	void onlyAdministratorsPutScopedPermissionsInRoles() throws Exception {
		String managers = createRole("Managers", "access.roles.view", "access.roles.manage");
		setRoles(ALICE, builtInRoleId("USER"), managers);

		post(user(ALICE), "/api/v1/roles", roleBody("Sneaky", "DEPARTMENT", AcademicPermissions.TICKETS_VIEW_IN_SCOPE))
			.andExpect(status().isForbidden());
		// She can still edit platform roles, within what she holds.
		post(user(ALICE), "/api/v1/roles", roleBody("Fine", null, "access.roles.view")).andExpect(status().isCreated());

		String scoped = read(post(admin(), "/api/v1/roles", roleBody("Invigilator", "DEPARTMENT",
				AcademicPermissions.TICKETS_VIEW_IN_SCOPE)).andExpect(status().isCreated()), "$.id");
		put(user(ALICE), "/api/v1/roles/" + scoped, "{\"name\":\"Invigilator\",\"description\":\"Test\",\"version\":0,\"permissions\":[\""
				+ AcademicPermissions.TICKETS_VIEW_IN_SCOPE + "\",\"" + AcademicPermissions.ISSUES_OPEN + "\"]}")
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("a role held in a scope cannot be deleted, and the permission list says which permissions are decided per record")
	void inUseAndListing() throws Exception {
		String hod = roleId("HOD");
		put(admin(), scopedRoles(ALICE), body(assignment(hod, Facts.DEPARTMENT_A))).andExpect(status().isOk());

		delete(admin(), "/api/v1/roles/" + hod).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("access.role_in_use"));

		get(admin(), "/api/v1/permissions")
			.andExpect(jsonPath("$[?(@.name == '" + AcademicPermissions.TICKETS_REVIEW + "')].policyDecided").value(hasItem(true)))
			.andExpect(jsonPath("$[?(@.name == 'access.roles.view')].policyDecided").value(hasItem(false)))
			.andExpect(jsonPath("$[?(@.name == '" + AcademicPermissions.CONFIG_MANAGE + "')].policyDecided").value(hasItem(false)));
	}

}
