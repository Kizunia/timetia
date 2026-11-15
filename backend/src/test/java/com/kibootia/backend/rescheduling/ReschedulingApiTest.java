package com.kibootia.backend.rescheduling;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.kibootia.backend.platform.fixtures.MutableClockConfiguration;
import com.kibootia.backend.platform.fixtures.MutableClockConfiguration.MutableClock;
import com.kibootia.backend.platform.persistence.UuidBytes;
import com.kibootia.backend.timetable.TimetableApiTest;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The base of the ticket tests: the campus and the published timetable of {@link TimetableApiTest} (CSE-A has OOP theory on
 * Monday 9-10, DBMS theory on Monday 10-11, both in R101, the two labs on Tuesday 9-11 and DBMS online on Wednesday; ME-A has
 * Thermodynamics on Monday 9-10 in R201), the three teachers holding the Faculty role, and a clock the test controls. The
 * clock starts before the term; a test moves it to show a lecture that has started.
 */
@Import(MutableClockConfiguration.class)
abstract class ReschedulingApiTest extends TimetableApiTest {

	/** Before the first lecture, which is Monday 6 July at 09:00 on the campus clock, 03:30 UTC. */
	static final Instant BEFORE_THE_TERM = Instant.parse("2026-07-01T00:00:00Z");

	/** After the first lecture of the term has started. */
	static final Instant AFTER_THE_FIRST_LECTURE_STARTS = Instant.parse("2026-07-06T04:00:00Z");

	@Autowired
	MutableClock clock;

	@BeforeEach
	void publishTheTimetableAndGiveTeachersTheirRole() throws Exception {
		this.clock.set(BEFORE_THE_TERM);
		publishStandardTimetable();
		assign(RAO_USER, "Faculty", null);
		assign(ROY_USER, "Faculty", null);
		assign(MEHTA_USER, "Faculty", null);
	}

	/** The lecture of the teaching assignment on the date, as the base timetable made it. */
	protected String lecture(String assignment, String date) {
		byte[] id = this.jdbc.queryForObject(
				"select id from lecture_occurrence where assignment_id = unhex(?) and occurrence_date = ?", byte[].class,
				hex(UUID.fromString(assignment)), date);
		return UuidBytes.fromBytes(id).toString();
	}

	/** The body that raises a reschedule of the lecture to an in-person slot. */
	protected static String move(String lecture, String date, int from, int to, String room) {
		return "{\"type\":\"RESCHEDULE\",\"lectureId\":\"" + lecture + "\",\"description\":\"Needs to move\",\"target\":"
				+ target(date, from, to, room) + "}";
	}

	protected static String target(String date, int from, int to, String room) {
		return "{\"date\":\"" + date + "\",\"startTime\":\"" + hour(from) + "\",\"endTime\":\"" + hour(to)
				+ "\",\"mode\":\"IN_PERSON\",\"roomId\":\"" + room + "\"}";
	}

	/** The body that raises a cancellation of the lecture. */
	protected static String cancel(String lecture, String reason) {
		return "{\"type\":\"CANCEL\",\"lectureId\":\"" + lecture + "\",\"reason\":\"" + reason + "\"}";
	}

	/** Raises a ticket as the person, expects it to be accepted, and returns its ID. */
	protected String raise(RequestPostProcessor who, String body) throws Exception {
		return read(post(who, "/api/v1/tickets", body).andExpect(status().isCreated()), "$.id");
	}

	protected String raise(UUID who, String body) throws Exception {
		return raise(user(who), body);
	}

	protected void applyOk(UUID who, String ticket) throws Exception {
		post(user(who), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isOk());
	}

	/** The IDs of the tickets the person sees in a list. */
	protected Set<String> listedTickets(RequestPostProcessor who, String query) throws Exception {
		String json = get(who, "/api/v1/tickets?size=100" + query).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		List<String> ids = JsonPath.read(json, "$.items[*].id");
		return new HashSet<>(ids);
	}

}
