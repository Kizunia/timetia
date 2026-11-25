package com.kibootia.backend.issues;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.rescheduling.ReschedulingApiTest;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The names an issue carries so that a screen never shows an id: the people, the place and the faculty mentioned. */
class IssueNamesApiIT extends ReschedulingApiTest {

	@Test
	@DisplayName("a theory issue carries the opener, the division code and the mentioned faculty, on one issue and in a list")
	void theoryIssueNames() throws Exception {
		UUID cr = personWith("CR", this.divisionCseA);
		String issue = read(post(user(cr), "/api/v1/issues", "{\"sessionType\":\"THEORY\",\"divisionId\":\""
				+ this.divisionCseA + "\",\"title\":\"Extra class\",\"mentionedFacultyIds\":[\"" + this.facultyRao + "\"]}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.openerName", startsWith("Given")))
			.andExpect(jsonPath("$.divisionCode").value("CSE-A"))
			.andExpect(jsonPath("$.batchCode").doesNotExist())
			.andExpect(jsonPath("$.closedByName").doesNotExist())
			.andExpect(jsonPath("$.mentionedFaculty[0].id").value(this.facultyRao))
			.andExpect(jsonPath("$.mentionedFaculty[0].name").value("Dr Rao")), "$.id");

		get(user(cr), "/api/v1/issues").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].divisionCode", contains("CSE-A")))
			.andExpect(jsonPath("$.items[0].mentionedFaculty[0].name").value("Dr Rao"));

		post(user(cr), "/api/v1/issues/" + issue + "/close", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.closedByName", startsWith("Given")));
	}

	@Test
	@DisplayName("a lab issue carries the batch code, and a deleted faculty member leaves the name out without failing")
	void labIssueNamesAndADeletedFaculty() throws Exception {
		UUID br = personWith("BR", this.batchA1);
		String issue = read(post(user(br), "/api/v1/issues", "{\"sessionType\":\"LAB\",\"batchId\":\"" + this.batchA1
				+ "\",\"title\":\"Repeat lab 1\",\"mentionedFacultyIds\":[\"" + this.facultyRao + "\"]}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.batchCode").value("CSE-A1")), "$.id");

		delete(admin(), "/api/v1/faculties/" + this.facultyRao).andExpect(status().isNoContent());

		get(user(br), "/api/v1/issues/" + issue).andExpect(status().isOk())
			.andExpect(jsonPath("$.mentionedFaculty[0].id").value(this.facultyRao))
			.andExpect(jsonPath("$.mentionedFaculty[0].name").doesNotExist());
	}

}
