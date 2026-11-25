package com.kibootia.backend.timetable;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.kibootia.backend.rescheduling.ReschedulingApiTest;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Which rooms are free at a time. In the standard timetable Monday 6 July holds R101 (OOP 9-10 and DBMS 10-11) and R201
 * (Thermodynamics 9-10); the lectures repeat each week of the term, so Monday the 13th is as busy. Thursday 9 July is empty.
 */
class FreeRoomsApiIT extends ReschedulingApiTest {

	private static final String PATH = "/api/v1/timetable/free-rooms";

	@Test
	@DisplayName("on a day with nothing held every room is free, by code, with its type and seats")
	void everyRoomIsFreeOnAnEmptyDay() throws Exception {
		get(user(somebody()), PATH + "?date=2026-07-09&startTime=09:00&endTime=10:00").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("LAB1", "R101", "R102", "R201")))
			.andExpect(jsonPath("$[0].roomType").value("LAB"))
			.andExpect(jsonPath("$[0].capacity").value(30))
			.andExpect(jsonPath("$[1].roomType").value("CLASSROOM"))
			.andExpect(jsonPath("$[1].capacity").value(nullValue()))
			.andExpect(jsonPath("$[*].fits", contains(true, true, true, true)));
	}

	@Test
	@DisplayName("a room held by a lecture in that hour is not offered")
	void aHeldRoomIsNotOffered() throws Exception {
		get(user(RAO_USER), PATH + "?date=2026-07-06&startTime=09:00&endTime=10:00").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("LAB1", "R102")));
		// The same on the next Monday: a weekly lecture holds its room every week.
		get(user(RAO_USER), PATH + "?date=2026-07-13&startTime=09:00&endTime=10:00").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("LAB1", "R102")));
	}

	@Test
	@DisplayName("the lecture that is about to move may keep its own room")
	void theMovingLectureMayKeepItsRoom() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");

		get(user(RAO_USER), PATH + "?date=2026-07-06&startTime=09:00&endTime=10:00&excludeLecture=" + oop)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("LAB1", "R101", "R102")));
		// Another lecture's claim still counts: excluding OOP does not free R201, which Thermodynamics holds.
		get(user(RAO_USER), PATH + "?date=2026-07-06&startTime=09:00&endTime=10:00&excludeLecture=" + oop)
			.andExpect(jsonPath("$[*].code", not(hasItem("R201"))));
	}

	@Test
	@DisplayName("a longer time is free only when every hour of it is free")
	void everyHourMustBeFree() throws Exception {
		// DBMS holds R101 on Monday 10-11, so a window of 10-12 loses R101 although its second hour is free.
		get(user(RAO_USER), PATH + "?date=2026-07-06&startTime=10:00&endTime=12:00").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("LAB1", "R102", "R201")));
		// The two labs hold LAB1 and R102 on Tuesday 9-11.
		get(user(RAO_USER), PATH + "?date=2026-07-07&startTime=09:00&endTime=11:00").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("R101", "R201")));
		// A window that starts before the labs and runs into their first hour loses those rooms as well.
		get(user(RAO_USER), PATH + "?date=2026-07-07&startTime=08:00&endTime=10:00").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("R101", "R201")));
	}

	@Test
	@DisplayName("rooms of a suitable kind come first and are marked; the others stay listed, marked as not fitting")
	void roomsThatFitComeFirst() throws Exception {
		String lab = PATH + "?date=2026-07-09&startTime=09:00&endTime=11:00&sessionType=LAB";
		get(user(RAO_USER), lab).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("LAB1", "R101", "R102", "R201")))
			.andExpect(jsonPath("$[*].fits", contains(true, false, false, false)));

		String theory = PATH + "?date=2026-07-09&startTime=09:00&endTime=10:00&sessionType=THEORY";
		get(user(RAO_USER), theory).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("R101", "R102", "R201", "LAB1")))
			.andExpect(jsonPath("$[*].fits", contains(true, true, true, false)));

		// A tutorial may be held in a tutorial room or a classroom, not in a lab.
		String tutorial = PATH + "?date=2026-07-09&startTime=09:00&endTime=10:00&sessionType=TUTORIAL";
		get(user(RAO_USER), tutorial).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("R101", "R102", "R201", "LAB1")))
			.andExpect(jsonPath("$[3].fits").value(false));
	}

	@Test
	@DisplayName("the room of a cancelled lecture is free again")
	void aCancelledLecturesRoomIsFree() throws Exception {
		String ticket = raise(RAO_USER, cancel(lecture(this.theoryOop, "2026-07-06"), "Away"));
		applyOk(RAO_USER, ticket);

		get(user(RAO_USER), PATH + "?date=2026-07-06&startTime=09:00&endTime=10:00").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("LAB1", "R101", "R102")));
	}

	@Test
	@DisplayName("a Sunday, a time off the hour, an end before the start and hours outside the day are refused with the field")
	void badTimesAreRefused() throws Exception {
		get(user(RAO_USER), PATH + "?date=2026-07-12&startTime=09:00&endTime=10:00").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("date")))
			.andExpect(jsonPath("$.errors[*].code", hasItem("sunday")));
		for (String times : new String[] { "startTime=09:30&endTime=10:30", "startTime=10:00&endTime=09:00",
				"startTime=09:00&endTime=09:00", "startTime=07:00&endTime=09:00", "startTime=19:00&endTime=21:00" }) {
			get(user(RAO_USER), PATH + "?date=2026-07-09&" + times).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].code", hasItem("invalid_time")));
		}
		get(user(RAO_USER), PATH + "?date=2026-07-09&startTime=09:00").andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("a person whose email is not verified is refused, and a request with no token is 401")
	void unverifiedAndAnonymousAreRefused() throws Exception {
		UUID person = somebody();
		String query = PATH + "?date=2026-07-09&startTime=09:00&endTime=10:00";

		get(unverified(person), query).andExpect(status().isForbidden());
		this.mvc.perform(MockMvcRequestBuilders.get(query)).andExpect(status().isUnauthorized());
	}

}
