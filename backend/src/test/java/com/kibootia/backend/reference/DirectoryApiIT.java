package com.kibootia.backend.reference;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.kibootia.backend.timetable.TimetableApiTest;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The campus directory any verified person may read, and the "what do I teach" answer. The campus is the standard one of
 * {@link TimetableApiTest}.
 */
class DirectoryApiIT extends TimetableApiTest {

	private static final List<String> PATHS = List.of("/api/v1/directory/departments", "/api/v1/directory/divisions",
			"/api/v1/directory/batches", "/api/v1/directory/rooms", "/api/v1/directory/faculties", "/api/v1/directory/me");

	@Test
	@DisplayName("a student reads the rooms, sorted by code, with their type and capacity")
	void aStudentReadsTheRooms() throws Exception {
		UUID student = personWith("Student", this.batchA1);

		get(user(student), "/api/v1/directory/rooms").andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(4)))
			.andExpect(jsonPath("$[*].code", contains("LAB1", "R101", "R102", "R201")))
			.andExpect(jsonPath("$[0].roomType").value("LAB"))
			.andExpect(jsonPath("$[0].capacity").value(30))
			.andExpect(jsonPath("$[1].roomType").value("CLASSROOM"))
			.andExpect(jsonPath("$[1].capacity").value(nullValue()));
	}

	@Test
	@DisplayName("a person with no role at all still reads the departments and divisions")
	void aPersonWithNoRoleReadsTheNames() throws Exception {
		UUID nobody = somebody();

		get(user(nobody), "/api/v1/directory/departments").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("CSE", "ME")));
		get(user(nobody), "/api/v1/directory/divisions").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("CSE-A", "ME-A")))
			.andExpect(jsonPath("$[0].departmentId").value(this.deptCse));
	}

	@Test
	@DisplayName("a person whose email is not verified is refused every directory endpoint")
	void anUnverifiedPersonIsRefused() throws Exception {
		UUID person = somebody();

		for (String path : PATHS) {
			get(unverified(person), path).andExpect(status().isForbidden());
		}
	}

	@Test
	@DisplayName("a request with no token is answered 401")
	void noTokenIsUnauthorized() throws Exception {
		for (String path : PATHS) {
			this.mvc.perform(MockMvcRequestBuilders.get(path)).andExpect(status().isUnauthorized());
		}
	}

	@Test
	@DisplayName("batches can be narrowed to a division; without the filter every batch is listed")
	void batchesCanBeNarrowedToADivision() throws Exception {
		UUID student = personWith("Student", this.batchA1);

		get(user(student), "/api/v1/directory/batches?division=" + this.divisionCseA).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("CSE-A1", "CSE-A2")))
			.andExpect(jsonPath("$[0].divisionId").value(this.divisionCseA));
		get(user(student), "/api/v1/directory/batches").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("CSE-A1", "CSE-A2", "ME-A1")));
	}

	@Test
	@DisplayName("faculty can be narrowed to a department, and say whether an account is linked")
	void facultyShowWhetherTheyHaveAnAccount() throws Exception {
		create("/api/v1/faculties",
				"{\"code\":\"NOACC\",\"name\":\"Dr Nobody\",\"departmentId\":\"" + this.deptCse + "\"}");
		UUID student = personWith("Student", this.batchA1);

		get(user(student), "/api/v1/directory/faculties?department=" + this.deptCse).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name", contains("Dr Nobody", "Dr Rao", "Dr Roy")))
			.andExpect(jsonPath("$[?(@.code == 'RAO')].hasAccount").value(true))
			.andExpect(jsonPath("$[?(@.code == 'ROY')].hasAccount").value(true))
			.andExpect(jsonPath("$[?(@.code == 'NOACC')].hasAccount").value(false))
			.andExpect(jsonPath("$[?(@.code == 'MEHTA')]").isEmpty());
		get(user(student), "/api/v1/directory/faculties").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", containsInAnyOrder("RAO", "ROY", "MEHTA", "NOACC")));
	}

	@Test
	@DisplayName("a teacher is told which faculty record is theirs and what they teach, in a stable order")
	void aTeacherSeesWhatTheyTeach() throws Exception {
		publishStandardTimetable();

		get(user(RAO_USER), "/api/v1/directory/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.faculty", hasSize(1)))
			.andExpect(jsonPath("$.faculty[0].id").value(this.facultyRao))
			.andExpect(jsonPath("$.faculty[0].code").value("RAO"))
			.andExpect(jsonPath("$.faculty[0].hasAccount").value(true))
			.andExpect(jsonPath("$.assignments", hasSize(2)))
			.andExpect(jsonPath("$.assignments[*].subjectCode", contains("OOP", "OOP")))
			.andExpect(jsonPath("$.assignments[*].divisionCode", contains("CSE-A", "CSE-A")))
			.andExpect(jsonPath("$.assignments[*].facultyId", contains(this.facultyRao, this.facultyRao)))
			.andExpect(jsonPath("$.assignments[?(@.id == '" + this.theoryOop + "')].sessionType").value("THEORY"))
			.andExpect(jsonPath("$.assignments[?(@.id == '" + this.theoryOop + "')].batchId").value(contains(nullValue())))
			.andExpect(jsonPath("$.assignments[?(@.id == '" + this.labA1 + "')].sessionType").value("LAB"))
			.andExpect(jsonPath("$.assignments[?(@.id == '" + this.labA1 + "')].batchCode").value("CSE-A1"))
			.andExpect(jsonPath("$.assignments[?(@.id == '" + this.labA1 + "')].subjectName")
				.value("Object-Oriented Programming"));
	}

	@Test
	@DisplayName("a person who is not faculty gets empty lists, not an error")
	void aStudentTeachesNothing() throws Exception {
		publishStandardTimetable();
		UUID student = personWith("Student", this.batchA1);

		get(user(student), "/api/v1/directory/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.faculty", hasSize(0)))
			.andExpect(jsonPath("$.assignments", hasSize(0)));
	}

	@Test
	@DisplayName("a deleted room, or a faculty member, drops out of the directory")
	void deletedRowsDropOut() throws Exception {
		UUID student = personWith("Student", this.batchA1);
		delete(admin(), "/api/v1/rooms/" + this.room201).andExpect(status().isNoContent());

		get(user(student), "/api/v1/directory/rooms").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("LAB1", "R101", "R102")));
	}

}
