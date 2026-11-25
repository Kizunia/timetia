package com.kibootia.backend.issues;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import com.kibootia.backend.rescheduling.ReschedulingApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issues, through the real stack: a class representative opens an issue about what the class wants, mentions the faculty it
 * concerns, and the faculty close it or link the ticket that answers it. An issue never changes the timetable.
 *
 * <p>CSE-A holds batches A1 and A2; Rao teaches OOP, Roy DBMS, and Mehta teaches in another division.
 */
class IssueApiIT extends ReschedulingApiTest {

	// --- opening ----------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a CR opens a theory issue for their division and mentions the faculty it concerns")
	void aCrOpensATheoryIssue() throws Exception {
		UUID cr = personWith("CR", this.divisionCseA);

		post(user(cr), "/api/v1/issues", theory("Extra DBMS lecture before the test", this.facultyRoy)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andExpect(jsonPath("$.sessionType").value("THEORY"))
			.andExpect(jsonPath("$.divisionId").value(this.divisionCseA))
			.andExpect(jsonPath("$.departmentId").value(this.deptCse))
			.andExpect(jsonPath("$.batchId").doesNotExist())
			.andExpect(jsonPath("$.openerUserId").value(cr.toString()))
			.andExpect(jsonPath("$.mentionedFacultyIds[0]").value(this.facultyRoy))
			.andExpect(jsonPath("$.linkedTicketIds", hasSize(0)));
		assertThat(count("select count(*) from audit_entry where action = 'issues.issue.opened'")).isEqualTo(1);
	}

	@Test
	@DisplayName("a BR opens a lab issue for their batch, and the division and department are worked out")
	void aBrOpensALabIssue() throws Exception {
		UUID br = personWith("BR", this.batchA1);

		post(user(br), "/api/v1/issues", lab("Lab 1 needs a repeat", this.facultyRao)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.sessionType").value("LAB"))
			.andExpect(jsonPath("$.batchId").value(this.batchA1))
			.andExpect(jsonPath("$.divisionId").value(this.divisionCseA))
			.andExpect(jsonPath("$.departmentId").value(this.deptCse));
	}

	@Test
	@DisplayName("only the representative of the place opens an issue: not a student, not a CR for a lab, not another division's CR")
	void whoMayOpen() throws Exception {
		String theory = theory("Extra class");
		String lab = lab("Extra lab");

		post(user(personWith("Student", this.batchA1)), "/api/v1/issues", theory).andExpect(status().isForbidden());
		post(user(personWith("CR", this.divisionCseA)), "/api/v1/issues", lab).andExpect(status().isForbidden());
		post(user(personWith("BR", this.batchA1)), "/api/v1/issues", theory).andExpect(status().isForbidden());
		post(user(personWith("BR", this.batchA2)), "/api/v1/issues", lab).andExpect(status().isForbidden());
		post(user(personWith("CR", this.divisionMeA)), "/api/v1/issues", theory).andExpect(status().isForbidden());
		post(user(RAO_USER), "/api/v1/issues", theory).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("the request is checked: the place fits the kind, faculty exist, and the place exists")
	void theRequestIsChecked() throws Exception {
		UUID cr = personWith("CR", this.divisionCseA);
		UUID br = personWith("BR", this.batchA1);

		post(user(cr), "/api/v1/issues", "{\"sessionType\":\"THEORY\",\"title\":\"x\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("divisionId")));
		post(user(cr), "/api/v1/issues", "{\"sessionType\":\"THEORY\",\"divisionId\":\"" + this.divisionCseA
				+ "\",\"batchId\":\"" + this.batchA1 + "\",\"title\":\"x\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("batchId")));
		post(user(br), "/api/v1/issues", "{\"sessionType\":\"LAB\",\"title\":\"x\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("batchId")));
		post(user(cr), "/api/v1/issues", "{\"sessionType\":\"THEORY\",\"divisionId\":\"" + this.divisionCseA
				+ "\",\"title\":\" \"}").andExpect(status().isBadRequest());
		post(user(cr), "/api/v1/issues", theory("x", UUID.randomUUID().toString())).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("unknown_faculty")));
		post(user(cr), "/api/v1/issues", "{\"sessionType\":\"THEORY\",\"divisionId\":\"" + UUID.randomUUID()
				+ "\",\"title\":\"x\"}").andExpect(status().isNotFound());
	}

	// --- seeing -----------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("who sees an issue follows the place and the people on it, and the list and the single issue agree for everyone")
	void visibility() throws Exception {
		UUID cr = personWith("CR", this.divisionCseA);
		UUID br = personWith("BR", this.batchA1);
		String theory = id(post(user(cr), "/api/v1/issues", theory("Extra DBMS", this.facultyRao)));
		String lab = id(post(user(br), "/api/v1/issues", lab("Repeat the lab", this.facultyRoy)));

		Map<String, RequestPostProcessor> people = new LinkedHashMap<>();
		people.put("admin", admin());
		people.put("the cr", user(cr));
		people.put("the br", user(br));
		people.put("another cr of the division", user(personWith("CR", this.divisionCseA)));
		people.put("another br of the batch", user(personWith("BR", this.batchA1)));
		people.put("br of the other batch", user(personWith("BR", this.batchA2)));
		people.put("hod cse", user(personWith("HOD", this.deptCse)));
		people.put("hod me", user(personWith("HOD", this.deptMe)));
		people.put("rao, mentioned on theory", user(RAO_USER));
		people.put("roy, mentioned on the lab", user(ROY_USER));
		people.put("mehta", user(MEHTA_USER));
		people.put("student", user(personWith("Student", this.batchA1)));
		people.put("nobody", user(somebody()));

		for (Map.Entry<String, RequestPostProcessor> person : people.entrySet()) {
			Set<String> allowed = Set.of(theory, lab).stream().filter((issue) -> {
				try {
					return get(person.getValue(), "/api/v1/issues/" + issue).andReturn().getResponse().getStatus() == 200;
				}
				catch (Exception ex) {
					throw new IllegalStateException(ex);
				}
			}).collect(Collectors.toSet());
			assertThat(listed(person.getValue(), "")).as("the list of " + person.getKey()).isEqualTo(allowed);
		}
		assertThat(listed(user(cr), "")).as("a CR sees the theory issues of the division, not the lab's").containsExactly(theory);
		assertThat(listed(user(br), "")).as("a BR sees the lab issues of the batch").containsExactly(lab);
		assertThat(listed(user(personWith("HOD", this.deptCse)), "")).containsExactlyInAnyOrder(theory, lab);
		assertThat(listed(user(RAO_USER), "")).as("a faculty member sees the issues that mention them").containsExactly(theory);
		assertThat(listed(user(MEHTA_USER), "")).isEmpty();
		assertThat(listed(user(somebody()), "")).isEmpty();
	}

	@Test
	@DisplayName("the list can be narrowed to the issues I opened and to open or closed ones")
	void filters() throws Exception {
		UUID cr = personWith("CR", this.divisionCseA);
		UUID other = personWith("CR", this.divisionCseA);
		String mine = id(post(user(cr), "/api/v1/issues", theory("Mine", this.facultyRao)));
		String theirs = id(post(user(other), "/api/v1/issues", theory("Theirs", this.facultyRao)));
		post(user(RAO_USER), "/api/v1/issues/" + theirs + "/close", null).andExpect(status().isOk());

		assertThat(listed(user(cr), "")).containsExactlyInAnyOrder(mine, theirs);
		assertThat(listed(user(cr), "&mine=true")).containsExactly(mine);
		assertThat(listed(user(cr), "&status=CLOSED")).containsExactly(theirs);
		assertThat(listed(user(cr), "&status=OPEN")).containsExactly(mine);
	}

	// --- closing, mentioning, linking -------------------------------------------------------------------------------

	@Test
	@DisplayName("the opener, or a faculty member mentioned, closes the issue; others cannot, and it closes once")
	void closing() throws Exception {
		UUID cr = personWith("CR", this.divisionCseA);
		String byOpener = id(post(user(cr), "/api/v1/issues", theory("Opener closes", this.facultyRao)));
		String byFaculty = id(post(user(cr), "/api/v1/issues", theory("Faculty closes", this.facultyRao)));

		// The HOD sees it through the department but is neither the opener nor mentioned; Roy cannot even see it.
		post(user(personWith("HOD", this.deptCse)), "/api/v1/issues/" + byOpener + "/close", null).andExpect(status().isForbidden());
		post(user(ROY_USER), "/api/v1/issues/" + byOpener + "/close", null).andExpect(status().isNotFound());

		post(user(cr), "/api/v1/issues/" + byOpener + "/close", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CLOSED"))
			.andExpect(jsonPath("$.closedByUserId").value(cr.toString()))
			.andExpect(jsonPath("$.closedAt").isNotEmpty());
		post(user(cr), "/api/v1/issues/" + byOpener + "/close", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("issues.issue_not_open"));
		post(user(RAO_USER), "/api/v1/issues/" + byFaculty + "/close", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.closedByUserId").value(RAO_USER.toString()));
		assertThat(count("select count(*) from audit_entry where action = 'issues.issue.closed'")).isEqualTo(2);
	}

	@Test
	@DisplayName("the opener mentions more faculty, who then see the issue; nobody else may, and a closed issue takes no more")
	void mentioning() throws Exception {
		UUID cr = personWith("CR", this.divisionCseA);
		String issue = id(post(user(cr), "/api/v1/issues", theory("Who can take it", this.facultyRao)));
		String path = "/api/v1/issues/" + issue + "/mentions";

		get(user(ROY_USER), "/api/v1/issues/" + issue).andExpect(status().isNotFound());
		post(user(RAO_USER), path, "{\"facultyId\":\"" + this.facultyRoy + "\"}").andExpect(status().isForbidden());
		post(user(cr), path, "{\"facultyId\":\"" + UUID.randomUUID() + "\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("unknown_faculty")));
		post(user(cr), path, "{\"facultyId\":\"" + this.facultyRoy + "\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.mentionedFacultyIds", hasSize(2)));
		// Mentioning again changes nothing.
		post(user(cr), path, "{\"facultyId\":\"" + this.facultyRoy + "\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.mentionedFacultyIds", hasSize(2)));
		get(user(ROY_USER), "/api/v1/issues/" + issue).andExpect(status().isOk());

		post(user(cr), "/api/v1/issues/" + issue + "/close", null).andExpect(status().isOk());
		post(user(cr), path, "{\"facultyId\":\"" + this.facultyMehta + "\"}").andExpect(status().isConflict());
	}

	@Test
	@DisplayName("the ticket that answers an issue is linked to it, if the person linking can see the ticket")
	void linking() throws Exception {
		UUID cr = personWith("CR", this.divisionCseA);
		String issue = id(post(user(cr), "/api/v1/issues", theory("Move the OOP lecture", this.facultyRao)));
		String lecture = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, move(lecture, "2026-07-09", 9, 10, this.room101));
		String hidden = raise(MEHTA_USER, cancel(lecture(this.theoryThermo, "2026-07-13"), "Away"));
		String path = "/api/v1/issues/" + issue + "/links";

		// Rao is mentioned, sees his own ticket, and links it.
		post(user(RAO_USER), path, "{\"ticketId\":\"" + ticket + "\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.linkedTicketIds[0]").value(ticket));
		post(user(RAO_USER), path, "{\"ticketId\":\"" + ticket + "\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.linkedTicketIds", hasSize(1)));
		// A ticket Rao cannot see is the same as no ticket; Roy cannot see the issue at all.
		post(user(RAO_USER), path, "{\"ticketId\":\"" + hidden + "\"}").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("issues.ticket_not_found"));
		post(user(RAO_USER), path, "{\"ticketId\":\"" + UUID.randomUUID() + "\"}").andExpect(status().isNotFound());
		post(user(ROY_USER), path, "{\"ticketId\":\"" + ticket + "\"}").andExpect(status().isNotFound());
		// The CR who opened it sees the theory ticket of the division and can link another.
		String second = raise(RAO_USER, move(lecture(this.theoryOop, "2026-07-13"), "2026-07-16", 9, 10, this.room101));
		post(user(cr), path, "{\"ticketId\":\"" + second + "\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.linkedTicketIds", hasSize(2)));

		// Linking changes nothing in the timetable.
		get(admin(), "/api/v1/timetable/live/" + lecture).andExpect(jsonPath("$.date").value("2026-07-06"));
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private String theory(String title, String... faculty) {
		return "{\"sessionType\":\"THEORY\",\"divisionId\":\"" + this.divisionCseA + "\",\"title\":\"" + title
				+ "\",\"description\":\"Please\"," + mentions(faculty) + "}";
	}

	private String lab(String title, String... faculty) {
		return "{\"sessionType\":\"LAB\",\"batchId\":\"" + this.batchA1 + "\",\"title\":\"" + title + "\","
				+ mentions(faculty) + "}";
	}

	private static String mentions(String... faculty) {
		return "\"mentionedFacultyIds\":[" + java.util.Arrays.stream(faculty).map((id) -> "\"" + id + "\"")
			.collect(Collectors.joining(",")) + "]";
	}

	private String id(org.springframework.test.web.servlet.ResultActions result) throws Exception {
		return read(result.andExpect(status().isCreated()), "$.id");
	}

	private Set<String> listed(RequestPostProcessor who, String query) throws Exception {
		String json = get(who, "/api/v1/issues?size=100" + query).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		java.util.List<String> ids = JsonPath.read(json, "$.items[*].id");
		return new java.util.HashSet<>(ids);
	}

}
