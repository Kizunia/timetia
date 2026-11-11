package com.kibootia.backend.access.application;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.kibootia.backend.AdminApiTest;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What starting the application does to the built-in roles (ADR 0041): it offers the user role each permission a module
 * flags for it, once, so an administrator's removals are still there after the next start.
 */
class BuiltInRolesIT extends AdminApiTest {

	@Autowired
	BuiltInRoles builtInRoles;

	@Test
	@DisplayName("a permission an administrator took off the user role stays off after a restart, a newly flagged one is offered once")
	void removalsSurviveAndNewPermissionsAreOffered() throws Exception {
		ensureAdmin();
		String userRole = builtInRoleId("USER");
		String current = get(admin(), "/api/v1/roles/" + userRole).andReturn().getResponse().getContentAsString();
		long version = ((Number) JsonPath.read(current, "$.version")).longValue();
		put(admin(), "/api/v1/roles/" + userRole, "{\"name\":\"User\",\"description\":\"x\",\"permissions\":"
				+ "[\"widgets.widgets.view\",\"widgets.widgets.update\",\"widgets.widgets.delete\"],\"version\":" + version + "}")
			.andExpect(status().isOk());

		// A permission the user role was never offered, as when a module adds one in a new release.
		this.jdbc.update("delete from role_permission_seed where permission = 'widgets.widgets.update'");
		this.jdbc.update("delete from role_permission where permission = 'widgets.widgets.update'");

		this.builtInRoles.run(null);

		get(admin(), "/api/v1/roles/" + userRole).andExpect(status().isOk())
			.andExpect(jsonPath("$.permissions", hasItem("widgets.widgets.update")))
			.andExpect(jsonPath("$.permissions", not(hasItem("widgets.widgets.create"))));

		this.builtInRoles.run(null);
		get(admin(), "/api/v1/roles/" + userRole).andExpect(jsonPath("$.permissions", not(hasItem("widgets.widgets.create"))));
	}

	@Test
	@DisplayName("the built-in roles are created again when they are missing, so a sign-in never fails for want of them")
	void missingBuiltInRolesAreRecreated() throws Exception {
		ensureAdmin();
		this.jdbc.update("delete from role_assignment");
		this.jdbc.update("delete from role_permission");
		this.jdbc.update("delete from role_permission_seed");
		this.jdbc.update("delete from role");
		this.jdbc.update("delete from user_profile");

		get(user(ALICE), "/api/v1/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.permissions", hasItem("widgets.widgets.create")));
	}

}
