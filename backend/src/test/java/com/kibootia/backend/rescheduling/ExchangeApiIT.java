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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exchanges, through the real stack: two lectures of one division take each other's place, and both moves are made or neither.
 *
 * <p>Rao teaches OOP theory on Monday 9-10, Roy DBMS theory on Monday 10-11, both in R101, and DBMS online on Wednesday
 * 14-15. Rao's lab for batch A1 and Roy's lab for batch A2 are on Tuesday 9-11. The division is CSE-A.
 */
class ExchangeApiIT extends ReschedulingApiTest {

	// --- raising ----------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a teacher raises an exchange with another teacher's lecture; each lecture goes where the other is")
	void aTeacherRaisesAnExchange() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String dbms = lecture(this.theoryDbms, "2026-07-06");

		post(user(RAO_USER), "/api/v1/tickets", exchange(oop, dbms)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("EXCHANGE"))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.conflicted").value(false))
			.andExpect(jsonPath("$.changes", hasSize(2)))
			.andExpect(jsonPath("$.change.lectureId").value(oop))
			.andExpect(jsonPath("$.changes[0].operation").value("MOVE"))
			.andExpect(jsonPath("$.changes[0].lectureId").value(oop))
			.andExpect(jsonPath("$.changes[0].target.startTime").value("10:00:00"))
			.andExpect(jsonPath("$.changes[1].lectureId").value(dbms))
			.andExpect(jsonPath("$.changes[1].target.startTime").value("09:00:00"))
			.andExpect(jsonPath("$.facultyId").value(this.facultyRao))
			.andExpect(jsonPath("$.expiresAt").value("2026-07-06T03:30:00Z"));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.startTime").value("09:00:00"));
	}

	@Test
	@DisplayName("the two lectures are checked: they differ, are of one kind and batch, and have different teachers")
	void theLecturesAreChecked() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String dbms = lecture(this.theoryDbms, "2026-07-06");

		post(user(RAO_USER), "/api/v1/tickets", "{\"type\":\"EXCHANGE\",\"lectureId\":\"" + oop + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("otherLectureId")));
		post(user(RAO_USER), "/api/v1/tickets", exchange(oop, oop)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("same_lecture")));
		post(user(RAO_USER), "/api/v1/tickets", exchange(oop, lecture(this.theoryOop, "2026-07-13")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("same_faculty")));
		post(user(RAO_USER), "/api/v1/tickets", exchange(oop, lecture(this.labA2, "2026-07-07")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("different_session")));
		post(user(RAO_USER), "/api/v1/tickets", exchange(lecture(this.labA1, "2026-07-07"), lecture(this.labA2, "2026-07-07")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("different_batch")));
		post(user(RAO_USER), "/api/v1/tickets", exchange(oop, dbms).replace("\"type\"",
				"\"target\":" + target("2026-07-09", 9, 10, this.room101) + ",\"type\"")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("target")));
		// Other types do not name a second lecture.
		post(user(RAO_USER), "/api/v1/tickets", cancel(oop, "Away").replace("\"type\"", "\"otherLectureId\":\"" + dbms + "\",\"type\""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("otherLectureId")));
	}

	@Test
	@DisplayName("a teacher can name the other teacher's lecture because they see the whole timetable of the division they teach in")
	void theAuthorSeesTheOtherLecture() throws Exception {
		String dbms = lecture(this.theoryDbms, "2026-07-06");

		get(user(RAO_USER), "/api/v1/timetable/live/" + dbms).andExpect(status().isOk());
		get(user(MEHTA_USER), "/api/v1/timetable/live/" + dbms).andExpect(status().isNotFound());
		// A teacher who teaches in no division sees no lecture of anyone else.
		UUID teacherOfNothing = somebody();
		assign(teacherOfNothing, "Faculty", null);
		get(user(teacherOfNothing), "/api/v1/timetable/live/" + dbms).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("the other lecture must be in the same division, and the author must teach their own")
	void whoMayRaiseIt() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String dbms = lecture(this.theoryDbms, "2026-07-06");

		// A lecture of another division, or one that does not exist, is not there to be exchanged with.
		post(user(RAO_USER), "/api/v1/tickets", exchange(oop, lecture(this.theoryThermo, "2026-07-06"))).andExpect(status().isNotFound());
		post(user(RAO_USER), "/api/v1/tickets", exchange(oop, UUID.randomUUID().toString())).andExpect(status().isNotFound());
		// Roy does not teach Rao's lecture.
		post(user(MEHTA_USER), "/api/v1/tickets", exchange(oop, dbms)).andExpect(status().isNotFound());
		post(user(personWith("HOD", this.deptCse)), "/api/v1/tickets", exchange(oop, dbms)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.actingFacultyId").value(this.facultyRao));
		// A lecture that has started cannot be exchanged.
		this.clock.set(AFTER_THE_FIRST_LECTURE_STARTS);
		post(user(RAO_USER), "/api/v1/tickets", exchange(oop, dbms)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.lecture_started"));
	}

	// --- applying ---------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("applying it swaps the two lectures together, and each teacher's time goes with their lecture")
	void applyingSwapsThem() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String dbms = lecture(this.theoryDbms, "2026-07-06");
		String ticket = raise(RAO_USER, exchange(oop, dbms));

		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.approvalPath").value("SELF"))
			.andExpect(jsonPath("$.changes[0].before.startTime").value("09:00:00"))
			.andExpect(jsonPath("$.changes[1].before.startTime").value("10:00:00"));

		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.startTime").value("10:00:00"))
			.andExpect(jsonPath("$.changed").value(true))
			.andExpect(jsonPath("$.facultyId").value(this.facultyRao));
		get(admin(), "/api/v1/timetable/live/" + dbms).andExpect(jsonPath("$.startTime").value("09:00:00"))
			.andExpect(jsonPath("$.facultyId").value(this.facultyRoy));
		assertThat(hourClaims(oop, 10)).as("Rao's lecture now holds the 10 o'clock hour").isEqualTo(5);
		assertThat(hourClaims(dbms, 9)).isEqualTo(5);
		assertThat(hourClaims(oop, 9)).isZero();
	}

	@Test
	@DisplayName("lectures on different dates change places across the dates")
	void acrossDates() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String later = lecture(this.theoryDbms, "2026-07-13");

		applyOk(RAO_USER, raise(RAO_USER, exchange(oop, later)));

		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.date").value("2026-07-13"))
			.andExpect(jsonPath("$.startTime").value("10:00:00"))
			.andExpect(jsonPath("$.originalDate").value("2026-07-06"));
		get(admin(), "/api/v1/timetable/live/" + later).andExpect(jsonPath("$.date").value("2026-07-06"))
			.andExpect(jsonPath("$.startTime").value("09:00:00"));
		// A lecture goes online or into a room with the place it takes: the online DBMS lecture's mode comes with it.
		String online = lecture(this.theoryDbms, "2026-07-08");
		applyOk(RAO_USER, raise(RAO_USER, exchange(lecture(this.theoryOop, "2026-07-20"), online)));
		get(admin(), "/api/v1/timetable/live/" + lecture(this.theoryOop, "2026-07-20"))
			.andExpect(jsonPath("$.mode").value("ONLINE"))
			.andExpect(jsonPath("$.roomId").doesNotExist());
		get(admin(), "/api/v1/timetable/live/" + online).andExpect(jsonPath("$.mode").value("IN_PERSON"))
			.andExpect(jsonPath("$.roomId").value(this.room101));
	}

	@Test
	@DisplayName("if one of the moves is blocked, neither is made")
	void allOrNothing() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String later = lecture(this.theoryDbms, "2026-07-13");
		String ticket = raise(RAO_USER, exchange(oop, later));
		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(jsonPath("$.conflicted").value(false));
		// Rao is also given a class in another division at the hour the exchange would put him in (Monday the 13th, 10-11).
		String other = assignment(this.divisionMeA, null, this.subjectOop, this.facultyRao, "THEORY");
		applyOk(RAO_USER, raise(RAO_USER, "{\"type\":\"EXTRA\",\"assignmentId\":\"" + other + "\",\"target\":"
				+ target("2026-07-13", 10, 11, this.room201) + "}"));

		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.blocked_by_conflicts"));
		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(jsonPath("$.conflicted").value(true))
			.andExpect(jsonPath("$.conflicts[*].type", hasItem("SAME_SLOT_TICKET")));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.date").value("2026-07-06"))
			.andExpect(jsonPath("$.changed").value(false));
		get(admin(), "/api/v1/timetable/live/" + later).andExpect(jsonPath("$.date").value("2026-07-13"))
			.andExpect(jsonPath("$.changed").value(false));
	}

	@Test
	@DisplayName("a lecture cancelled after the exchange was raised blocks it")
	void aCancelledLectureBlocksIt() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String dbms = lecture(this.theoryDbms, "2026-07-06");
		String ticket = raise(RAO_USER, exchange(oop, dbms));
		applyOk(ROY_USER, raise(ROY_USER, cancel(dbms, "Away")));

		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.errors[*].code", hasItem("occurrence_changed")));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.startTime").value("09:00:00"));
	}

	@Test
	@DisplayName("the HOD raises it for a teacher but cannot apply it, as with any change that needs the teacher's own approval")
	void theHodCannotApplyIt() throws Exception {
		UUID hod = personWith("HOD", this.deptCse);
		String ticket = raise(user(hod), exchange(lecture(this.theoryOop, "2026-07-06"), lecture(this.theoryDbms, "2026-07-06")));

		post(user(hod), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isForbidden());
	}

	// --- consent, editing -------------------------------------------------------------------------------------------

	@Test
	@DisplayName("the other teacher may be asked to agree: their approval applies the exchange, their refusal stops it")
	void theOtherTeacherMayBeAsked() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String dbms = lecture(this.theoryDbms, "2026-07-06");
		String agreed = raise(RAO_USER, exchange(oop, dbms));
		post(user(RAO_USER), "/api/v1/tickets/" + agreed + "/submit", reviewers(ROY_USER)).andExpect(status().isOk());
		post(user(ROY_USER), "/api/v1/tickets/" + agreed + "/review", "{\"decision\":\"APPROVE\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.approvalPath").value("REVIEWED"));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.startTime").value("10:00:00"));

		String later = lecture(this.theoryOop, "2026-07-13");
		String refused = raise(RAO_USER, exchange(later, lecture(this.theoryDbms, "2026-07-13")));
		post(user(RAO_USER), "/api/v1/tickets/" + refused + "/submit", reviewers(ROY_USER)).andExpect(status().isOk());
		post(user(ROY_USER), "/api/v1/tickets/" + refused + "/review", "{\"decision\":\"REJECT\",\"reason\":\"I need that hour\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"));
		get(admin(), "/api/v1/timetable/live/" + later).andExpect(jsonPath("$.startTime").value("09:00:00"));
	}

	@Test
	@DisplayName("editing an exchange plans it again from the lectures as they are now; a plan of its own is refused")
	void editingPlansItAgain() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String dbms = lecture(this.theoryDbms, "2026-07-06");
		String ticket = raise(RAO_USER, exchange(oop, dbms));
		// Roy moves his lecture elsewhere first, so the plan is out of date.
		applyOk(ROY_USER, raise(ROY_USER, move(dbms, "2026-07-09", 10, 11, this.room101)));
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.errors[*].code", hasItem("occurrence_changed")));

		String version = read(get(user(RAO_USER), "/api/v1/tickets/" + ticket), "$.version");
		put(user(RAO_USER), "/api/v1/tickets/" + ticket, "{\"target\":" + target("2026-07-09", 9, 10, this.room101) + ",\"version\":"
				+ version + "}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("target")));
		put(user(RAO_USER), "/api/v1/tickets/" + ticket, "{\"description\":\"Updated\",\"version\":" + version + "}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.conflicted").value(false))
			.andExpect(jsonPath("$.changes[0].target.date").value("2026-07-09"))
			.andExpect(jsonPath("$.changes[1].target.date").value("2026-07-06"));
		applyOk(RAO_USER, ticket);
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.date").value("2026-07-09"))
			.andExpect(jsonPath("$.startTime").value("10:00:00"));
	}

	// --- seeing it --------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("the other teacher sees the exchange without being asked, and the list and the single-ticket decision agree")
	void theOtherTeacherSeesIt() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String dbms = lecture(this.theoryDbms, "2026-07-06");
		String exchange = raise(RAO_USER, exchange(oop, dbms));
		String unrelated = raise(MEHTA_USER, cancel(lecture(this.theoryThermo, "2026-07-13"), "Away"));

		Map<String, RequestPostProcessor> people = new LinkedHashMap<>();
		people.put("admin", admin());
		people.put("rao", user(RAO_USER));
		people.put("roy, the other teacher", user(ROY_USER));
		people.put("mehta", user(MEHTA_USER));
		people.put("hod cse", user(personWith("HOD", this.deptCse)));
		people.put("hod me", user(personWith("HOD", this.deptMe)));
		people.put("cr cse-a", user(personWith("CR", this.divisionCseA)));
		people.put("br a1", user(personWith("BR", this.batchA1)));
		people.put("student", user(personWith("Student", this.batchA1)));
		people.put("nobody", user(somebody()));

		for (Map.Entry<String, RequestPostProcessor> person : people.entrySet()) {
			Set<String> allowed = Set.of(exchange, unrelated).stream().filter((id) -> {
				try {
					return get(person.getValue(), "/api/v1/tickets/" + id).andReturn().getResponse().getStatus() == 200;
				}
				catch (Exception ex) {
					throw new IllegalStateException(ex);
				}
			}).collect(Collectors.toSet());
			assertThat(listedTickets(person.getValue(), "")).as("the list of " + person.getKey()).isEqualTo(allowed);
		}
		assertThat(listedTickets(user(ROY_USER), "")).containsExactly(exchange);
		// A search by either lecture finds it.
		assertThat(listedTickets(user(RAO_USER), "&lecture=" + dbms)).containsExactly(exchange);
		assertThat(listedTickets(user(RAO_USER), "&lecture=" + oop)).containsExactly(exchange);
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private static String exchange(String lecture, String other) {
		return "{\"type\":\"EXCHANGE\",\"lectureId\":\"" + lecture + "\",\"otherLectureId\":\"" + other
				+ "\",\"description\":\"Swap\"}";
	}

	private static String reviewers(UUID user) {
		return "{\"reviewers\":[{\"userId\":\"" + user + "\",\"capacity\":\"AFFECTED_FACULTY\"}]}";
	}

	/** How many claims the lecture holds at the hour. */
	private int hourClaims(String lecture, int hour) {
		return count("select count(*) from occurrence_claim where occurrence_id = unhex('" + hex(UUID.fromString(lecture))
				+ "') and cell_hour = " + hour);
	}

}
