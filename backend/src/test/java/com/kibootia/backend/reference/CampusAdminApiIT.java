package com.kibootia.backend.reference;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.academic.application.RoleTemplateSeeder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the campus admin APIs through the real stack (ADR 0043 conventions, CONFIG_MANAGE). Unlike {@code AcademicApiTest},
 * this test does <em>not</em> import the fake scope directory, so the real {@code JpaScopeDirectory} is what answers: the
 * end-to-end test proves a scoped role can finally be assigned in a department created over HTTP, which was the one thing
 * blocking Timetia's scoped roles.
 */
class CampusAdminApiIT extends AdminApiTest {

	@Autowired
	private RoleTemplateSeeder seeder;

	@BeforeEach
	@AfterEach
	void cleanAndSeed() {
		this.jdbc.update("delete from batch");
		this.jdbc.update("delete from division");
		this.jdbc.update("delete from department");
	}

	@BeforeEach
	void seedRoles() {
		// The base class wipes the role table around every test; the Timetia roles are needed for the scope-assignment test.
		this.seeder.seed();
	}

	// --- departments ------------------------------------------------------------------------------------------------

	@Test
	void adminCreatesReadsRenamesAndDeletesADepartment() throws Exception {
		String id = read(post(admin(), "/api/v1/departments", "{\"code\":\"CSE\",\"name\":\"Computer Science\"}")
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", containsString("/api/v1/departments/")))
			.andExpect(jsonPath("$.code").value("CSE"))
			.andExpect(jsonPath("$.name").value("Computer Science"))
			.andExpect(jsonPath("$.version").value(0)), "$.id");

		get(admin(), "/api/v1/departments/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("CSE"));

		get(admin(), "/api/v1/departments?size=100").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].code", hasItem("CSE")));

		put(admin(), "/api/v1/departments/" + id, "{\"name\":\"Comp Sci & Engg\",\"version\":0}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Comp Sci & Engg"))
			.andExpect(jsonPath("$.version").value(1));

		delete(admin(), "/api/v1/departments/" + id).andExpect(status().isNoContent());
		get(admin(), "/api/v1/departments/" + id).andExpect(status().isNotFound());
	}

	@Test
	void aDuplicateCodeIsRefused() throws Exception {
		post(admin(), "/api/v1/departments", "{\"code\":\"CSE\",\"name\":\"First\"}").andExpect(status().isCreated());
		post(admin(), "/api/v1/departments", "{\"code\":\"CSE\",\"name\":\"Second\"}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("reference.duplicate_code"));
	}

	@Test
	void aStaleRenameIsRefused() throws Exception {
		String id = read(post(admin(), "/api/v1/departments", "{\"code\":\"CSE\",\"name\":\"First\"}")
			.andExpect(status().isCreated()), "$.id");
		put(admin(), "/api/v1/departments/" + id, "{\"name\":\"Again\",\"version\":0}").andExpect(status().isOk());
		put(admin(), "/api/v1/departments/" + id, "{\"name\":\"Stale\",\"version\":0}").andExpect(status().isConflict());
	}

	@Test
	void anUnknownDepartmentIs404() throws Exception {
		get(admin(), "/api/v1/departments/" + UUID.randomUUID()).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("reference.department_not_found"));
	}

	@Test
	void anOrdinaryUserMayNotManageTheCampus() throws Exception {
		UUID user = signIn(ALICE);
		post(user(user), "/api/v1/departments", "{\"code\":\"X\",\"name\":\"Nope\"}").andExpect(status().isForbidden());
	}

	// --- divisions and batches --------------------------------------------------------------------------------------

	@Test
	void divisionsAndBatchesHangOffTheirParents() throws Exception {
		String department = read(post(admin(), "/api/v1/departments", "{\"code\":\"CSE\",\"name\":\"CS\"}")
			.andExpect(status().isCreated()), "$.id");

		String division = read(post(admin(), "/api/v1/divisions",
				"{\"code\":\"CSE-A\",\"name\":\"SY-CSSE-B\",\"departmentId\":\"" + department + "\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.departmentId").value(department)), "$.id");

		read(post(admin(), "/api/v1/batches",
				"{\"code\":\"CSE-A1\",\"name\":\"B1\",\"divisionId\":\"" + division + "\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.divisionId").value(division)), "$.id");

		get(admin(), "/api/v1/divisions?department=" + department).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].code", hasItem("CSE-A")));
	}

	@Test
	void aDivisionUnderAnUnknownDepartmentIsRejected() throws Exception {
		post(admin(), "/api/v1/divisions",
				"{\"code\":\"X-A\",\"name\":\"X\",\"departmentId\":\"" + UUID.randomUUID() + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("departmentId")));
	}

	// --- the unblock: a real scope resolves a created place ---------------------------------------------------------

	@Test
	void anHodCanBeAssignedInADepartmentCreatedOverHttp() throws Exception {
		String department = read(post(admin(), "/api/v1/departments", "{\"code\":\"CSE\",\"name\":\"CS\"}")
			.andExpect(status().isCreated()), "$.id");
		UUID person = signIn(BOB);

		// The real ScopeDirectory resolves the created department, so the assignment is accepted.
		post(admin(), "/api/v1/users/" + person + "/scoped-roles",
				"{\"roleId\":\"" + hodRoleId() + "\",\"scopeId\":\"" + department + "\"}").andExpect(status().isOk());

		// A department that does not exist is refused by that same real directory.
		post(admin(), "/api/v1/users/" + person + "/scoped-roles",
				"{\"roleId\":\"" + hodRoleId() + "\",\"scopeId\":\"" + UUID.randomUUID() + "\"}")
			.andExpect(status().isBadRequest());
	}

	private String hodRoleId() throws Exception {
		String json = get(admin(), "/api/v1/roles?size=100").andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(json, "$.items[?(@.name == 'HOD')].id");
		return ids.get(0);
	}

}
