package com.kibootia.backend.academic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.BeforeEach;
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
import com.kibootia.backend.platform.fixtures.ScopeFixtures;
import com.kibootia.backend.platform.fixtures.ScopeFixtures.FakeScopeDirectory;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * For a module's test that needs people who hold Timetia roles in places (W4.2): a base class that seeds the six roles
 * and a small campus, signs people in and gives them roles through the real API, and asks the real decision point what
 * they may do. A test reads as the story it tells:
 *
 * <pre>{@code
 * UUID hod = hodOf(DEPARTMENT_A);
 * UUID cr = crOf(DIVISION_1);
 * assertThat(can(hod, AcademicPermissions.TICKETS_REVERT, ticketInDepartmentA)).isTrue();
 * assertThat(can(hod, AcademicPermissions.TICKETS_REVERT, ticketInDepartmentB)).isFalse();
 * }</pre>
 *
 * <p>The campus is fixed (see {@link Facts}): department A ({@code CSE}) holds division 1 ({@code CSE-A}) and its batch 1
 * ({@code CSE-A1}); department B ({@code ME}) holds division 2 ({@code ME-A}) and its batch 2 ({@code ME-A1}). Places are
 * known to a stand-in scope directory, because the application has no provider of its own yet. The people are given
 * roles exactly as the screen does, so everything the application enforces when it gives a role is enforced here too.
 *
 * <p>Everything is read from the database on every call, like a request, so a role removed with
 * {@link #unassign} stops working at once.
 */
@Import(ScopeFixtures.class)
public abstract class AcademicApiTest extends AdminApiTest {

	public static final String HOD = "HOD";

	public static final String CR = "CR";

	public static final String BR = "BR";

	public static final String STUDENT = "Student";

	public static final String FACULTY = "Faculty";

	public static final String SUPER_HOD = "Super HOD";

	@Autowired
	protected Authorization authorization;

	@Autowired
	protected FakeScopeDirectory directory;

	@Autowired
	protected RoleTemplateSeeder seeder;

	private int people;

	/** Runs after the base class has emptied the database: the campus, the six roles and the administrator. */
	@BeforeEach
	void seedCampus() throws Exception {
		this.people = 0;
		this.directory.clear();
		this.directory.add(AcademicScopes.DEPARTMENT, Facts.DEPARTMENT_A, "CSE");
		this.directory.add(AcademicScopes.DEPARTMENT, Facts.DEPARTMENT_B, "ME");
		this.directory.add(AcademicScopes.DIVISION, Facts.DIVISION_1, "CSE-A");
		this.directory.add(AcademicScopes.DIVISION, Facts.DIVISION_2, "ME-A");
		this.directory.add(AcademicScopes.BATCH, Facts.BATCH_1, "CSE-A1");
		this.directory.add(AcademicScopes.BATCH, Facts.BATCH_2, "ME-A1");
		this.seeder.seed();
		ensureAdmin();
	}

	// --- people -----------------------------------------------------------------------------------------------------

	/** A signed-in person with no role beyond the default one. */
	protected UUID somebody() throws Exception {
		this.people++;
		return signIn(UUID.fromString(String.format("e0000000-0000-7000-8000-%012d", this.people)));
	}

	/** A person who holds the role in the department, division or batch, given by the administrator as the screen does. */
	protected UUID personWith(String role, UUID place) throws Exception {
		UUID person = somebody();
		assign(person, role, place);
		return person;
	}

	protected UUID hodOf(UUID department) throws Exception {
		return personWith(HOD, department);
	}

	protected UUID crOf(UUID division) throws Exception {
		return personWith(CR, division);
	}

	protected UUID brOf(UUID batch) throws Exception {
		return personWith(BR, batch);
	}

	protected UUID studentOf(UUID batch) throws Exception {
		return personWith(STUDENT, batch);
	}

	protected UUID faculty() throws Exception {
		return personWith(FACULTY, null);
	}

	protected UUID superHod() throws Exception {
		return personWith(SUPER_HOD, null);
	}

	/** Gives an existing person one more role (a place for a scoped role; null for a role that applies everywhere). */
	protected void assign(UUID person, String role, UUID place) throws Exception {
		post(admin(), "/api/v1/users/" + person + "/scoped-roles",
				"{\"roleId\":\"" + roleId(role) + "\"" + ((place == null) ? "" : ",\"scopeId\":\"" + place + "\"") + "}")
			.andExpect(status().isOk());
	}

	/** Takes one role away; it stops working on the person's next request. */
	protected void unassign(UUID person, String role, UUID place) throws Exception {
		delete(admin(), "/api/v1/users/" + person + "/scoped-roles/" + roleId(role) + ((place == null) ? "" : "?scopeId=" + place))
			.andExpect(status().isOk());
	}

	protected String roleId(String name) throws Exception {
		String json = get(admin(), "/api/v1/roles?size=100").andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(json, "$.items[?(@.name == '" + name + "')].id");
		return ids.get(0);
	}

	// --- what a person may do ---------------------------------------------------------------------------------------

	/** Runs the work as the person, in the default tenant, as a request with their verified token would. */
	protected <T> T asCaller(UUID person, Callable<T> work) {
		Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject(person.toString()).claim("email_verified", true).build();
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

	/** The same for work that returns nothing. A separate name, so a lambda is never ambiguous between the two. */
	protected void runAs(UUID person, Runnable work) {
		asCaller(person, () -> {
			work.run();
			return null;
		});
	}

	/** Whether the person may do the action to the record described by the facts. */
	protected boolean can(UUID person, String action, Object facts) {
		return asCaller(person, () -> this.authorization.can(action, facts));
	}

	/** How far the person's permission reaches, for a list. */
	protected Reach reachOf(UUID person, String action) {
		return asCaller(person, () -> this.authorization.reach(action));
	}

}
