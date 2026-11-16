package com.kibootia.backend.rescheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The names a ticket carries so that a screen never has to map ids: the author, the teacher, the subject and class, the rooms
 * and the lecture as it is now. A record that was deleted gives a missing name, never an error.
 */
class TicketNamesApiIT extends ReschedulingApiTest {

	@Test
	@DisplayName("a reschedule names its author, teacher, subject, class, the lecture now and the target room")
	void aRescheduleCarriesItsNames() throws Exception {
		String lecture = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, move(lecture, "2026-07-09", 9, 10, this.room102));

		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(status().isOk())
			.andExpect(jsonPath("$.authorName").value(not(emptyOrNullString())))
			.andExpect(jsonPath("$.facultyName").value("Dr Rao"))
			.andExpect(jsonPath("$.subjectCode").value("OOP"))
			.andExpect(jsonPath("$.subjectName").value("Object-Oriented Programming"))
			.andExpect(jsonPath("$.divisionCode").value("CSE-A"))
			.andExpect(jsonPath("$.batchCode").value(nullValue()))
			.andExpect(jsonPath("$.change.lecture.date").value("2026-07-06"))
			.andExpect(jsonPath("$.change.lecture.roomCode").value("R101"))
			.andExpect(jsonPath("$.change.lecture.subjectCode").value("OOP"))
			.andExpect(jsonPath("$.change.lecture.facultyName").value("Dr Rao"))
			.andExpect(jsonPath("$.change.target.roomCode").value("R102"))
			.andExpect(jsonPath("$.change.before").value(nullValue()));
	}

	@Test
	@DisplayName("once applied, the room it came from is named, and the lecture is shown where it is now")
	void anAppliedTicketNamesTheRoomItLeft() throws Exception {
		String ticket = raise(RAO_USER, move(lecture(this.theoryOop, "2026-07-06"), "2026-07-09", 9, 10, this.room102));
		applyOk(RAO_USER, ticket);

		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(status().isOk())
			.andExpect(jsonPath("$.change.before.roomCode").value("R101"))
			.andExpect(jsonPath("$.change.target.roomCode").value("R102"))
			.andExpect(jsonPath("$.change.lecture.date").value("2026-07-09"))
			.andExpect(jsonPath("$.change.lecture.roomCode").value("R102"));
	}

	@Test
	@DisplayName("a substitute ticket names the teacher who takes the lecture, and after apply the one who had it")
	void aSubstituteTicketNamesBothTeachers() throws Exception {
		String lecture = lecture(this.theoryOop, "2026-07-13");
		String ticket = raise(RAO_USER, "{\"type\":\"SUBSTITUTE\",\"lectureId\":\"" + lecture + "\",\"substituteFacultyId\":\""
				+ this.facultyRoy + "\"}");

		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(status().isOk())
			.andExpect(jsonPath("$.change.substituteFacultyName").value("Dr Roy"))
			.andExpect(jsonPath("$.change.beforeFacultyName").value(nullValue()));
		applyOk(RAO_USER, ticket);
		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(status().isOk())
			.andExpect(jsonPath("$.change.substituteFacultyName").value("Dr Roy"))
			.andExpect(jsonPath("$.change.beforeFacultyName").value("Dr Rao"))
			.andExpect(jsonPath("$.change.lecture.facultyName").value("Dr Roy"));
	}

	@Test
	@DisplayName("a reviewer is named once asked")
	void aReviewerIsNamed() throws Exception {
		String ticket = raise(RAO_USER, move(lecture(this.theoryOop, "2026-07-06"), "2026-07-09", 9, 10, this.room101));
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/submit",
				"{\"reviewers\":[{\"userId\":\"" + ROY_USER + "\",\"capacity\":\"OTHER_FACULTY\"}]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviews[0].reviewerName").value(not(emptyOrNullString())));
	}

	@Test
	@DisplayName("a deleted room leaves the code out and does not break the response")
	void aDeletedRoomIsAMissingName() throws Exception {
		String ticket = raise(RAO_USER, move(lecture(this.theoryOop, "2026-07-06"), "2026-07-09", 9, 10, this.room102));
		delete(admin(), "/api/v1/rooms/" + this.room102).andExpect(status().isNoContent());

		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(status().isOk())
			.andExpect(jsonPath("$.change.target.roomId").value(this.room102))
			.andExpect(jsonPath("$.change.target.roomCode").value(nullValue()))
			.andExpect(jsonPath("$.change.lecture.roomCode").value("R101"));
	}

	@Test
	@DisplayName("a list carries the same top-level names but not the lecture, and every action answers with names too")
	void aListCarriesNamesButNotTheLecture() throws Exception {
		String ticket = raise(RAO_USER, move(lecture(this.theoryOop, "2026-07-06"), "2026-07-09", 9, 10, this.room102));

		get(user(RAO_USER), "/api/v1/tickets").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(ticket))
			.andExpect(jsonPath("$.items[0].authorName").value(not(emptyOrNullString())))
			.andExpect(jsonPath("$.items[0].facultyName").value("Dr Rao"))
			.andExpect(jsonPath("$.items[0].subjectCode").value("OOP"))
			.andExpect(jsonPath("$.items[0].divisionCode").value("CSE-A"))
			.andExpect(jsonPath("$.items[0].change.target.roomCode").value("R102"))
			.andExpect(jsonPath("$.items[0].change.lecture").doesNotExist());
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/apply", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.subjectCode").value("OOP"))
			.andExpect(jsonPath("$.change.lecture.roomCode").value("R102"));
	}

}
