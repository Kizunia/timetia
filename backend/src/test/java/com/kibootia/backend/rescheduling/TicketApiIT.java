package com.kibootia.backend.rescheduling;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tickets that reschedule or cancel one lecture, through the real stack: raising and checking them, who may do what, applying
 * them by their author, and what happens when something is in the way. The rules under test are the lecture-rescheduling
 * module's: self-approval for a teacher, the HOD acting for an absent teacher, every conflict blocking, a lecture that has
 * started being fixed, and "the first change applied wins".
 */
class TicketApiIT extends ReschedulingApiTest {

	// --- raising a ticket -------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a teacher raises a draft for a lecture they teach, and it is checked at once")
	void aTeacherRaisesADraft() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");

		post(user(RAO_USER), "/api/v1/tickets", move(oop, "2026-07-09", 9, 10, this.room101))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", containsString("/api/v1/tickets/")))
			.andExpect(jsonPath("$.type").value("RESCHEDULE"))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.authorUserId").value(RAO_USER.toString()))
			.andExpect(jsonPath("$.actingFacultyId").doesNotExist())
			.andExpect(jsonPath("$.conflicted").value(false))
			.andExpect(jsonPath("$.conflicts.length()").value(0))
			.andExpect(jsonPath("$.change.operation").value("MOVE"))
			.andExpect(jsonPath("$.change.lectureId").value(oop))
			.andExpect(jsonPath("$.change.target.date").value("2026-07-09"))
			.andExpect(jsonPath("$.change.before").doesNotExist())
			// It lapses when the earliest lecture it involves starts: the lecture itself, 09:00 on the campus clock.
			.andExpect(jsonPath("$.expiresAt").value("2026-07-06T03:30:00Z"));
	}

	@Test
	@DisplayName("a ticket whose plan is in the way is still raised, flagged, with each thing in the way named")
	void aTicketWithConflictsIsRaisedAndFlagged() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");

		post(user(RAO_USER), "/api/v1/tickets", move(oop, "2026-07-06", 10, 11, this.room101))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.conflicted").value(true))
			.andExpect(jsonPath("$.conflicts[*].type", hasItem("ROOM_OCCUPIED")))
			.andExpect(jsonPath("$.conflicts[*].type", hasItem("DIVISION_BUSY")))
			.andExpect(jsonPath("$.conflicts[0].message").isNotEmpty());
	}

	@Test
	@DisplayName("a cancellation needs a reason and names no new slot")
	void aCancellationNeedsAReason() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");

		post(user(RAO_USER), "/api/v1/tickets", "{\"type\":\"CANCEL\",\"lectureId\":\"" + oop + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("reason")));
		post(user(RAO_USER), "/api/v1/tickets", "{\"type\":\"CANCEL\",\"lectureId\":\"" + oop + "\",\"reason\":\"  \"}")
			.andExpect(status().isBadRequest());
		post(user(RAO_USER), "/api/v1/tickets", "{\"type\":\"CANCEL\",\"lectureId\":\"" + oop + "\",\"reason\":\"Ill\",\"target\":"
				+ target("2026-07-09", 9, 10, this.room101) + "}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("not_allowed")));
		post(user(RAO_USER), "/api/v1/tickets", cancel(oop, "Unwell")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("CANCEL"))
			.andExpect(jsonPath("$.change.operation").value("CANCEL"))
			.andExpect(jsonPath("$.change.reason").value("Unwell"))
			.andExpect(jsonPath("$.change.target").doesNotExist());
	}

	@Test
	@DisplayName("what a reschedule asks for is checked, and each bad field is reported")
	void aRescheduleIsChecked() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String path = "/api/v1/tickets";

		post(user(RAO_USER), path, "{\"type\":\"RESCHEDULE\",\"lectureId\":\"" + oop + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("target")));
		// A move does not change how long a lecture lasts.
		post(user(RAO_USER), path, move(oop, "2026-07-09", 9, 11, this.room101)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("same_length")));
		// In person needs a room.
		post(user(RAO_USER), path, "{\"type\":\"RESCHEDULE\",\"lectureId\":\"" + oop + "\",\"target\":{\"date\":\"2026-07-09\","
				+ "\"startTime\":\"09:00\",\"endTime\":\"10:00\",\"mode\":\"IN_PERSON\"}}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("room_required")));
		// Not into the past, nor to where it already is.
		post(user(RAO_USER), path, move(oop, "2026-06-30", 9, 10, this.room101)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("in_the_past")));
		post(user(RAO_USER), path, move(oop, "2026-07-06", 9, 10, this.room101)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("no_change")));
		// An online lecture has no room and may have a link.
		post(user(RAO_USER), path, "{\"type\":\"RESCHEDULE\",\"lectureId\":\"" + oop + "\",\"target\":{\"date\":\"2026-07-09\","
				+ "\"startTime\":\"09:00\",\"endTime\":\"10:00\",\"mode\":\"ONLINE\",\"roomId\":\"" + this.room101 + "\"}}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("room_not_allowed")));
		post(user(RAO_USER), path, "{\"type\":\"RESCHEDULE\",\"lectureId\":\"" + oop + "\",\"target\":{\"date\":\"2026-07-09\","
				+ "\"startTime\":\"09:00\",\"endTime\":\"10:00\",\"mode\":\"ONLINE\",\"meetingLink\":\"https://meet.example/x\"}}")
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("a ticket is raised by the teacher of the lecture or the HOD of its department; anyone else is refused or not told it exists")
	void whoMayRaiseATicket() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String body = move(oop, "2026-07-09", 9, 10, this.room101);
		String path = "/api/v1/tickets";

		// Roy cannot even see Rao's lecture, so it does not exist for him; neither does it for the HOD of another department.
		post(user(ROY_USER), path, body).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("rescheduling.lecture_not_found"));
		post(user(personWith("HOD", this.deptMe)), path, body).andExpect(status().isNotFound());
		post(user(somebody()), path, body).andExpect(status().isNotFound());
		post(user(RAO_USER), path, move(UUID.randomUUID().toString(), "2026-07-09", 9, 10, this.room101))
			.andExpect(status().isNotFound());
		// Others can see the lecture, and may not change it.
		post(user(personWith("CR", this.divisionCseA)), path, body).andExpect(status().isForbidden());
		post(user(personWith("Student", this.batchA1)), path, body).andExpect(status().isForbidden());
		post(admin(), path, body).andExpect(status().isForbidden());
		// The HOD of the department raises it for the teacher.
		post(user(personWith("HOD", this.deptCse)), path, body).andExpect(status().isCreated())
			.andExpect(jsonPath("$.actingFacultyId").value(this.facultyRao));
	}

	@Test
	@DisplayName("a lecture that has started, or is cancelled, can no longer be changed")
	void aLectureThatCannotBeChanged() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String later = lecture(this.theoryOop, "2026-07-13");
		raiseAndApplyCancel(later);

		post(user(RAO_USER), "/api/v1/tickets", cancel(later, "Again")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.lecture_cancelled"));

		this.clock.set(AFTER_THE_FIRST_LECTURE_STARTS);
		post(user(RAO_USER), "/api/v1/tickets", move(oop, "2026-07-09", 9, 10, this.room101))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.lecture_started"));
	}

	// --- applying a ticket ------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a teacher applies their own reschedule: it is laid over the timetable, and the ticket records where the lecture was")
	void aTeacherAppliesAReschedule() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, move(oop, "2026-07-09", 9, 10, this.room101));

		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.approvalPath").value("SELF"))
			.andExpect(jsonPath("$.closedAt").isNotEmpty())
			.andExpect(jsonPath("$.conflicted").value(false))
			.andExpect(jsonPath("$.change.before.date").value("2026-07-06"))
			.andExpect(jsonPath("$.change.before.startTime").value("09:00:00"))
			.andExpect(jsonPath("$.change.target.date").value("2026-07-09"));

		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(status().isOk())
			.andExpect(jsonPath("$.date").value("2026-07-09"))
			.andExpect(jsonPath("$.changed").value(true))
			.andExpect(jsonPath("$.originalDate").value("2026-07-06"));
		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(jsonPath("$.status").value("APPLIED"));
		assertThat(count("select count(*) from audit_entry where action = 'rescheduling.ticket.created'")).isEqualTo(1);
		assertThat(count("select count(*) from audit_entry where action = 'rescheduling.ticket.applied'")).isEqualTo(1);
		// Applied is the end of the ticket's own life.
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.ticket_not_draft"));
	}

	@Test
	@DisplayName("an HOD may raise a reschedule for a teacher but not apply it, since self-approval is the teacher's")
	void anHodCannotSelfApproveAReschedule() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		UUID hod = personWith("HOD", this.deptCse);
		String ticket = raise(hod, move(oop, "2026-07-09", 9, 10, this.room101));

		post(user(hod), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isForbidden());
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.changed").value(false));
	}

	@Test
	@DisplayName("a cancellation is applied directly, by the teacher or by the HOD acting for them")
	void aCancellationIsAppliedDirectly() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, cancel(oop, "Unwell"));
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.approvalPath").value("SELF"));
		get(user(personWith("Student", this.batchA1)), "/api/v1/timetable/live/" + oop).andExpect(status().isOk())
			.andExpect(jsonPath("$.cancelled").value(true));

		String dbms = lecture(this.theoryDbms, "2026-07-13");
		UUID hod = personWith("HOD", this.deptCse);
		String forRoy = raise(hod, cancel(dbms, "Roy is on leave"));
		post(user(hod), "/api/v1/tickets/" + forRoy + "/apply", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.actingFacultyId").value(this.facultyRoy));
		get(admin(), "/api/v1/timetable/live/" + dbms).andExpect(jsonPath("$.cancelled").value(true));
	}

	@Test
	@DisplayName("only the author applies, edits or withdraws a ticket, and someone who cannot see it is told it does not exist")
	void onlyTheAuthorActs() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, move(oop, "2026-07-09", 9, 10, this.room101));
		UUID hod = personWith("HOD", this.deptCse);
		String path = "/api/v1/tickets/" + ticket;

		// The HOD of the department can see it, and is not its author.
		get(user(hod), path).andExpect(status().isOk());
		post(user(hod), path + "/apply", null).andExpect(status().isForbidden());
		post(user(hod), path + "/withdraw", null).andExpect(status().isForbidden());
		put(user(hod), path, "{\"target\":" + target("2026-07-10", 9, 10, this.room101) + ",\"version\":0}")
			.andExpect(status().isForbidden());
		// Roy is not on it and cannot see it.
		get(user(ROY_USER), path).andExpect(status().isNotFound());
		post(user(ROY_USER), path + "/apply", null).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("when something is in the way at the moment of applying, nothing is changed, the conflicts are recorded and the ticket is flagged")
	void aConflictWhenApplyingBlocksTheTicket() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String dbms = lecture(this.theoryDbms, "2026-07-06");
		// Both are clear when raised, for the same free slot: Thursday 9-10 in R101.
		String raos = raise(RAO_USER, move(oop, "2026-07-09", 9, 10, this.room101));
		String roys = raise(ROY_USER, move(dbms, "2026-07-09", 9, 10, this.room101));
		get(user(RAO_USER), "/api/v1/tickets/" + raos).andExpect(jsonPath("$.conflicted").value(false));

		// Roy's is applied first, so it wins.
		post(user(ROY_USER), "/api/v1/tickets/" + roys + "/apply", null).andExpect(status().isOk());

		post(user(RAO_USER), "/api/v1/tickets/" + raos + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.blocked_by_conflicts"))
			.andExpect(jsonPath("$.errors[*].code", hasItem("same_slot_ticket")));
		get(user(RAO_USER), "/api/v1/tickets/" + raos).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.conflicted").value(true))
			.andExpect(jsonPath("$.conflicts[*].type", hasItem("SAME_SLOT_TICKET")))
			.andExpect(jsonPath("$.conflicts[?(@.type == 'SAME_SLOT_TICKET')].conflictingTicketId").value(hasItem(roys)));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.changed").value(false))
			.andExpect(jsonPath("$.date").value("2026-07-06"));

		// The author moves the plan to a free slot, which clears the flag, and applies it.
		String version = read(get(user(RAO_USER), "/api/v1/tickets/" + raos), "$.version");
		put(user(RAO_USER), "/api/v1/tickets/" + raos, "{\"target\":" + target("2026-07-10", 9, 10, this.room101)
				+ ",\"version\":" + version + "}").andExpect(status().isOk())
			.andExpect(jsonPath("$.conflicted").value(false))
			.andExpect(jsonPath("$.conflicts.length()").value(0));
		post(user(RAO_USER), "/api/v1/tickets/" + raos + "/apply", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.date").value("2026-07-10"));
	}

	@Test
	@DisplayName("a ticket planned against a lecture that another ticket has since changed is refused")
	void aTicketAgainstAnOldStateOfTheLectureIsRefused() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String move = raise(RAO_USER, move(oop, "2026-07-09", 9, 10, this.room101));
		String cancel = raise(RAO_USER, cancel(oop, "Unwell"));
		post(user(RAO_USER), "/api/v1/tickets/" + cancel + "/apply", null).andExpect(status().isOk());

		post(user(RAO_USER), "/api/v1/tickets/" + move + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.errors[*].code", hasItem("occurrence_changed")));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.date").value("2026-07-06"))
			.andExpect(jsonPath("$.cancelled").value(true));
	}

	@Test
	@DisplayName("a ticket not applied before its lecture starts expires and can no longer be applied")
	void aTicketExpiresWhenTheLectureStarts() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, move(oop, "2026-07-09", 9, 10, this.room101));

		this.clock.set(AFTER_THE_FIRST_LECTURE_STARTS);
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.ticket_expired"));
		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(jsonPath("$.status").value("EXPIRED"))
			.andExpect(jsonPath("$.closedAt").isNotEmpty());
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.changed").value(false));
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.ticket_not_draft"));
	}

	// --- withdrawing and editing ------------------------------------------------------------------------------------

	@Test
	@DisplayName("an author withdraws a draft, which cannot then be applied or withdrawn again")
	void anAuthorWithdrawsADraft() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, move(oop, "2026-07-09", 9, 10, this.room101));

		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/withdraw", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("WITHDRAWN"))
			.andExpect(jsonPath("$.closedAt").isNotEmpty());
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/withdraw", null).andExpect(status().isConflict());
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.ticket_not_draft"));
	}

	@Test
	@DisplayName("an edit replaces the plan of a draft, is refused for a ticket read out of date, and is not possible once applied")
	void anAuthorEditsADraft() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, move(oop, "2026-07-09", 9, 10, this.room101));
		String path = "/api/v1/tickets/" + ticket;
		String edit = "{\"description\":\"Better day\",\"target\":" + target("2026-07-10", 9, 10, this.room102) + ",\"version\":";

		put(user(RAO_USER), path, edit + "99}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.stale_ticket"));
		String version = read(get(user(RAO_USER), path), "$.version");
		put(user(RAO_USER), path, edit + version + "}").andExpect(status().isOk())
			.andExpect(jsonPath("$.description").value("Better day"))
			.andExpect(jsonPath("$.change.target.date").value("2026-07-10"))
			.andExpect(jsonPath("$.change.target.roomId").value(this.room102));

		post(user(RAO_USER), path + "/apply", null).andExpect(status().isOk());
		String applied = read(get(user(RAO_USER), path), "$.version");
		put(user(RAO_USER), path, edit + applied + "}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.ticket_not_draft"));
	}

	private void raiseAndApplyCancel(String lecture) throws Exception {
		applyOk(RAO_USER, raise(RAO_USER, cancel(lecture, "Unwell")));
	}

}
