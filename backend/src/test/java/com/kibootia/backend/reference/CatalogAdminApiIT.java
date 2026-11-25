package com.kibootia.backend.reference;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.AdminApiTest;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Drives the reference catalog APIs (subjects, rooms, faculty, terms) through the real stack (ADR 0043, CONFIG_MANAGE). */
class CatalogAdminApiIT extends AdminApiTest {

	@BeforeEach
	@AfterEach
	void cleanReferenceTables() {
		this.jdbc.update("delete from faculty");
		this.jdbc.update("delete from subject");
		this.jdbc.update("delete from room");
		this.jdbc.update("delete from academic_term");
		this.jdbc.update("delete from batch");
		this.jdbc.update("delete from division");
		this.jdbc.update("delete from department");
	}

	@Test
	void createsReadsListsAndDeletesASubject() throws Exception {
		String id = read(post(admin(), "/api/v1/subjects", "{\"code\":\"OOP\",\"name\":\"Object-Oriented Programming\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("OOP")), "$.id");

		get(admin(), "/api/v1/subjects?size=100").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].code", hasItem("OOP")));
		delete(admin(), "/api/v1/subjects/" + id).andExpect(status().isNoContent());
		get(admin(), "/api/v1/subjects/" + id).andExpect(status().isNotFound());
	}

	@Test
	void aDuplicateSubjectCodeIsRefused() throws Exception {
		post(admin(), "/api/v1/subjects", "{\"code\":\"OOP\",\"name\":\"First\"}").andExpect(status().isCreated());
		post(admin(), "/api/v1/subjects", "{\"code\":\"OOP\",\"name\":\"Second\"}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("reference.duplicate_code"));
	}

	@Test
	void createsARoomWithItsTypeAndCapacity() throws Exception {
		post(admin(), "/api/v1/rooms", "{\"code\":\"B101\",\"name\":\"Lab 1\",\"roomType\":\"LAB\",\"capacity\":30}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.roomType").value("LAB"))
			.andExpect(jsonPath("$.capacity").value(30));
	}

	@Test
	void aTermEndingBeforeItStartsIsRejected() throws Exception {
		post(admin(), "/api/v1/academic-terms",
				"{\"code\":\"SEM3\",\"name\":\"Semester 3\",\"startDate\":\"2026-07-06\",\"endDate\":\"2026-06-06\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("endDate")));
	}

	@Test
	void aFacultyNeedsAnExistingDepartment() throws Exception {
		post(admin(), "/api/v1/faculties",
				"{\"code\":\"F001\",\"name\":\"Dr Rao\",\"departmentId\":\"" + UUID.randomUUID() + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("departmentId")));

		String department = read(post(admin(), "/api/v1/departments", "{\"code\":\"CSE\",\"name\":\"CS\"}")
			.andExpect(status().isCreated()), "$.id");
		UUID user = UUID.randomUUID();
		post(admin(), "/api/v1/faculties", "{\"code\":\"F001\",\"name\":\"Dr Rao\",\"departmentId\":\"" + department
				+ "\",\"userId\":\"" + user + "\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.departmentId").value(department))
			.andExpect(jsonPath("$.userId").value(user.toString()));
	}

	@Test
	void anOrdinaryUserMayNotManageTheCatalog() throws Exception {
		UUID user = signIn(ALICE);
		post(user(user), "/api/v1/subjects", "{\"code\":\"X\",\"name\":\"Nope\"}").andExpect(status().isForbidden());
	}

}
