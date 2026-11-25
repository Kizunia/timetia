package com.kibootia.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What must be true of the application whichever default-on modules a fork removed or switched off (ADR 0024). It is the
 * test group the module-removal CI job runs for every row of its matrix (together with the migrate-from-empty test):
 * the application starts against MySQL, the core API answers, and a module that is off or gone says so instead of
 * failing. It names no removable module by class, so it compiles with any of them deleted, and it reads the switches
 * from the environment the job set.
 */
@Tag("startup")
class ModuleStartupIT extends AdminApiTest {

	@Autowired
	Environment environment;

	private boolean filesOn() {
		return this.environment.getProperty("kibootia.files.enabled", Boolean.class, true);
	}

	private static boolean notesPresent() {
		return ClassUtils.isPresent("com.kibootia.backend.notes.NotesPermissions", ModuleStartupIT.class.getClassLoader());
	}

	@Test
	@DisplayName("the core API answers: the signed-in user, users, roles, permissions and the audit log")
	void coreApiAnswers() throws Exception {
		ensureAdmin();

		get(admin(), "/api/v1/me").andExpect(status().isOk()).andExpect(jsonPath("$.permissions").isArray());
		get(admin(), "/api/v1/users").andExpect(status().isOk());
		get(admin(), "/api/v1/roles").andExpect(status().isOk());
		get(admin(), "/api/v1/permissions").andExpect(status().isOk());
		get(admin(), "/api/v1/audit-entries").andExpect(status().isOk());
		get(user(ALICE), "/api/v1/me").andExpect(status().isOk());
	}

	@Test
	@DisplayName("the files module is on or off as the environment says, and the profile and the avatar endpoint agree")
	void filesAreAsConfigured() throws Exception {
		signIn(ALICE);
		boolean on = filesOn();

		get(user(ALICE), "/api/v1/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.avatarsEnabled").value(on))
			.andExpect(jsonPath("$.filesEnabled").value(on));
		var avatar = get(user(ALICE), "/api/v1/users/" + ALICE + "/avatar").andExpect(status().isNotFound());
		avatar.andExpect(jsonPath("$.code").value(on ? "identity.avatar_not_found" : "files.disabled"));
	}

	@Test
	@DisplayName("the notes endpoints exist when the module is there, and are simply absent when it was removed")
	void notesAreThereOrGone() throws Exception {
		ensureAdmin();

		if (notesPresent()) {
			get(admin(), "/api/v1/notes").andExpect(status().isOk());
		}
		else {
			get(admin(), "/api/v1/notes").andExpect(status().isNotFound());
		}
		assertThat(ClassUtils.isPresent("com.kibootia.backend.identity.IdentityPermissions", getClass().getClassLoader()))
			.as("the core modules are always there")
			.isTrue();
	}

}
