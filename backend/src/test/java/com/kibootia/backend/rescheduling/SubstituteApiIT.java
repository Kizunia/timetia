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
 * Substitutes, through the real stack: a teacher has another faculty member take one lecture on its date. The lecture stays
 * where it is; who teaches it changes, and with it who holds the teacher's time, who sees it on their timetable and who may
 * change it afterwards.
 *
 * <p>Rao teaches OOP theory on Monday 9-10 (R101); Roy teaches DBMS theory on Monday 10-11 and is free at 9; Mehta teaches
 * Thermodynamics to another division on Monday 9-10 and is not.
 */
class SubstituteApiIT extends ReschedulingApiTest {

	// --- raising ----------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a teacher raises a substitute naming who takes the lecture; the ticket changes nothing yet")
	void aTeacherRaisesASubstitute() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");

		post(user(RAO_USER), "/api/v1/tickets", substitute(oop, this.facultyRoy)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("SUBSTITUTE"))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.conflicted").value(false))
			.andExpect(jsonPath("$.change.operation").value("REASSIGN_FACULTY"))
			.andExpect(jsonPath("$.change.substituteFacultyId").value(this.facultyRoy))
			.andExpect(jsonPath("$.change.target").doesNotExist())
			.andExpect(jsonPath("$.expiresAt").value("2026-07-06T03:30:00Z"));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.facultyId").value(this.facultyRao))
			.andExpect(jsonPath("$.changed").value(false));
	}

	@Test
	@DisplayName("the request is checked: a substitute is named, is someone else, exists, and does not move the lecture")
	void theRequestIsChecked() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");

		post(user(RAO_USER), "/api/v1/tickets", "{\"type\":\"SUBSTITUTE\",\"lectureId\":\"" + oop + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("substituteFacultyId")));
		post(user(RAO_USER), "/api/v1/tickets", substitute(oop, this.facultyRao)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("same_faculty")));
		post(user(RAO_USER), "/api/v1/tickets", substitute(oop, UUID.randomUUID().toString())).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("unknown_faculty")));
		post(user(RAO_USER), "/api/v1/tickets", "{\"type\":\"SUBSTITUTE\",\"lectureId\":\"" + oop + "\",\"substituteFacultyId\":\""
				+ this.facultyRoy + "\",\"target\":" + target("2026-07-09", 9, 10, this.room101) + "}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("not_allowed")));
		// A substitute is named by a substitute ticket only.
		post(user(RAO_USER), "/api/v1/tickets", move(oop, "2026-07-09", 9, 10, this.room101).replace("\"type\"",
				"\"substituteFacultyId\":\"" + this.facultyRoy + "\",\"type\"")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("substituteFacultyId")));
	}

	@Test
	@DisplayName("only the teacher, or the HOD for them, raises it, and an unrelated teacher is told the lecture does not exist")
	void whoMayRaiseIt() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");

		// Roy sees the lecture, as a teacher of the division, and may not change it; Mehta teaches elsewhere and cannot see it.
		post(user(ROY_USER), "/api/v1/tickets", substitute(oop, this.facultyMehta)).andExpect(status().isForbidden());
		post(user(MEHTA_USER), "/api/v1/tickets", substitute(oop, this.facultyRoy)).andExpect(status().isNotFound());
		post(user(personWith("HOD", this.deptCse)), "/api/v1/tickets", substitute(oop, this.facultyRoy)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.actingFacultyId").value(this.facultyRao));
	}

	// --- applying ---------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("applying it puts the substitute in front of the lecture, in the same slot, and the live timetable says so")
	void applyingPutsTheSubstituteInFront() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, substitute(oop, this.facultyRoy));

		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.approvalPath").value("SELF"))
			.andExpect(jsonPath("$.change.beforeFacultyId").value(this.facultyRao))
			.andExpect(jsonPath("$.change.before.date").value("2026-07-06"));

		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.facultyId").value(this.facultyRoy))
			.andExpect(jsonPath("$.facultyName").value("Dr Roy"))
			.andExpect(jsonPath("$.originalFacultyId").value(this.facultyRao))
			.andExpect(jsonPath("$.changed").value(true))
			.andExpect(jsonPath("$.date").value("2026-07-06"))
			.andExpect(jsonPath("$.startTime").value("09:00:00"))
			.andExpect(jsonPath("$.originalDate").doesNotExist());
		// The base columns stay as the timetable made them.
		assertThat(count("select count(*) from lecture_occurrence where id = unhex('" + hex(UUID.fromString(oop))
				+ "') and faculty_id = effective_faculty_id")).isZero();
	}

	@Test
	@DisplayName("the teacher's time is handed over: the substitute holds it on that date and the teacher no longer does")
	void theTeachersTimeIsHandedOver() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		applyOk(RAO_USER, raise(RAO_USER, substitute(oop, this.facultyRoy)));

		assertThat(facultyClaims(oop, this.facultyRoy)).as("Roy holds 9 on the 6th").isEqualTo(1);
		assertThat(facultyClaims(oop, this.facultyRao)).as("Rao holds nothing of it").isZero();
		// The other Mondays are Rao's still.
		String next = lecture(this.theoryOop, "2026-07-13");
		assertThat(facultyClaims(next, this.facultyRao)).isEqualTo(1);
		get(admin(), "/api/v1/timetable/live/" + next).andExpect(jsonPath("$.facultyId").value(this.facultyRao))
			.andExpect(jsonPath("$.originalFacultyId").doesNotExist());
	}

	@Test
	@DisplayName("a substitute who is teaching another class at that hour is a conflict, and nothing changes")
	void aBusySubstituteIsAConflict() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, substitute(oop, this.facultyMehta));

		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(jsonPath("$.conflicted").value(true))
			.andExpect(jsonPath("$.conflicts[*].type", hasItem("FACULTY_BUSY")));
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.blocked_by_conflicts"));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.facultyId").value(this.facultyRao));
	}

	@Test
	@DisplayName("a lecture cancelled after the ticket was raised cannot be handed over")
	void aCancelledLectureCannotBeHandedOver() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String substitute = raise(RAO_USER, substitute(oop, this.facultyRoy));
		applyOk(RAO_USER, raise(RAO_USER, cancel(oop, "Away")));

		post(user(RAO_USER), "/api/v1/tickets/" + substitute + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.blocked_by_conflicts"))
			.andExpect(jsonPath("$.errors[0].code").value("occurrence_changed"));
	}

	@Test
	@DisplayName("after the hand-over the substitute teaches the lecture: they see it, may move it, and their time follows it")
	void theSubstituteTeachesTheLecture() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		applyOk(RAO_USER, raise(RAO_USER, substitute(oop, this.facultyRoy)));

		// Rao no longer teaches it on that date, so it is not his to raise a ticket for (he still sees it, in his division).
		post(user(RAO_USER), "/api/v1/tickets", cancel(oop, "Away")).andExpect(status().isForbidden());
		// Roy does, and moving it takes his time with it.
		String move = raise(ROY_USER, move(oop, "2026-07-09", 9, 10, this.room101));
		applyOk(ROY_USER, move);

		assertThat(facultyClaims(oop, this.facultyRoy)).isEqualTo(1);
		assertThat(count("select count(*) from occurrence_claim where occurrence_id = unhex('" + hex(UUID.fromString(oop))
				+ "') and claim_kind = 'FACULTY' and claim_date = '2026-07-09'")).isEqualTo(1);
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.facultyId").value(this.facultyRoy))
			.andExpect(jsonPath("$.date").value("2026-07-09"));

		// On the live timetable it is Roy's, no longer Rao's, though Rao still sees it with the rest of the division.
		assertThat(liveIds(user(ROY_USER))).contains(oop);
		get(admin(), "/api/v1/timetable/live?from=2026-07-01&to=2026-07-31&faculty=" + this.facultyRao + "&size=100")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id == '" + oop + "')]").isEmpty());
	}

	// --- review -----------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("the substitute can be asked to agree: their approval applies the ticket")
	void theSubstituteCanBeAskedToAgree() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, substitute(oop, this.facultyRoy));

		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/submit", "{\"reviewers\":[{\"userId\":\"" + ROY_USER
				+ "\",\"capacity\":\"AFFECTED_FACULTY\"}]}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REVIEW"));
		post(user(ROY_USER), "/api/v1/tickets/" + ticket + "/review", "{\"decision\":\"APPROVE\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.approvalPath").value("REVIEWED"));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.facultyId").value(this.facultyRoy));
	}

	@Test
	@DisplayName("the substitute declining stops it, with their reason")
	void theSubstituteCanDecline() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, substitute(oop, this.facultyRoy));
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/submit", "{\"reviewers\":[{\"userId\":\"" + ROY_USER
				+ "\",\"capacity\":\"AFFECTED_FACULTY\"}]}").andExpect(status().isOk());

		post(user(ROY_USER), "/api/v1/tickets/" + ticket + "/review", "{\"decision\":\"REJECT\",\"reason\":\"Away that day\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.facultyId").value(this.facultyRao));
	}

	// --- editing ----------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a draft substitute can be pointed at someone else, and the conflicts are checked again")
	void aDraftCanBePointedAtSomeoneElse() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, substitute(oop, this.facultyMehta));
		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(jsonPath("$.conflicted").value(true));

		String version = read(get(user(RAO_USER), "/api/v1/tickets/" + ticket), "$.version");
		put(user(RAO_USER), "/api/v1/tickets/" + ticket, "{\"substituteFacultyId\":\"" + this.facultyRoy + "\",\"version\":" + version + "}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.change.substituteFacultyId").value(this.facultyRoy))
			.andExpect(jsonPath("$.conflicted").value(false));
		applyOk(RAO_USER, ticket);
	}

	// --- seeing it --------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("the substitute sees the ticket without being asked, and the list and the single-ticket decision agree for everyone")
	void theSubstituteSeesTheTicket() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, substitute(oop, this.facultyRoy));
		String unrelated = raise(MEHTA_USER, cancel(lecture(this.theoryThermo, "2026-07-13"), "Away"));

		Map<String, RequestPostProcessor> people = new LinkedHashMap<>();
		people.put("admin", admin());
		people.put("rao", user(RAO_USER));
		people.put("roy, the substitute", user(ROY_USER));
		people.put("mehta", user(MEHTA_USER));
		people.put("hod cse", user(personWith("HOD", this.deptCse)));
		people.put("hod me", user(personWith("HOD", this.deptMe)));
		people.put("cr cse-a", user(personWith("CR", this.divisionCseA)));
		people.put("student", user(personWith("Student", this.batchA1)));
		people.put("nobody", user(somebody()));

		for (Map.Entry<String, RequestPostProcessor> person : people.entrySet()) {
			Set<String> allowed = Set.of(ticket, unrelated).stream().filter((id) -> {
				try {
					return get(person.getValue(), "/api/v1/tickets/" + id).andReturn().getResponse().getStatus() == 200;
				}
				catch (Exception ex) {
					throw new IllegalStateException(ex);
				}
			}).collect(Collectors.toSet());
			assertThat(listedTickets(person.getValue(), "")).as("the list of " + person.getKey()).isEqualTo(allowed);
		}
		assertThat(listedTickets(user(ROY_USER), "")).containsExactly(ticket);
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private static String substitute(String lecture, String faculty) {
		return "{\"type\":\"SUBSTITUTE\",\"lectureId\":\"" + lecture + "\",\"description\":\"Cover for me\","
				+ "\"substituteFacultyId\":\"" + faculty + "\"}";
	}

	/** How many hours of the lecture the faculty member holds: its length for the teacher, none for anyone else. */
	private int facultyClaims(String lecture, String faculty) {
		return count("select count(*) from occurrence_claim where occurrence_id = unhex('" + hex(UUID.fromString(lecture))
				+ "') and claim_kind = 'FACULTY' and resource_id = unhex('" + hex(UUID.fromString(faculty)) + "')");
	}

	private Set<String> liveIds(RequestPostProcessor who) throws Exception {
		String json = get(who, "/api/v1/timetable/live?from=2026-07-01&to=2026-07-31&size=100").andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		java.util.List<String> ids = com.jayway.jsonpath.JsonPath.read(json, "$.items[*].id");
		return new java.util.HashSet<>(ids);
	}

}
