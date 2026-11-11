package com.kibootia.backend.access;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.jayway.jsonpath.JsonPath;
import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.academic.Facts;
import com.kibootia.backend.academic.application.RoleTemplateSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The application itself has no module that provides departments, divisions and batches yet, so it cannot tell whether a
 * place exists. A role that needs a place is then refused, so nothing is silently unvalidated; a role that applies
 * everywhere needs no place and still works (Timetia ADR 0004). This context has no scope directory, unlike the others.
 */
class ScopedAssignmentsWithoutDirectoryIT extends AdminApiTest {

	@Autowired
	RoleTemplateSeeder seeder;

	@BeforeEach
	void seed() throws Exception {
		this.seeder.seed();
		ensureAdmin();
		signIn(ALICE);
	}

	private String roleId(String name) throws Exception {
		String json = get(admin(), "/api/v1/roles?size=100").andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(json, "$.items[?(@.name == '" + name + "')].id");
		return ids.get(0);
	}

	@Test
	@DisplayName("a role for a department, division or batch cannot be given while nothing can say the place exists")
	void failsClosed() throws Exception {
		String hod = roleId("HOD");

		put(admin(), "/api/v1/users/" + ALICE + "/scoped-roles", "{\"assignments\":[{\"roleId\":\"" + hod + "\",\"scopeId\":\""
				+ Facts.DEPARTMENT_A + "\"}]}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("access.scope_unvalidated"));
		assertThat(count("select count(*) from role_assignment where role_id = unhex(?)", hod.replace("-", ""))).isZero();
	}

	@Test
	@DisplayName("a role that applies everywhere is still given, because it names no place")
	void globalRolesStillWork() throws Exception {
		put(admin(), "/api/v1/users/" + ALICE + "/scoped-roles", "{\"assignments\":[{\"roleId\":\"" + roleId("Faculty") + "\"}]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].roleName").value("Faculty"));
	}

}
