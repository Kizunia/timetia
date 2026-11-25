package com.kibootia.backend.academic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.jayway.jsonpath.JsonPath;
import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.academic.application.RoleTemplateSeeder;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.authorization.Scope;
import com.kibootia.backend.platform.fixtures.ScopeFixtures;
import com.kibootia.backend.platform.fixtures.ScopeFixtures.FakeScopeDirectory;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static com.kibootia.backend.academic.Facts.BATCH_1;
import static com.kibootia.backend.academic.Facts.BATCH_2;
import static com.kibootia.backend.academic.Facts.DEPARTMENT_A;
import static com.kibootia.backend.academic.Facts.DEPARTMENT_B;
import static com.kibootia.backend.academic.Facts.DIVISION_1;
import static com.kibootia.backend.academic.Facts.DIVISION_2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Timetia's authorization through the real stack: roles seeded in MySQL, scoped assignments through the API, the decision
 * point, the grants read from the database on every call and the academic policy. The unit test of the policy proves
 * each rule; this proves that the pieces are wired so that a person gets what their assignments give them, in the place
 * they hold it, and nothing more, from the next request on.
 */
@Import(ScopeFixtures.class)
class AcademicAuthorizationIT extends AdminApiTest {

	static final UUID HOD_A = UUID.fromString("c1000000-0000-7000-8000-000000000001");

	static final UUID CR_1 = UUID.fromString("c2000000-0000-7000-8000-000000000002");

	static final UUID BR_1 = UUID.fromString("c3000000-0000-7000-8000-000000000003");

	static final UUID FACULTY = UUID.fromString("f1000000-0000-7000-8000-000000000001");

	static final UUID SUPER_HOD = UUID.fromString("c4000000-0000-7000-8000-000000000004");

	static final UUID STUDENT = UUID.fromString("c5000000-0000-7000-8000-000000000005");

	@Autowired
	Authorization authorization;

	@Autowired
	FakeScopeDirectory directory;

	@Autowired
	RoleTemplateSeeder seeder;

	@BeforeEach
	void seedRolesAndPlaces() throws Exception {
		this.directory.clear();
		this.directory.add(AcademicScopes.DEPARTMENT, DEPARTMENT_A);
		this.directory.add(AcademicScopes.DEPARTMENT, DEPARTMENT_B);
		this.directory.add(AcademicScopes.DIVISION, DIVISION_1);
		this.directory.add(AcademicScopes.DIVISION, DIVISION_2);
		this.directory.add(AcademicScopes.BATCH, BATCH_1);
		this.directory.add(AcademicScopes.BATCH, BATCH_2);
		this.seeder.seed();
		ensureAdmin();
		for (UUID person : List.of(HOD_A, CR_1, BR_1, FACULTY, SUPER_HOD, STUDENT)) {
			signIn(person);
		}
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private String roleId(String name) throws Exception {
		String json = get(admin(), "/api/v1/roles?size=100").andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(json, "$.items[?(@.name == '" + name + "')].id");
		return ids.get(0);
	}

	private void assign(UUID person, String assignmentsJson) throws Exception {
		put(admin(), "/api/v1/users/" + person + "/scoped-roles", "{\"assignments\":[" + assignmentsJson + "]}")
			.andExpect(status().isOk());
	}

	private String in(String role, UUID place) throws Exception {
		return "{\"roleId\":\"" + roleId(role) + "\",\"scopeId\":\"" + place + "\"}";
	}

	private String everywhere(String role) throws Exception {
		return "{\"roleId\":\"" + roleId(role) + "\"}";
	}

	/** Runs the work as the signed-in user, in the default tenant, as a request would. */
	private <T> T as(UUID user, Callable<T> work) {
		Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject(user.toString()).claim("email_verified", true).build();
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new JwtAuthenticationToken(jwt));
		SecurityContextHolder.setContext(context);
		try {
			return TenantContext.callAs(TenantContext.DEFAULT_TENANT, work);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private boolean can(UUID user, String action, Object facts) {
		return as(user, () -> this.authorization.can(action, facts));
	}

	// --- the seeded roles --------------------------------------------------------------------------------------------

	@Test
	@DisplayName("the Timetia roles are created with their scope types and permissions, once, and an edit to one is kept")
	void seededRoles() throws Exception {
		// The base class emptied the roles before the test, so the seed in setUp created all six; a second seed adds none.
		assertThat(this.seeder.seed()).isZero();

		get(admin(), "/api/v1/roles?size=100").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.name == 'Super HOD')].scopeType").value(hasItem("GLOBAL")))
			.andExpect(jsonPath("$.items[?(@.name == 'Faculty')].scopeType").value(hasItem("GLOBAL")))
			.andExpect(jsonPath("$.items[?(@.name == 'HOD')].scopeType").value(hasItem("DEPARTMENT")))
			.andExpect(jsonPath("$.items[?(@.name == 'CR')].scopeType").value(hasItem("DIVISION")))
			.andExpect(jsonPath("$.items[?(@.name == 'BR')].scopeType").value(hasItem("BATCH")))
			.andExpect(jsonPath("$.items[?(@.name == 'Student')].scopeType").value(hasItem("BATCH")));
		get(admin(), "/api/v1/roles/" + roleId("HOD")).andExpect(jsonPath("$.permissions",
				hasItem(AcademicPermissions.TICKETS_APPROVE_PERMANENT)));
		get(admin(), "/api/v1/roles/" + roleId("Student")).andExpect(jsonPath("$.permissions.length()").value(1))
			.andExpect(jsonPath("$.permissions", hasItem(AcademicPermissions.LIVE_VIEW)));

		// An administrator edits the Student role; seeding again does not undo it.
		String student = roleId("Student");
		String version = read(get(admin(), "/api/v1/roles/" + student), "$.version");
		String body = "{\"name\":\"Student\",\"description\":\"Edited\",\"permissions\":[\"" + AcademicPermissions.LIVE_VIEW
				+ "\"],\"version\":" + version + "}";
		put(admin(), "/api/v1/roles/" + student, body).andExpect(status().isOk());
		this.seeder.seed();
		get(admin(), "/api/v1/roles/" + student).andExpect(jsonPath("$.description").value("Edited"));
	}

	// --- the HOD ------------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("an HOD of department A acts in department A, not in department B, and loses it on the next request")
	void hodOfADepartment() throws Exception {
		Facts inA = Facts.lecture(SessionType.THEORY).taughtBy(FACULTY).byAuthor(FACULTY).permanent();
		Facts inB = inA.inDepartmentB();
		assign(HOD_A, in("HOD", DEPARTMENT_A));

		assertThat(can(HOD_A, AcademicPermissions.TICKETS_APPROVE_PERMANENT, inA)).isTrue();
		assertThat(can(HOD_A, AcademicPermissions.TICKETS_REVERT_IN_SCOPE, inA)).isTrue();
		assertThat(can(HOD_A, AcademicPermissions.TICKETS_APPROVE_PERMANENT, inB)).as("another department").isFalse();
		assertThat(can(HOD_A, AcademicPermissions.TICKETS_REVERT_IN_SCOPE, inB)).as("another department").isFalse();
		assertThat(can(FACULTY, AcademicPermissions.TICKETS_APPROVE_PERMANENT, inA)).as("someone with no assignment").isFalse();

		assign(HOD_A, "");
		assertThat(can(HOD_A, AcademicPermissions.TICKETS_APPROVE_PERMANENT, inA)).as("after the assignment ends").isFalse();
	}

	@Test
	@DisplayName("an HOD of two departments acts in both and in no third place; a second assignment adds, it does not replace")
	void hodOfTwoDepartments() throws Exception {
		Facts inA = Facts.lecture(SessionType.THEORY).byAuthor(FACULTY);
		assign(HOD_A, in("HOD", DEPARTMENT_A) + "," + in("HOD", DEPARTMENT_B));

		assertThat(can(HOD_A, AcademicPermissions.TICKETS_REVERT_IN_SCOPE, inA)).isTrue();
		assertThat(can(HOD_A, AcademicPermissions.TICKETS_REVERT_IN_SCOPE, inA.inDepartmentB())).isTrue();
		Facts elsewhere = new Facts(Facts.TENANT, UUID.randomUUID(), UUID.randomUUID(), java.util.Set.of(UUID.randomUUID()),
				SessionType.THEORY, java.util.Set.of(), FACULTY, java.util.Set.of(), java.util.Set.of(), false, null,
				java.util.Set.of());
		assertThat(can(HOD_A, AcademicPermissions.TICKETS_REVERT_IN_SCOPE, elsewhere)).isFalse();
	}

	// --- representatives and students ---------------------------------------------------------------------------------

	@Test
	@DisplayName("a CR reviews a theory lecture of their division only, and a BR a lab lecture of their batch only")
	void representatives() throws Exception {
		Facts theory = Facts.lecture(SessionType.THEORY).byAuthor(FACULTY).reviewedBy(CR_1, BR_1);
		Facts lab = Facts.lecture(SessionType.LAB).byAuthor(FACULTY).reviewedBy(CR_1, BR_1);
		assign(CR_1, in("CR", DIVISION_1));
		assign(BR_1, in("BR", BATCH_1));

		assertThat(can(CR_1, AcademicPermissions.TICKETS_REVIEW, theory)).isTrue();
		assertThat(can(CR_1, AcademicPermissions.TICKETS_REVIEW, lab)).as("a CR on a lab lecture").isFalse();
		assertThat(can(CR_1, AcademicPermissions.TICKETS_REVIEW, theory.inDepartmentB())).as("another division").isFalse();
		assertThat(can(BR_1, AcademicPermissions.TICKETS_REVIEW, lab)).isTrue();
		assertThat(can(BR_1, AcademicPermissions.TICKETS_REVIEW, theory)).as("a BR on a theory lecture").isFalse();
		assertThat(can(BR_1, AcademicPermissions.TICKETS_REVIEW, lab.inDepartmentB())).as("another batch").isFalse();
		assertThat(can(CR_1, AcademicPermissions.TICKETS_APPROVE_PERMANENT, theory.permanent()))
			.as("a CR cannot approve a permanent change").isFalse();
	}

	@Test
	@DisplayName("a student sees the lectures of their batch and not those of another, and cannot raise or see tickets")
	void student() throws Exception {
		Facts mine = Facts.lecture(SessionType.LAB).byAuthor(FACULTY);
		assign(STUDENT, in("Student", BATCH_1));

		assertThat(can(STUDENT, AcademicPermissions.LIVE_VIEW, mine)).isTrue();
		assertThat(can(STUDENT, AcademicPermissions.LIVE_VIEW, mine.inDepartmentB())).isFalse();
		assertThat(can(STUDENT, AcademicPermissions.TICKETS_VIEW_IN_SCOPE, mine)).isFalse();
		assertThat(can(STUDENT, AcademicPermissions.TICKETS_VIEW, mine.byAuthor(STUDENT))).isFalse();
		assertThat(can(STUDENT, AcademicPermissions.ISSUES_OPEN, mine)).as("a student cannot open an issue").isFalse();
	}

	@Test
	@DisplayName("a faculty member acts through the relation to the lecture, and a global grant does not widen it")
	void faculty() throws Exception {
		Facts mine = Facts.lecture(SessionType.THEORY).taughtBy(FACULTY).byAuthor(FACULTY);
		assign(FACULTY, everywhere("Faculty"));

		assertThat(can(FACULTY, AcademicPermissions.TICKETS_CREATE, mine)).isTrue();
		assertThat(can(FACULTY, AcademicPermissions.TICKETS_SELF_APPROVE, mine)).isTrue();
		assertThat(can(FACULTY, AcademicPermissions.TICKETS_CREATE, mine.taughtBy(HOD_A))).as("a lecture someone else teaches")
			.isFalse();
		assertThat(can(FACULTY, AcademicPermissions.TICKETS_APPROVE_PERMANENT, mine)).as("not an HOD right").isFalse();
	}

	// --- where the permission is held ---------------------------------------------------------------------------------

	@Test
	@DisplayName("a policy-decided permission is never satisfied by a permission-only check, even for a Super HOD")
	void flatChecksNeverSatisfyIt() throws Exception {
		assign(SUPER_HOD, everywhere("Super HOD"));

		assertThat(as(SUPER_HOD, () -> this.authorization.can(AcademicPermissions.TICKETS_APPROVE_PERMANENT))).isFalse();
		assertThat(as(SUPER_HOD, () -> this.authorization.scopeFor(AcademicPermissions.TICKETS_APPROVE_PERMANENT)))
			.isEqualTo(Scope.NONE);
		assertThat(can(SUPER_HOD, AcademicPermissions.TICKETS_APPROVE_PERMANENT, Facts.lecture(SessionType.THEORY).permanent()
			.inDepartmentB())).isTrue();
	}

	@Test
	@DisplayName("the administrator holds the flat Super Admin permissions and none of the ticket permissions")
	void administratorIsNotAnApprover() throws Exception {
		Facts lecture = Facts.lecture(SessionType.THEORY).byAuthor(FACULTY);

		assertThat(as(ADMIN, () -> this.authorization.can(AcademicPermissions.CONFIG_MANAGE))).isTrue();
		assertThat(as(ADMIN, () -> this.authorization.can(AcademicPermissions.AUDIT_VIEW_ALL))).isTrue();
		assertThat(can(ADMIN, AcademicPermissions.TICKETS_APPROVE_PERMANENT, lecture.permanent())).isFalse();
		assertThat(can(ADMIN, AcademicPermissions.TICKETS_REVERT_IN_SCOPE, lecture)).isFalse();
		assertThat(can(ADMIN, AcademicPermissions.TICKETS_CREATE_ON_BEHALF, lecture)).isFalse();

		get(admin(), "/api/v1/me").andExpect(jsonPath("$.permissions", hasItem(AcademicPermissions.CONFIG_MANAGE)))
			.andExpect(jsonPath("$.permissions", not(hasItem(AcademicPermissions.TICKETS_APPROVE_PERMANENT))));
		get(admin(), "/api/v1/roles/" + builtInRoleId("ADMIN"))
			.andExpect(jsonPath("$.permissions", not(hasItem(AcademicPermissions.TICKETS_APPROVE_PERMANENT))))
			.andExpect(jsonPath("$.permissions", hasItem(AcademicPermissions.CONFIG_MANAGE)));
	}

	@Test
	@DisplayName("the permissions /me lists include those held in a scope, as a hint for the interface and not a decision")
	void meListsScopedPermissionsAsAHint() throws Exception {
		get(user(HOD_A), "/api/v1/me").andExpect(jsonPath("$.permissions", not(hasItem(AcademicPermissions.TICKETS_APPROVE_PERMANENT))));
		assign(HOD_A, in("HOD", DEPARTMENT_A));

		get(user(HOD_A), "/api/v1/me")
			.andExpect(jsonPath("$.permissions", hasItem(AcademicPermissions.TICKETS_APPROVE_PERMANENT)));
		// The list says nothing about where: a decision about a record in another department is still no.
		assertThat(can(HOD_A, AcademicPermissions.TICKETS_APPROVE_PERMANENT,
				Facts.lecture(SessionType.THEORY).permanent().inDepartmentB())).isFalse();
	}

	@Test
	@DisplayName("a person who has not verified their email address is refused everything, whatever they hold")
	void unverifiedCaller() throws Exception {
		assign(HOD_A, in("HOD", DEPARTMENT_A));
		Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject(HOD_A.toString()).claim("email_verified", false).build();
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new JwtAuthenticationToken(jwt));
		SecurityContextHolder.setContext(context);
		try {
			assertThat(TenantContext.callAs(TenantContext.DEFAULT_TENANT, () -> this.authorization.can(
					AcademicPermissions.TICKETS_APPROVE_PERMANENT, Facts.lecture(SessionType.THEORY).permanent()))).isFalse();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	@Test
	@DisplayName("a lecture of another tenant is refused even to someone who holds the permission everywhere")
	void anotherTenant() throws Exception {
		assign(SUPER_HOD, everywhere("Super HOD"));
		Facts foreign = new Facts(UUID.fromString("00000000-0000-7000-8000-0000000000ff"), DEPARTMENT_A, DIVISION_1,
				java.util.Set.of(BATCH_1), SessionType.THEORY, java.util.Set.of(), FACULTY, java.util.Set.of(),
				java.util.Set.of(), true, null, java.util.Set.of());

		assertThat(can(SUPER_HOD, AcademicPermissions.TICKETS_APPROVE_PERMANENT, foreign)).isFalse();
	}

	// --- lists -------------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("the reach of a permission for a list is the places where it is held, everywhere for a global grant, nothing otherwise")
	void reach() throws Exception {
		assign(HOD_A, in("HOD", DEPARTMENT_A) + "," + in("HOD", DEPARTMENT_B));
		assign(CR_1, in("CR", DIVISION_1));
		assign(SUPER_HOD, everywhere("Super HOD"));

		Reach hod = as(HOD_A, () -> this.authorization.reach(AcademicPermissions.TICKETS_VIEW_IN_SCOPE));
		assertThat(hod.everywhere()).isFalse();
		assertThat(hod.idsOf(AcademicScopes.DEPARTMENT)).containsExactlyInAnyOrder(DEPARTMENT_A, DEPARTMENT_B);
		assertThat(hod.idsOf(AcademicScopes.DIVISION)).isEmpty();

		Reach cr = as(CR_1, () -> this.authorization.reach(AcademicPermissions.TICKETS_VIEW_IN_SCOPE));
		assertThat(cr.idsOf(AcademicScopes.DIVISION)).containsExactly(DIVISION_1);
		assertThat(cr.idsOf(AcademicScopes.DEPARTMENT)).isEmpty();

		assertThat(as(SUPER_HOD, () -> this.authorization.reach(AcademicPermissions.TICKETS_VIEW_IN_SCOPE)).everywhere())
			.isTrue();
		assertThat(as(STUDENT, () -> this.authorization.reach(AcademicPermissions.TICKETS_VIEW_IN_SCOPE)).isEmpty())
			.as("a student holds it nowhere").isTrue();
		assertThat(as(CR_1, () -> this.authorization.reach(AcademicPermissions.TICKETS_APPROVE_PERMANENT)).isEmpty())
			.as("a CR cannot approve a permanent change anywhere").isTrue();
	}

}
