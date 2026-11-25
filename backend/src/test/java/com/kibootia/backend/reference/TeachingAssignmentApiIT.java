package com.kibootia.backend.reference;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.AdminApiTest;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the teaching-assignment API through the real stack (ADR 0043, CONFIG_MANAGE), proving the batch rule (theory
 * names no batch; a lab names a batch of its division), reference validation and the duplicate guard.
 */
class TeachingAssignmentApiIT extends AdminApiTest {

	private String department;

	private String division;

	private String batch;

	private String subject;

	private String faculty;

	// One @BeforeEach so cleaning always runs before seeding (JUnit does not order multiple @BeforeEach methods).
	@BeforeEach
	void cleanAndSeed() throws Exception {
		cleanReferenceTables();
		seedGraph();
	}

	@AfterEach
	void cleanReferenceTables() {
		this.jdbc.update("delete from teaching_assignment");
		this.jdbc.update("delete from faculty");
		this.jdbc.update("delete from subject");
		this.jdbc.update("delete from batch");
		this.jdbc.update("delete from division");
		this.jdbc.update("delete from department");
	}

	private void seedGraph() throws Exception {
		this.department = read(post(admin(), "/api/v1/departments", "{\"code\":\"CSE\",\"name\":\"CS\"}")
			.andExpect(status().isCreated()), "$.id");
		this.division = read(post(admin(), "/api/v1/divisions",
				"{\"code\":\"CSE-A\",\"name\":\"SY-CSSE-B\",\"departmentId\":\"" + this.department + "\"}")
			.andExpect(status().isCreated()), "$.id");
		this.batch = read(post(admin(), "/api/v1/batches",
				"{\"code\":\"CSE-A1\",\"name\":\"B1\",\"divisionId\":\"" + this.division + "\"}")
			.andExpect(status().isCreated()), "$.id");
		this.subject = read(post(admin(), "/api/v1/subjects", "{\"code\":\"OOP\",\"name\":\"OOP\"}")
			.andExpect(status().isCreated()), "$.id");
		this.faculty = read(post(admin(), "/api/v1/faculties",
				"{\"code\":\"F001\",\"name\":\"Dr Rao\",\"departmentId\":\"" + this.department + "\"}")
			.andExpect(status().isCreated()), "$.id");
	}

	@Test
	void createsATheoryAssignmentWithoutABatch() throws Exception {
		post(admin(), "/api/v1/teaching-assignments", theory(null))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.sessionType").value("THEORY"))
			.andExpect(jsonPath("$.batchId").doesNotExist());
	}

	@Test
	void createsALabAssignmentForABatch() throws Exception {
		post(admin(), "/api/v1/teaching-assignments", assignment("LAB", this.batch))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.sessionType").value("LAB"))
			.andExpect(jsonPath("$.batchId").value(this.batch));
	}

	@Test
	void aTheoryAssignmentMayNotNameABatch() throws Exception {
		post(admin(), "/api/v1/teaching-assignments", theory(this.batch))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("batchId")));
	}

	@Test
	void aLabAssignmentNeedsABatch() throws Exception {
		post(admin(), "/api/v1/teaching-assignments", assignment("LAB", null))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("batch_required")));
	}

	@Test
	void aDuplicateAssignmentIsRefused() throws Exception {
		post(admin(), "/api/v1/teaching-assignments", theory(null)).andExpect(status().isCreated());
		post(admin(), "/api/v1/teaching-assignments", theory(null)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("reference.duplicate_teaching_assignment"));
	}

	@Test
	void unknownReferencesAreReported() throws Exception {
		String body = "{\"divisionId\":\"" + UUID.randomUUID() + "\",\"subjectId\":\"" + UUID.randomUUID()
				+ "\",\"facultyId\":\"" + UUID.randomUUID() + "\",\"sessionType\":\"THEORY\"}";
		post(admin(), "/api/v1/teaching-assignments", body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("divisionId")))
			.andExpect(jsonPath("$.errors[*].field", hasItem("subjectId")))
			.andExpect(jsonPath("$.errors[*].field", hasItem("facultyId")));
	}

	@Test
	void listsAndDeletes() throws Exception {
		String id = read(post(admin(), "/api/v1/teaching-assignments", theory(null)).andExpect(status().isCreated()),
				"$.id");
		get(admin(), "/api/v1/teaching-assignments?faculty=" + this.faculty).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].id", hasItem(id)));
		delete(admin(), "/api/v1/teaching-assignments/" + id).andExpect(status().isNoContent());
		get(admin(), "/api/v1/teaching-assignments/" + id).andExpect(status().isNotFound());
	}

	private String theory(String batchId) {
		return assignment("THEORY", batchId);
	}

	private String assignment(String sessionType, String batchId) {
		String batch = (batchId == null) ? "" : ",\"batchId\":\"" + batchId + "\"";
		return "{\"divisionId\":\"" + this.division + "\",\"subjectId\":\"" + this.subject + "\",\"facultyId\":\""
				+ this.faculty + "\",\"sessionType\":\"" + sessionType + "\"" + batch + "}";
	}

}
