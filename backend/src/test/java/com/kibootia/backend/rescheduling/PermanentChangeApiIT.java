package com.kibootia.backend.rescheduling;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Permanent changes, through the real stack: a lecture of the weekly timetable moves every week from a date onward. It always
 * needs an HOD, and it applies to all the lectures that remain together, or to none.
 *
 * <p>Rao's OOP theory is on Mondays 9-10 in R101, on the 6th, 13th, 20th and 27th of July; Thursday 9-10 is free. The
 * change moves it to Thursdays.
 */
class PermanentChangeApiIT extends ReschedulingApiTest {

	// --- raising ----------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a permanent change is one ticket with a move for each remaining lecture of the weekly entry")
	void oneMovePerLecture() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");

		post(user(RAO_USER), "/api/v1/tickets", permanent(oop, "2026-07-09")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("PERMANENT_CHANGE"))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.conflicted").value(false))
			.andExpect(jsonPath("$.changes", hasSize(4)))
			.andExpect(jsonPath("$.changes[0].target.date").value("2026-07-09"))
			.andExpect(jsonPath("$.changes[1].target.date").value("2026-07-16"))
			.andExpect(jsonPath("$.changes[3].target.date").value("2026-07-30"))
			.andExpect(jsonPath("$.changes[3].target.startTime").value("09:00:00"))
			.andExpect(jsonPath("$.expiresAt").value("2026-07-06T03:30:00Z"));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.date").value("2026-07-06"));
	}

	@Test
	@DisplayName("lectures that are cancelled or already changed are left out, and so are the ones before the lecture raised on")
	void someLecturesAreLeftAlone() throws Exception {
		applyOk(RAO_USER, raise(RAO_USER, cancel(lecture(this.theoryOop, "2026-07-20"), "Away")));
		applyOk(RAO_USER, raise(RAO_USER, move(lecture(this.theoryOop, "2026-07-27"), "2026-07-29", 9, 10, this.room101)));

		// From the 13th: the 13th itself, and nothing else is left to change (the 20th is cancelled, the 27th was moved).
		post(user(RAO_USER), "/api/v1/tickets", permanent(lecture(this.theoryOop, "2026-07-13"), "2026-07-16"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.changes", hasSize(1)));
	}

	@Test
	@DisplayName("only a lecture of the weekly timetable can change permanently, and the request is checked as a move is")
	void theRequestIsChecked() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String extra = raise(RAO_USER, "{\"type\":\"EXTRA\",\"assignmentId\":\"" + this.theoryOop + "\",\"target\":"
				+ target("2026-07-10", 9, 10, this.room101) + "}");
		applyOk(RAO_USER, extra);
		String extraLecture = read(get(user(RAO_USER), "/api/v1/tickets/" + extra), "$.change.lectureId");

		post(user(RAO_USER), "/api/v1/tickets", permanent(extraLecture, "2026-07-16")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.not_weekly"));
		post(user(RAO_USER), "/api/v1/tickets", "{\"type\":\"PERMANENT_CHANGE\",\"lectureId\":\"" + oop + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("target")));
		post(user(ROY_USER), "/api/v1/tickets", permanent(oop, "2026-07-09")).andExpect(status().isForbidden());
		post(user(MEHTA_USER), "/api/v1/tickets", permanent(oop, "2026-07-09")).andExpect(status().isNotFound());
	}

	// --- approval ---------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("it is never applied by its author alone, not even a teacher and not an HOD")
	void neverAppliedAlone() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, permanent(oop, "2026-07-09"));
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.needs_hod_approval"));

		UUID hod = personWith("HOD", this.deptCse);
		String byHod = raise(user(hod), permanent(oop, "2026-07-09"));
		post(user(hod), "/api/v1/tickets/" + byHod + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.needs_hod_approval"));
	}

	@Test
	@DisplayName("an HOD is among the people asked, and the HOD's approval applies every move together")
	void anHodApprovesIt() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, permanent(oop, "2026-07-09"));
		UUID hod = personWith("HOD", this.deptCse);

		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/submit", reviewers(ROY_USER, "OTHER_FACULTY"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("hod_required")));
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/submit", reviewers(ROY_USER, "OTHER_FACULTY", hod, "HOD"))
			.andExpect(status().isOk());

		// Everyone else's approval is not enough.
		post(user(ROY_USER), "/api/v1/tickets/" + ticket + "/review", "{\"decision\":\"APPROVE\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_REVIEW"));
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.reviews_pending"));

		post(user(hod), "/api/v1/tickets/" + ticket + "/review", "{\"decision\":\"APPROVE\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.approvalPath").value("REVIEWED"));

		for (String[] move : new String[][] { { "2026-07-06", "2026-07-09" }, { "2026-07-13", "2026-07-16" },
				{ "2026-07-20", "2026-07-23" }, { "2026-07-27", "2026-07-30" } }) {
			get(admin(), "/api/v1/timetable/live/" + lecture(this.theoryOop, move[0])).andExpect(jsonPath("$.date").value(move[1]))
				.andExpect(jsonPath("$.startTime").value("09:00:00"))
				.andExpect(jsonPath("$.changed").value(true));
		}
		// The Monday slots are free for others now.
		applyOk(ROY_USER, raise(ROY_USER, move(lecture(this.theoryDbms, "2026-07-13"), "2026-07-13", 9, 10, this.room101)));
	}

	@Test
	@DisplayName("a person named as the HOD who does not hold the right cannot approve, and an HOD cannot merely acknowledge")
	void onlyAnHodApproves() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, permanent(oop, "2026-07-09"));
		UUID hod = personWith("HOD", this.deptCse);
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/submit", reviewers(ROY_USER, "HOD", hod, "HOD"))
			.andExpect(status().isOk());

		post(user(ROY_USER), "/api/v1/tickets/" + ticket + "/review", "{\"decision\":\"APPROVE\"}").andExpect(status().isForbidden());
		post(user(hod), "/api/v1/tickets/" + ticket + "/review", "{\"decision\":\"ACKNOWLEDGE\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("approval_required")));
		// Rejecting is open to either, with a reason.
		post(user(ROY_USER), "/api/v1/tickets/" + ticket + "/review", "{\"decision\":\"REJECT\",\"reason\":\"Clashes\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"));
	}

	@Test
	@DisplayName("an HOD who raises a permanent change for their department approves it themselves")
	void anHodApprovesTheirOwn() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		UUID hod = personWith("HOD", this.deptCse);
		String ticket = raise(user(hod), permanent(oop, "2026-07-09"));

		post(user(hod), "/api/v1/tickets/" + ticket + "/submit", reviewers(hod, "HOD")).andExpect(status().isOk());
		post(user(hod), "/api/v1/tickets/" + ticket + "/review", "{\"decision\":\"APPROVE\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.date").value("2026-07-09"));

		// A teacher cannot do the same: asking themselves is refused.
		String raos = raise(RAO_USER, permanent(lecture(this.theoryOop, "2026-07-13"), "2026-07-17"));
		post(user(RAO_USER), "/api/v1/tickets/" + raos + "/submit", reviewers(RAO_USER, "HOD")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("reviewer_is_author")));
	}

	// --- conflicts, editing, revert ---------------------------------------------------------------------------------

	@Test
	@DisplayName("one blocked move blocks the whole change")
	void oneBlockedMoveBlocksAll() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		// Roy takes Thursday the 16th at 9, one of the places the permanent change would put a lecture.
		applyOk(ROY_USER, raise(ROY_USER, "{\"type\":\"EXTRA\",\"assignmentId\":\"" + this.theoryDbms + "\",\"target\":"
				+ target("2026-07-16", 9, 10, this.room101) + "}"));

		String ticket = raise(RAO_USER, permanent(oop, "2026-07-09"));
		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(jsonPath("$.conflicted").value(true))
			.andExpect(jsonPath("$.conflicts[*].type", hasItem("SAME_SLOT_TICKET")));
		UUID hod = personWith("HOD", this.deptCse);
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/submit", reviewers(hod, "HOD")).andExpect(status().isOk());
		post(user(hod), "/api/v1/tickets/" + ticket + "/review", "{\"decision\":\"APPROVE\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_REVIEW"))
			.andExpect(jsonPath("$.conflicted").value(true));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.date").value("2026-07-06"));
		get(admin(), "/api/v1/timetable/live/" + lecture(this.theoryOop, "2026-07-13")).andExpect(jsonPath("$.date").value("2026-07-13"));
	}

	@Test
	@DisplayName("a permanent change is withdrawn and raised again rather than edited")
	void notEdited() throws Exception {
		String ticket = raise(RAO_USER, permanent(lecture(this.theoryOop, "2026-07-06"), "2026-07-09"));
		String version = read(get(user(RAO_USER), "/api/v1/tickets/" + ticket), "$.version");

		put(user(RAO_USER), "/api/v1/tickets/" + ticket, "{\"description\":\"x\",\"version\":" + version + "}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.ticket_not_editable"));
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/withdraw", null).andExpect(status().isOk());
	}

	@Test
	@DisplayName("only an HOD of the place reverts a permanent change, and the revert undoes every lecture together")
	void onlyAnHodReverts() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, permanent(oop, "2026-07-09"));
		UUID hod = personWith("HOD", this.deptCse);
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/submit", reviewers(hod, "HOD")).andExpect(status().isOk());
		post(user(hod), "/api/v1/tickets/" + ticket + "/review", "{\"decision\":\"APPROVE\"}").andExpect(status().isOk());

		// The author and the teacher it affects cannot undo it.
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/revert", null).andExpect(status().isForbidden());

		String revert = read(post(user(hod), "/api/v1/tickets/" + ticket + "/revert", null).andExpect(status().isCreated())
			.andExpect(jsonPath("$.changes", hasSize(4))), "$.id");
		post(user(hod), "/api/v1/tickets/" + revert + "/apply", null).andExpect(status().isOk());

		for (String date : new String[] { "2026-07-06", "2026-07-13", "2026-07-20", "2026-07-27" }) {
			get(admin(), "/api/v1/timetable/live/" + lecture(this.theoryOop, date)).andExpect(jsonPath("$.date").value(date))
				.andExpect(jsonPath("$.changed").value(false));
		}
		get(user(hod), "/api/v1/tickets/" + ticket).andExpect(jsonPath("$.status").value("REVERTED"));
		assertThat(count("select count(*) from occurrence_claim where claim_date in ('2026-07-09', '2026-07-16')")).isZero();
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	/** Raises a permanent change of the lecture to Thursday 9-10 in R101, on the given date of the lecture's week. */
	private String permanent(String lecture, String date) {
		return "{\"type\":\"PERMANENT_CHANGE\",\"lectureId\":\"" + lecture + "\",\"description\":\"Thursdays from now on\","
				+ "\"target\":" + target(date, 9, 10, this.room101) + "}";
	}

	private static String reviewers(Object... userAndCapacity) {
		StringBuilder list = new StringBuilder("{\"reviewers\":[");
		for (int i = 0; i < userAndCapacity.length; i += 2) {
			list.append(i == 0 ? "" : ",")
				.append("{\"userId\":\"")
				.append(userAndCapacity[i])
				.append("\",\"capacity\":\"")
				.append(userAndCapacity[i + 1])
				.append("\"}");
		}
		return list.append("]}").toString();
	}

}
