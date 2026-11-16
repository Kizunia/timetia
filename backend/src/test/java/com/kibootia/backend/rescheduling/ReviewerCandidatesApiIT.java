package com.kibootia.backend.rescheduling;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who the author of a ticket can ask to review it: the HODs of the department, the CR (theory) or BR (lab) of the class and the
 * faculty the ticket affects, by name, each with the capacity to send. Nobody else, and only to the author.
 */
class ReviewerCandidatesApiIT extends ReschedulingApiTest {

	private UUID hodCse;

	private UUID hodMe;

	private UUID cr;

	private UUID br;

	private UUID student;

	@BeforeEach
	void people() throws Exception {
		this.hodCse = personWith("HOD", this.deptCse);
		this.hodMe = personWith("HOD", this.deptMe);
		this.cr = personWith("CR", this.divisionCseA);
		this.br = personWith("BR", this.batchA1);
		this.student = personWith("Student", this.batchA1);
	}

	@Test
	@DisplayName("a theory ticket offers the HOD of the department and the CR of the division, and nobody else")
	void aTheoryTicketOffersTheHodAndTheCr() throws Exception {
		String ticket = raise(RAO_USER, move(lecture(this.theoryOop, "2026-07-06"), "2026-07-09", 9, 10, this.room101));

		get(user(RAO_USER), "/api/v1/tickets/" + ticket + "/reviewer-candidates").andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[*].userId", contains(this.hodCse.toString(), this.cr.toString())))
			.andExpect(jsonPath("$[*].capacity", contains("HOD", "CR")))
			.andExpect(jsonPath("$[0].name").value(not(emptyOrNullString())))
			.andExpect(jsonPath("$[1].name").value(not(emptyOrNullString())));
	}

	@Test
	@DisplayName("a lab ticket offers the HOD and the BR of the batch, not the CR")
	void aLabTicketOffersTheHodAndTheBr() throws Exception {
		String ticket = raise(RAO_USER, move(lecture(this.labA1, "2026-07-07"), "2026-07-09", 9, 11, this.lab1));

		get(user(RAO_USER), "/api/v1/tickets/" + ticket + "/reviewer-candidates").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].userId", contains(this.hodCse.toString(), this.br.toString())))
			.andExpect(jsonPath("$[*].capacity", contains("HOD", "BR")));
	}

	@Test
	@DisplayName("an exchange also offers the other teacher, as an affected faculty member")
	void anExchangeOffersTheOtherTeacher() throws Exception {
		String ticket = raise(RAO_USER, "{\"type\":\"EXCHANGE\",\"lectureId\":\"" + lecture(this.theoryOop, "2026-07-06")
				+ "\",\"otherLectureId\":\"" + lecture(this.theoryDbms, "2026-07-06") + "\"}");

		get(user(RAO_USER), "/api/v1/tickets/" + ticket + "/reviewer-candidates").andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[*].capacity", contains("HOD", "CR", "AFFECTED_FACULTY")))
			.andExpect(jsonPath("$[2].userId").value(ROY_USER.toString()))
			.andExpect(jsonPath("$[2].name").value(not(emptyOrNullString())));
	}

	@Test
	@DisplayName("the author never appears, even when they hold the role themselves, and a person is listed once")
	void theAuthorNeverAppearsAndNobodyTwice() throws Exception {
		// The CR of the division also teaches in it: they are the author of a ticket for their own lecture, and a CR for it.
		assign(RAO_USER, "CR", this.divisionCseA);
		String ticket = raise(RAO_USER, move(lecture(this.theoryOop, "2026-07-06"), "2026-07-09", 9, 10, this.room101));

		get(user(RAO_USER), "/api/v1/tickets/" + ticket + "/reviewer-candidates").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].userId", contains(this.hodCse.toString(), this.cr.toString())));
	}

	@Test
	@DisplayName("someone who cannot sign in is not offered")
	void aDisabledAccountIsNotOffered() throws Exception {
		post(admin(), "/api/v1/users/" + this.cr + "/disable", null).andExpect(status().isOk());
		String ticket = raise(RAO_USER, move(lecture(this.theoryOop, "2026-07-06"), "2026-07-09", 9, 10, this.room101));

		get(user(RAO_USER), "/api/v1/tickets/" + ticket + "/reviewer-candidates").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].capacity", contains("HOD")));
	}

	@Test
	@DisplayName("only the author may ask: someone who cannot see the ticket gets 404, an HOD who can see it gets 403")
	void onlyTheAuthorMayAsk() throws Exception {
		String ticket = raise(RAO_USER, move(lecture(this.theoryOop, "2026-07-06"), "2026-07-09", 9, 10, this.room101));
		String path = "/api/v1/tickets/" + ticket + "/reviewer-candidates";

		get(user(MEHTA_USER), path).andExpect(status().isNotFound());
		get(user(this.student), path).andExpect(status().isNotFound());
		get(user(this.hodMe), path).andExpect(status().isNotFound());
		get(user(this.hodCse), path).andExpect(status().isForbidden());
		get(user(this.cr), path).andExpect(status().isForbidden());
		get(user(RAO_USER), path).andExpect(status().isOk());
	}

	@Test
	@DisplayName("a ticket that does not exist is 404, and a caller with no token is 401")
	void anUnknownTicketIsNotFound() throws Exception {
		get(user(RAO_USER), "/api/v1/tickets/" + UUID.randomUUID() + "/reviewer-candidates").andExpect(status().isNotFound());
		this.mvc.perform(MockMvcRequestBuilders.get("/api/v1/tickets/" + UUID.randomUUID() + "/reviewer-candidates"))
			.andExpect(status().isUnauthorized());
	}

}
