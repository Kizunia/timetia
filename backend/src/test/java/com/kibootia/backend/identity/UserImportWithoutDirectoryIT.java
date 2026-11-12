package com.kibootia.backend.identity;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.academic.application.RoleTemplateSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The application as it runs today: nothing provides departments, divisions and batches, so the import cannot check a
 * place and must not guess. Rows for roles that apply everywhere work; every row that names a place is refused, and a
 * person with such a row is left out entirely (Timetia ADR 0004 and 0006). This context has no scope directory.
 */
class UserImportWithoutDirectoryIT extends AdminApiTest {

	@Autowired
	RoleTemplateSeeder seeder;

	@BeforeEach
	void seed() throws Exception {
		this.seeder.seed();
		ensureAdmin();
	}

	@Test
	@DisplayName("roles that apply everywhere import; rows that name a place are refused as unvalidated, and nobody is invited for them")
	void failsClosedForPlaces() throws Exception {
		String csv = "email,role,scope\nfaculty@college.test,Faculty,\nboss@college.test,Super HOD,\n"
				+ "hod@college.test,HOD,CSE\ncr@college.test,CR,CSE-A\nstudent@college.test,Student,CSE-A1\n";

		this.mvc.perform(multipart("/api/v1/user-imports")
			.file(new MockMultipartFile("file", "people.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
			.with(admin())).andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.added").value(2))
			.andExpect(jsonPath("$.counts.errors").value(3))
			.andExpect(jsonPath("$.lines[0].outcome").value("ADDED"))
			.andExpect(jsonPath("$.lines[1].outcome").value("ADDED"))
			.andExpect(jsonPath("$.lines[2].code").value("scope_unvalidated"))
			.andExpect(jsonPath("$.lines[3].code").value("scope_unvalidated"))
			.andExpect(jsonPath("$.lines[4].code").value("scope_unvalidated"));

		assertThat(this.keycloak.countCalls("createUser")).as("only the two with global roles").isEqualTo(2);
		assertThat(count("select count(*) from user_profile")).isEqualTo(3);
		assertThat(count("select count(*) from user_profile where email like 'hod@%' or email like 'cr@%' or email like 'student@%'"))
			.isZero();
	}

}
