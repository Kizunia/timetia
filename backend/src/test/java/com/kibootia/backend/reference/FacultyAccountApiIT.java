package com.kibootia.backend.reference;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.timetable.TimetableApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Linking a faculty member to the account they sign in as, which makes their lectures theirs. */
class FacultyAccountApiIT extends TimetableApiTest {

	@Test
	@DisplayName("linking a faculty member to an account makes their assignments that user's, and null removes the link")
	void linkAndUnlink() throws Exception {
		UUID person = somebody();
		String path = "/api/v1/faculties/" + this.facultyRao + "/account";

		put(admin(), path, body(person, 0)).andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").value(person.toString()))
			.andExpect(jsonPath("$.version").value(1));
		get(user(person), "/api/v1/directory/me").andExpect(jsonPath("$.faculty[0].id").value(this.facultyRao))
			.andExpect(jsonPath("$.assignments", hasSize(2)));
		get(user(RAO_USER), "/api/v1/directory/me").andExpect(jsonPath("$.faculty", hasSize(0)));

		put(admin(), path, body(null, 1)).andExpect(status().isOk()).andExpect(jsonPath("$.userId").doesNotExist());
		get(user(person), "/api/v1/directory/me").andExpect(jsonPath("$.faculty", hasSize(0)));
		assertThat(count("select count(*) from audit_entry where action = 'reference.faculty.account-linked'")).isEqualTo(2);
	}

	@Test
	@DisplayName("an unknown user, a stale version and an unknown faculty member are refused")
	void refusals() throws Exception {
		UUID person = somebody();
		String path = "/api/v1/faculties/" + this.facultyRao + "/account";

		put(admin(), path, body(UUID.randomUUID(), 0)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("userId")));
		put(admin(), path, body(person, 7)).andExpect(status().isConflict());
		put(admin(), "/api/v1/faculties/" + UUID.randomUUID() + "/account", body(person, 0)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("only someone who manages the configuration may link an account")
	void onlyAdministrators() throws Exception {
		UUID person = somebody();
		String path = "/api/v1/faculties/" + this.facultyRao + "/account";

		put(user(person), path, body(person, 0)).andExpect(status().isForbidden());
		put(unverified(person), path, body(person, 0)).andExpect(status().isForbidden());
	}

	private static String body(UUID user, long version) {
		return "{\"userId\":" + ((user == null) ? "null" : "\"" + user + "\"") + ",\"version\":" + version + "}";
	}

}
