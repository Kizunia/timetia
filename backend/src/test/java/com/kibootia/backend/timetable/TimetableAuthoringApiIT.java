package com.kibootia.backend.timetable;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arranging the base timetable through the real stack: versions, the weekly lectures of a draft, publishing and what it
 * does to the dated lectures. The rules under test are the data model's: a published version is never edited, a version
 * cannot book a teacher, a room or students twice, and a version that takes effect later replaces the earlier one from its
 * date.
 */
class TimetableAuthoringApiIT extends TimetableApiTest {

	// --- versions ---------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a draft is created for a division in a term and starts as a draft")
	void createsADraft() throws Exception {
		post(admin(), "/api/v1/timetable/versions",
				"{\"divisionId\":\"" + this.divisionCseA + "\",\"termId\":\"" + this.term + "\",\"effectiveFrom\":\"2026-07-13\"}")
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", containsString("/api/v1/timetable/versions/")))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.effectiveFrom").value("2026-07-13"))
			.andExpect(jsonPath("$.divisionId").value(this.divisionCseA));
	}

	@Test
	@DisplayName("a version must start within its term, and name a division and a term that exist")
	void versionReferencesAreChecked() throws Exception {
		post(admin(), "/api/v1/timetable/versions",
				"{\"divisionId\":\"" + this.divisionCseA + "\",\"termId\":\"" + this.term + "\",\"effectiveFrom\":\"2026-09-01\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("outside_term")));

		post(admin(), "/api/v1/timetable/versions", "{\"divisionId\":\"" + UUID.randomUUID() + "\",\"termId\":\""
				+ UUID.randomUUID() + "\",\"effectiveFrom\":\"2026-07-13\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("divisionId")))
			.andExpect(jsonPath("$.errors[*].field", hasItem("termId")));
	}

	@Test
	@DisplayName("only the administrator arranges the timetable")
	void anOrdinaryUserMayNotArrangeIt() throws Exception {
		UUID user = somebody();
		post(user(user), "/api/v1/timetable/versions",
				"{\"divisionId\":\"" + this.divisionCseA + "\",\"termId\":\"" + this.term + "\",\"effectiveFrom\":\"2026-07-13\"}")
			.andExpect(status().isForbidden());
		get(user(user), "/api/v1/timetable/versions").andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("a draft can be listed by division and removed, and a missing version is a 404")
	void listsAndDeletesADraft() throws Exception {
		String version = draft(this.divisionCseA, "2026-07-13");

		get(admin(), "/api/v1/timetable/versions?division=" + this.divisionCseA + "&status=DRAFT").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].id", hasItem(version)));
		delete(admin(), "/api/v1/timetable/versions/" + version).andExpect(status().isNoContent());
		get(admin(), "/api/v1/timetable/versions/" + version).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("timetable.version_not_found"));
	}

	// --- the lectures of a draft ------------------------------------------------------------------------------------

	@Test
	@DisplayName("a lecture is added to a draft and read back, in person with a room or online with a link")
	void addsAndReadsEntries() throws Exception {
		String version = draft(this.divisionCseA, TERM_START);

		post(admin(), "/api/v1/timetable/versions/" + version + "/entries",
				entryBody(this.theoryOop, 1, 9, 10, this.room101)).andExpect(status().isCreated())
			.andExpect(header().string("Location", containsString("/entries/")))
			.andExpect(jsonPath("$.mode").value("IN_PERSON"))
			.andExpect(jsonPath("$.roomId").value(this.room101));
		post(admin(), "/api/v1/timetable/versions/" + version + "/entries", onlineEntryBody(this.theoryDbms, 3, 14, 15))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.mode").value("ONLINE"))
			.andExpect(jsonPath("$.roomId").doesNotExist())
			.andExpect(jsonPath("$.meetingLink").value("https://meet.example/abc"));

		get(admin(), "/api/v1/timetable/versions/" + version + "/entries").andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].dayOfWeek").value(1))
			.andExpect(jsonPath("$.items[1].dayOfWeek").value(3));
	}

	@Test
	@DisplayName("every bad field of a lecture is reported together")
	void entryFieldsAreChecked() throws Exception {
		String version = draft(this.divisionCseA, TERM_START);
		String path = "/api/v1/timetable/versions/" + version + "/entries";

		// In person but no room.
		post(admin(), path, "{\"assignmentId\":\"" + this.theoryOop
				+ "\",\"dayOfWeek\":1,\"startTime\":\"09:00\",\"endTime\":\"10:00\",\"mode\":\"IN_PERSON\"}")
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].code", hasItem("room_required")));
		// Online with a room.
		post(admin(), path, "{\"assignmentId\":\"" + this.theoryOop + "\",\"dayOfWeek\":1,\"startTime\":\"09:00\","
				+ "\"endTime\":\"10:00\",\"mode\":\"ONLINE\",\"roomId\":\"" + this.room101 + "\"}")
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].code", hasItem("room_not_allowed")));
		// A time off the hour, and a day that is not a teaching day.
		post(admin(), path, "{\"assignmentId\":\"" + this.theoryOop + "\",\"dayOfWeek\":7,\"startTime\":\"09:30\","
				+ "\"endTime\":\"10:30\",\"mode\":\"IN_PERSON\",\"roomId\":\"" + this.room101 + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("invalid_day")))
			.andExpect(jsonPath("$.errors[*].code", hasItem("invalid_time")));
		// Outside the 08:00 to 20:00 day.
		post(admin(), path, entryBody(this.theoryOop, 1, 19, 21, this.room101)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("invalid_time")));
		// A teaching assignment of another division, an unknown one, and an unknown room.
		post(admin(), path, entryBody(this.theoryThermo, 1, 9, 10, this.room101)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("assignment_of_another_division")));
		post(admin(), path, entryBody(UUID.randomUUID().toString(), 1, 9, 10, UUID.randomUUID().toString()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("unknown_assignment")))
			.andExpect(jsonPath("$.errors[*].code", hasItem("unknown_room")));
	}

	@Test
	@DisplayName("a lecture that would book the same teacher, room or students twice is refused when added")
	void clashesAreRefusedWhenAdded() throws Exception {
		String version = draft(this.divisionCseA, TERM_START);
		entry(version, this.theoryOop, 1, 9, 10, this.room101);
		String path = "/api/v1/timetable/versions/" + version + "/entries";

		// Rao again, at an overlapping time, in another room, for a batch: the faculty member is double booked.
		post(admin(), path, entryBody(this.labA1, 1, 9, 11, this.lab1)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("timetable.clash"))
			.andExpect(jsonPath("$.detail", containsString("faculty")));
		// Another teacher in the same room at the same time.
		post(admin(), path, entryBody(this.theoryDbms, 1, 9, 10, this.room101)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("room")));
		// Another teacher and room, but a lab of a batch of the division while the whole division has theory.
		post(admin(), path, entryBody(this.labA2, 1, 9, 11, this.room102)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("students")));
		// Meeting end to end is fine.
		post(admin(), path, entryBody(this.theoryDbms, 1, 10, 11, this.room101)).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("two batches of a division can have their labs at the same time")
	void parallelLabsAreAllowed() throws Exception {
		String version = draft(this.divisionCseA, TERM_START);
		entry(version, this.labA1, 2, 9, 11, this.lab1);
		post(admin(), "/api/v1/timetable/versions/" + version + "/entries", entryBody(this.labA2, 2, 9, 11, this.room102))
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("a lecture can be removed from a draft, and one that is not in the version is a 404")
	void removesAnEntry() throws Exception {
		String version = draft(this.divisionCseA, TERM_START);
		String other = draft(this.divisionCseA, "2026-07-13");
		String entry = entry(version, this.theoryOop, 1, 9, 10, this.room101);

		delete(admin(), "/api/v1/timetable/versions/" + other + "/entries/" + entry).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("timetable.entry_not_found"));
		delete(admin(), "/api/v1/timetable/versions/" + version + "/entries/" + entry).andExpect(status().isNoContent());
		get(admin(), "/api/v1/timetable/versions/" + version + "/entries").andExpect(jsonPath("$.items.length()").value(0));
	}

	// --- publishing -------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("publishing creates a dated lecture for each time a weekly lecture falls in the term, and fixes the version")
	void publishCreatesTheDatedLectures() throws Exception {
		publishStandardTimetable();

		// CSE-A: Monday x2, Tuesday labs x2, Wednesday online x1, each four times = 20. ME-A: 4.
		assertThat(count("select count(*) from lecture_occurrence where record_status = 'ACTIVE'")).isEqualTo(24);
		assertThat(count("select count(*) from lecture_occurrence where division_id = unhex(?)", hex(UUID.fromString(this.divisionCseA))))
			.isEqualTo(20);
		assertThat(count("select count(*) from timetable_version where status = 'PUBLISHED'")).isEqualTo(2);
		// The first Monday of the term, and a lecture is copied with where it is held and who teaches it.
		assertThat(count("select count(*) from lecture_occurrence where occurrence_date = '2026-07-06' and session_type = 'THEORY'"))
			.isEqualTo(3);
	}

	@Test
	@DisplayName("a published version is never edited or removed, and cannot be published again")
	void aPublishedVersionIsFixed() throws Exception {
		String version = draft(this.divisionCseA, TERM_START);
		String entry = entry(version, this.theoryOop, 1, 9, 10, this.room101);
		publish(version);

		get(admin(), "/api/v1/timetable/versions/" + version).andExpect(jsonPath("$.status").value("PUBLISHED"));
		post(admin(), "/api/v1/timetable/versions/" + version + "/entries", entryBody(this.theoryDbms, 2, 9, 10, this.room101))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("timetable.version_published"));
		delete(admin(), "/api/v1/timetable/versions/" + version + "/entries/" + entry).andExpect(status().isConflict());
		delete(admin(), "/api/v1/timetable/versions/" + version).andExpect(status().isConflict());
		post(admin(), "/api/v1/timetable/versions/" + version + "/publish", null).andExpect(status().isConflict());
	}

	@Test
	@DisplayName("an empty version cannot be published, and two published versions cannot take effect on one day")
	void publishPreconditions() throws Exception {
		String empty = draft(this.divisionCseA, TERM_START);
		post(admin(), "/api/v1/timetable/versions/" + empty + "/publish", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("timetable.version_empty"));

		String first = draft(this.divisionCseA, TERM_START);
		entry(first, this.theoryOop, 1, 9, 10, this.room101);
		publish(first);
		String second = draft(this.divisionCseA, TERM_START);
		entry(second, this.theoryOop, 2, 9, 10, this.room101);
		post(admin(), "/api/v1/timetable/versions/" + second + "/publish", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("timetable.effective_from_taken"));
	}

	@Test
	@DisplayName("a division cannot book a teacher or a room that another division's published timetable already has")
	void clashesWithAnotherDivisionAreRefusedAtPublish() throws Exception {
		String cse = draft(this.divisionCseA, TERM_START);
		entry(cse, this.theoryOop, 1, 9, 10, this.room101);
		publish(cse);

		// Rao teaches ME-A too, on Monday 9-10, while teaching CSE-A then.
		String raoForMe = assignment(this.divisionMeA, null, this.subjectOop, this.facultyRao, "THEORY");
		String sameTeacher = draft(this.divisionMeA, TERM_START);
		entry(sameTeacher, raoForMe, 1, 9, 10, this.room201);
		post(admin(), "/api/v1/timetable/versions/" + sameTeacher + "/publish", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("timetable.clash"))
			.andExpect(jsonPath("$.detail", containsString("faculty")));
		assertThat(count("select count(*) from lecture_occurrence where division_id = unhex(?)", hex(UUID.fromString(this.divisionMeA))))
			.as("nothing is created when publishing is refused").isZero();

		// Another teacher, but Room 101, which CSE-A holds then.
		String sameRoom = draft(this.divisionMeA, "2026-07-13");
		entry(sameRoom, this.theoryThermo, 1, 9, 10, this.room101);
		post(admin(), "/api/v1/timetable/versions/" + sameRoom + "/publish", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("room")));
	}

	// --- one version replaces another -------------------------------------------------------------------------------

	@Test
	@DisplayName("a version that takes effect later replaces the earlier one from its date, and the earlier lectures stay")
	void aLaterVersionSupersedesFromItsDate() throws Exception {
		String first = draft(this.divisionCseA, TERM_START);
		entry(first, this.theoryOop, 1, 9, 10, this.room101);
		publish(first);
		assertThat(count("select count(*) from lecture_occurrence where record_status = 'ACTIVE'")).isEqualTo(4);

		String second = draft(this.divisionCseA, "2026-07-20");
		entry(second, this.theoryDbms, 2, 11, 12, this.room102);
		post(admin(), "/api/v1/timetable/versions/" + second + "/publish", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.occurrencesCreated").value(2))
			.andExpect(jsonPath("$.occurrencesSuperseded").value(2));

		// Mondays 6 and 13 stay from the first version; Mondays 20 and 27 are replaced by Tuesdays 21 and 28.
		assertThat(count("select count(*) from lecture_occurrence where record_status = 'SUPERSEDED'")).isEqualTo(2);
		assertThat(count("select count(*) from lecture_occurrence where record_status = 'ACTIVE' and occurrence_date < '2026-07-20'"))
			.isEqualTo(2);
		assertThat(count("select count(*) from lecture_occurrence where record_status = 'ACTIVE' and occurrence_date >= '2026-07-20'"))
			.isEqualTo(2);
		// What the replaced lectures held is let go, and each active lecture holds its faculty member, room, division and batches.
		assertThat(count("select count(*) from occurrence_claim c join lecture_occurrence o on o.id = c.occurrence_id "
				+ "where o.record_status = 'SUPERSEDED'")).isZero();
		assertThat(count("select count(*) from occurrence_claim"))
			.as("4 theory lectures, each holding a faculty member, a room, the division and its two batches")
			.isEqualTo(4 * 5);
	}

	@Test
	@DisplayName("a version published after a later one only covers the time before it takes over")
	void anEarlierVersionPublishedLateIsCappedAtTheNextOne() throws Exception {
		String later = draft(this.divisionCseA, "2026-07-20");
		entry(later, this.theoryDbms, 2, 11, 12, this.room102);
		publish(later);

		String earlier = draft(this.divisionCseA, TERM_START);
		entry(earlier, this.theoryOop, 1, 9, 10, this.room101);
		post(admin(), "/api/v1/timetable/versions/" + earlier + "/publish", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.occurrencesCreated").value(2))
			.andExpect(jsonPath("$.occurrencesSuperseded").value(0));

		assertThat(count("select count(*) from lecture_occurrence where record_status = 'ACTIVE'")).isEqualTo(4);
		assertThat(count("select max(occurrence_date) = '2026-07-13' from lecture_occurrence where timetable_version_id = unhex(?)",
				hex(UUID.fromString(earlier)))).isEqualTo(1);
	}

}
