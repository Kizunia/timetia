package com.kibootia.backend.rescheduling;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Extra lectures, through the real stack: a teacher adds one lecture of a teaching assignment on a date. There is no lecture
 * to change, so the ticket names the teaching assignment, and the lecture comes into being when the ticket is applied, holding
 * the teacher, the room and the students for its hours like any other.
 *
 * <p>The week is as in {@link ReschedulingApiTest}: CSE-A has OOP theory (Rao) on Monday 9-10 and DBMS theory (Roy) on Monday
 * 10-11, both in R101; Thursday is free.
 */
class ExtraLectureApiIT extends ReschedulingApiTest {

	// --- raising ----------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a teacher raises an extra lecture of their assignment; nothing exists until it is applied")
	void aTeacherRaisesAnExtraLecture() throws Exception {
		post(user(RAO_USER), "/api/v1/tickets", extra(this.theoryOop, "2026-07-09", 9, 10, this.room101))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("EXTRA"))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.conflicted").value(false))
			.andExpect(jsonPath("$.change.operation").value("CREATE_EXTRA"))
			.andExpect(jsonPath("$.change.assignmentId").value(this.theoryOop))
			.andExpect(jsonPath("$.change.lectureId").doesNotExist())
			.andExpect(jsonPath("$.change.target.date").value("2026-07-09"))
			.andExpect(jsonPath("$.facultyId").value(this.facultyRao))
			.andExpect(jsonPath("$.sessionType").value("THEORY"))
			.andExpect(jsonPath("$.expiresAt").value("2026-07-09T03:30:00Z"));
		assertThat(lecturesOnThursday()).isZero();
	}

	@Test
	@DisplayName("the request is checked: the assignment is named, no lecture is, and there is a slot that is real")
	void theRequestIsChecked() throws Exception {
		String body = extra(this.theoryOop, "2026-07-09", 9, 10, this.room101);

		post(user(RAO_USER), "/api/v1/tickets", body.replace("\"assignmentId\":\"" + this.theoryOop + "\",", ""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("assignmentId")));
		post(user(RAO_USER), "/api/v1/tickets", body.replace("\"type\"", "\"lectureId\":\"" + UUID.randomUUID() + "\",\"type\""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("lectureId")));
		post(user(RAO_USER), "/api/v1/tickets", "{\"type\":\"EXTRA\",\"assignmentId\":\"" + this.theoryOop + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("target")));
		post(user(RAO_USER), "/api/v1/tickets", extra(this.theoryOop, "2026-07-09", 9, 10, null).replace(
				"\"mode\":\"IN_PERSON\",\"roomId\":\"null\"", "\"mode\":\"IN_PERSON\""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("room_required")));
		// A time that has passed.
		this.clock.set(AFTER_THE_FIRST_LECTURE_STARTS);
		post(user(RAO_USER), "/api/v1/tickets", extra(this.theoryOop, "2026-07-06", 9, 10, this.room101))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("in_the_past")));
		// The other types name a lecture, and no assignment.
		this.clock.set(BEFORE_THE_TERM);
		post(user(RAO_USER), "/api/v1/tickets", "{\"type\":\"RESCHEDULE\",\"target\":" + target("2026-07-09", 9, 10, this.room101) + "}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("lectureId")));
		post(user(RAO_USER), "/api/v1/tickets", cancel(lecture(this.theoryOop, "2026-07-06"), "Away").replace("\"type\"",
				"\"assignmentId\":\"" + this.theoryOop + "\",\"type\""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("assignmentId")));
	}

	@Test
	@DisplayName("only the teacher, or the HOD of the department for them, adds an extra lecture; others find no such assignment")
	void whoMayRaiseIt() throws Exception {
		String body = extra(this.theoryOop, "2026-07-09", 9, 10, this.room101);

		post(user(ROY_USER), "/api/v1/tickets", body).andExpect(status().isNotFound());
		post(user(personWith("HOD", this.deptMe)), "/api/v1/tickets", body).andExpect(status().isNotFound());
		post(user(somebody()), "/api/v1/tickets", body).andExpect(status().isNotFound());
		post(user(RAO_USER), "/api/v1/tickets", extra(UUID.randomUUID().toString(), "2026-07-09", 9, 10, this.room101))
			.andExpect(status().isNotFound());
		post(user(personWith("HOD", this.deptCse)), "/api/v1/tickets", body).andExpect(status().isCreated())
			.andExpect(jsonPath("$.actingFacultyId").value(this.facultyRao));
	}

	// --- applying ---------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("applying it creates the lecture, which holds the teacher, the room and the students for its hour")
	void applyingCreatesTheLecture() throws Exception {
		String ticket = raise(RAO_USER, extra(this.theoryOop, "2026-07-09", 9, 10, this.room101));

		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.approvalPath").value("SELF"))
			.andExpect(jsonPath("$.change.lectureId").isNotEmpty());

		assertThat(lecturesOnThursday()).isEqualTo(1);
		get(admin(), "/api/v1/timetable/live?from=2026-07-09&to=2026-07-09&division=" + this.divisionCseA + "&size=100")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].extra").value(true))
			.andExpect(jsonPath("$.items[0].changed").value(true))
			.andExpect(jsonPath("$.items[0].facultyId").value(this.facultyRao))
			.andExpect(jsonPath("$.items[0].startTime").value("09:00:00"))
			.andExpect(jsonPath("$.items[0].sessionType").value("THEORY"))
			.andExpect(jsonPath("$.items[0].originalDate").doesNotExist());
		String created = read(get(user(RAO_USER), "/api/v1/tickets/" + ticket), "$.change.lectureId");
		assertThat(count("select count(*) from lecture_occurrence where id = unhex('" + hex(UUID.fromString(created))
				+ "') and timetable_entry_id is null and changed_by_ticket_id = unhex('" + hex(UUID.fromString(ticket)) + "')"))
			.isEqualTo(1);
		// The teacher, the room, the division and each of its two batches, for the one hour.
		assertThat(count("select count(*) from occurrence_claim where occurrence_id = unhex('" + hex(UUID.fromString(created))
				+ "')")).isEqualTo(5);
	}

	@Test
	@DisplayName("a lab is added for its batch, held in a lab room for both hours")
	void aLabIsAdded() throws Exception {
		String ticket = raise(RAO_USER, extra(this.labA1, "2026-07-09", 14, 16, this.lab1));
		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(jsonPath("$.sessionType").value("LAB"))
			.andExpect(jsonPath("$.batchId").value(this.batchA1));
		applyOk(RAO_USER, ticket);

		String created = read(get(user(RAO_USER), "/api/v1/tickets/" + ticket), "$.change.lectureId");
		// Two hours of the teacher, the room and the batch.
		assertThat(count("select count(*) from occurrence_claim where occurrence_id = unhex('" + hex(UUID.fromString(created))
				+ "')")).isEqualTo(6);
	}

	@Test
	@DisplayName("a slot that is taken is a conflict, reported without a lecture of its own, and nothing is created")
	void aTakenSlotIsAConflict() throws Exception {
		// Monday the 13th, 10-11 in R101: the DBMS lecture of the same division is there.
		String ticket = raise(RAO_USER, extra(this.theoryOop, "2026-07-13", 10, 11, this.room101));

		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(jsonPath("$.conflicted").value(true))
			.andExpect(jsonPath("$.conflicts[*].type", hasItem("ROOM_OCCUPIED")))
			.andExpect(jsonPath("$.conflicts[*].type", hasItem("DIVISION_BUSY")))
			.andExpect(jsonPath("$.conflicts[0].lectureId").doesNotExist());
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.blocked_by_conflicts"));
		assertThat(count("select count(*) from lecture_occurrence where timetable_entry_id is null")).isZero();
	}

	@Test
	@DisplayName("the calendar applies: not a Sunday, not outside the term, not off the hour")
	void theCalendarApplies() throws Exception {
		for (String[] bad : new String[][] { { "2026-07-12", "9", "10" }, { "2026-08-03", "9", "10" } }) {
			String ticket = raise(RAO_USER, extra(this.theoryOop, bad[0], Integer.parseInt(bad[1]), Integer.parseInt(bad[2]), this.room101));
			get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(jsonPath("$.conflicts[*].type", hasItem("OUTSIDE_CALENDAR")));
		}
		String late = raise(RAO_USER, extra(this.theoryOop, "2026-07-09", 19, 21, this.room101));
		get(user(RAO_USER), "/api/v1/tickets/" + late).andExpect(jsonPath("$.conflicts[*].type", hasItem("OUTSIDE_SLOT_GRID")));
	}

	@Test
	@DisplayName("two extra lectures racing for the same slot: the first applied wins and the other is blocked")
	void firstAppliedWins() throws Exception {
		String raos = raise(RAO_USER, extra(this.theoryOop, "2026-07-09", 9, 10, this.room101));
		String roys = raise(ROY_USER, extra(this.theoryDbms, "2026-07-09", 9, 10, this.room101));

		applyOk(RAO_USER, raos);
		post(user(ROY_USER), "/api/v1/tickets/" + roys + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.errors[*].code", hasItem("same_slot_ticket")));
		assertThat(lecturesOnThursday()).isEqualTo(1);
	}

	// --- afterwards -------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("an extra lecture is a lecture: it can be moved and cancelled, and cancelling frees what it held")
	void anExtraLectureCanBeChanged() throws Exception {
		String ticket = raise(RAO_USER, extra(this.theoryOop, "2026-07-09", 9, 10, this.room101));
		applyOk(RAO_USER, ticket);
		String created = read(get(user(RAO_USER), "/api/v1/tickets/" + ticket), "$.change.lectureId");

		applyOk(RAO_USER, raise(RAO_USER, move(created, "2026-07-10", 9, 10, this.room101)));
		get(admin(), "/api/v1/timetable/live/" + created).andExpect(jsonPath("$.date").value("2026-07-10"))
			.andExpect(jsonPath("$.extra").value(true))
			.andExpect(jsonPath("$.originalDate").value("2026-07-09"));
		applyOk(RAO_USER, raise(RAO_USER, cancel(created, "Not needed after all")));
		get(admin(), "/api/v1/timetable/live/" + created).andExpect(jsonPath("$.cancelled").value(true));
		assertThat(count("select count(*) from occurrence_claim where occurrence_id = unhex('" + hex(UUID.fromString(created))
				+ "')")).isZero();

		// What it held is free for another to take.
		applyOk(ROY_USER, raise(ROY_USER, extra(this.theoryDbms, "2026-07-10", 9, 10, this.room101)));
		// The tickets that concern the lecture include the one that made it.
		assertThat(listedTickets(user(RAO_USER), "&lecture=" + created)).contains(ticket).hasSize(3);
	}

	@Test
	@DisplayName("a published timetable cannot replace the week while an extra lecture is in it")
	void aNewVersionCannotDiscardAnExtraLecture() throws Exception {
		applyOk(RAO_USER, raise(RAO_USER, extra(this.theoryOop, "2026-07-09", 9, 10, this.room101)));

		String next = draft(this.divisionCseA, "2026-07-08");
		entry(next, this.theoryOop, 1, 9, 10, this.room101);
		post(admin(), "/api/v1/timetable/versions/" + next + "/publish", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("timetable.changes_would_be_lost"));
	}

	// --- editing, review, withdrawing -------------------------------------------------------------------------------

	@Test
	@DisplayName("a draft extra lecture can be pointed at another slot, and is checked again")
	void aDraftCanBeEdited() throws Exception {
		String ticket = raise(RAO_USER, extra(this.theoryOop, "2026-07-13", 10, 11, this.room101));
		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(jsonPath("$.conflicted").value(true));

		String version = read(get(user(RAO_USER), "/api/v1/tickets/" + ticket), "$.version");
		put(user(RAO_USER), "/api/v1/tickets/" + ticket,
				"{\"target\":" + target("2026-07-09", 9, 10, this.room101) + ",\"version\":" + version + "}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.change.target.date").value("2026-07-09"))
			.andExpect(jsonPath("$.conflicted").value(false))
			.andExpect(jsonPath("$.expiresAt").value("2026-07-09T03:30:00Z"));
		applyOk(RAO_USER, ticket);
	}

	@Test
	@DisplayName("it can be sent for review, and the last approval creates the lecture")
	void reviewCreatesTheLecture() throws Exception {
		String ticket = raise(RAO_USER, extra(this.theoryOop, "2026-07-09", 9, 10, this.room101));
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/submit",
				"{\"reviewers\":[{\"userId\":\"" + ROY_USER + "\",\"capacity\":\"OTHER_FACULTY\"}]}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_REVIEW"));
		assertThat(lecturesOnThursday()).isZero();

		post(user(ROY_USER), "/api/v1/tickets/" + ticket + "/review", "{\"decision\":\"APPROVE\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.approvalPath").value("REVIEWED"));
		assertThat(lecturesOnThursday()).isEqualTo(1);
	}

	@Test
	@DisplayName("a ticket under review that wants a slot is flagged when another change takes it")
	void aTicketUnderReviewIsFlagged() throws Exception {
		String ticket = raise(RAO_USER, extra(this.theoryOop, "2026-07-09", 9, 10, this.room101));
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/submit",
				"{\"reviewers\":[{\"userId\":\"" + MEHTA_USER + "\",\"capacity\":\"OTHER_FACULTY\"}]}").andExpect(status().isOk());

		applyOk(ROY_USER, raise(ROY_USER, move(lecture(this.theoryDbms, "2026-07-06"), "2026-07-09", 9, 10, this.room101)));

		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(jsonPath("$.conflicted").value(true))
			.andExpect(jsonPath("$.conflicts[*].type", hasItem("SAME_SLOT_TICKET")));
	}

	@Test
	@DisplayName("an extra lecture not applied before it would have started expires")
	void itExpires() throws Exception {
		String ticket = raise(RAO_USER, extra(this.theoryOop, "2026-07-09", 9, 10, this.room101));

		this.clock.set(java.time.Instant.parse("2026-07-09T04:00:00Z"));
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.ticket_expired"));
		assertThat(lecturesOnThursday()).isZero();
	}

	// --- seeing it --------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("who sees an extra lecture ticket follows the lecture it would be: the list and the single decision agree")
	void visibilityFollowsTheAssignment() throws Exception {
		String theory = raise(RAO_USER, extra(this.theoryOop, "2026-07-09", 9, 10, this.room101));
		String lab = raise(RAO_USER, extra(this.labA1, "2026-07-09", 14, 16, this.lab1));

		Map<String, RequestPostProcessor> people = new LinkedHashMap<>();
		people.put("admin", admin());
		people.put("rao", user(RAO_USER));
		people.put("roy", user(ROY_USER));
		people.put("mehta", user(MEHTA_USER));
		people.put("hod cse", user(personWith("HOD", this.deptCse)));
		people.put("hod me", user(personWith("HOD", this.deptMe)));
		people.put("cr cse-a", user(personWith("CR", this.divisionCseA)));
		people.put("br a1", user(personWith("BR", this.batchA1)));
		people.put("br a2", user(personWith("BR", this.batchA2)));
		people.put("student a1", user(personWith("Student", this.batchA1)));
		people.put("nobody", user(somebody()));

		for (Map.Entry<String, RequestPostProcessor> person : people.entrySet()) {
			Set<String> allowed = Set.of(theory, lab).stream().filter((id) -> {
				try {
					return get(person.getValue(), "/api/v1/tickets/" + id).andReturn().getResponse().getStatus() == 200;
				}
				catch (Exception ex) {
					throw new IllegalStateException(ex);
				}
			}).collect(Collectors.toSet());
			assertThat(listedTickets(person.getValue(), "")).as("the list of " + person.getKey()).isEqualTo(allowed);
		}
		assertThat(listedTickets(user(personWith("CR", this.divisionCseA)), "")).containsExactly(theory);
		assertThat(listedTickets(user(personWith("BR", this.batchA1)), "")).containsExactly(lab);
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private static String extra(String assignment, String date, int from, int to, String room) {
		return "{\"type\":\"EXTRA\",\"assignmentId\":\"" + assignment + "\",\"description\":\"Extra class\",\"target\":"
				+ target(date, from, to, room) + "}";
	}

	/** How many live lectures there are on Thursday the 9th, which the weekly timetable leaves empty. */
	private int lecturesOnThursday() {
		return count("select count(*) from lecture_occurrence where effective_date = '2026-07-09' and is_cancelled = false");
	}

}
