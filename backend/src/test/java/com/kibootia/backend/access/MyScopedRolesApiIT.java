package com.kibootia.backend.access;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.kibootia.backend.timetable.TimetableApiTest;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A person reads the roles they hold in places: the answer a screen uses to preselect their own division or batch. */
class MyScopedRolesApiIT extends TimetableApiTest {

	private static final String PATH = "/api/v1/me/scoped-roles";

	@Test
	@DisplayName("a CR of a division sees that one role, with the division's id and code")
	void aClassRepresentativeSeesTheirDivision() throws Exception {
		UUID cr = personWith("CR", this.divisionCseA);

		get(user(cr), PATH).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].roleName").value("CR"))
			.andExpect(jsonPath("$[0].scopeType").value("DIVISION"))
			.andExpect(jsonPath("$[0].scopeId").value(this.divisionCseA))
			.andExpect(jsonPath("$[0].scopeCode").value("CSE-A"));
	}

	@Test
	@DisplayName("a batch role carries the batch, and an HOD the department")
	void batchAndDepartmentRolesCarryTheirPlace() throws Exception {
		UUID student = personWith("Student", this.batchA1);
		UUID hod = personWith("HOD", this.deptCse);

		get(user(student), PATH).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].scopeType").value("BATCH"))
			.andExpect(jsonPath("$[0].scopeCode").value("CSE-A1"));
		get(user(hod), PATH).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].scopeType").value("DEPARTMENT"))
			.andExpect(jsonPath("$[0].scopeCode").value("CSE"));
	}

	@Test
	@DisplayName("a teacher's Faculty role applies everywhere, so it has no place")
	void aTeachersRoleHasNoPlace() throws Exception {
		assign(RAO_USER, "Faculty", null);

		get(user(RAO_USER), PATH).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].roleName").value("Faculty"))
			.andExpect(jsonPath("$[0].scopeType").value("GLOBAL"))
			.andExpect(jsonPath("$[0].scopeId").value(nullValue()))
			.andExpect(jsonPath("$[0].scopeCode").value(nullValue()));
	}

	@Test
	@DisplayName("a person sees only their own roles, never another person's")
	void aPersonSeesOnlyTheirOwnRoles() throws Exception {
		UUID cr = personWith("CR", this.divisionCseA);
		UUID student = personWith("Student", this.batchA2);

		get(user(student), PATH).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].roleName").value("Student"));
		get(user(cr), PATH).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	@DisplayName("a person with no role in any place gets an empty list")
	void noRoleGivesAnEmptyList() throws Exception {
		get(user(somebody()), PATH).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	@DisplayName("a person whose email is not verified is refused, and a request with no token is answered 401")
	void unverifiedAndAnonymousAreRefused() throws Exception {
		UUID cr = personWith("CR", this.divisionCseA);

		get(unverified(cr), PATH).andExpect(status().isForbidden());
		this.mvc.perform(MockMvcRequestBuilders.get(PATH)).andExpect(status().isUnauthorized());
	}

}
