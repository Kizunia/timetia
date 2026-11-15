package com.kibootia.backend.timetable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The live timetable through the real stack: what each role sees, and that a list and a single lecture agree. The campus is
 * built through the APIs (see {@link TimetableApiTest}), people are given their roles as the screen does, and every
 * answer comes from the real reference data, the real scope directory and the real decision point, so this is the proof
 * that the authorization model works over HTTP and not only in unit tests.
 *
 * <p>The published timetable has 24 lectures: CSE-A has 20 (12 theory, 4 lab for batch A1, 4 lab for batch A2) and ME-A has
 * 4 (theory). A theory lecture is for every batch of its division; a lab is for its one batch.
 */
class LiveTimetableApiIT extends TimetableApiTest {

	static final String RANGE = "from=2026-07-01&to=2026-07-31&size=100";

	@BeforeEach
	void publishTheTimetableAndGiveTeachersTheirRole() throws Exception {
		// Runs after the base class has built the campus (a superclass @BeforeEach runs first).
		publishStandardTimetable();
		assign(RAO_USER, "Faculty", null);
		assign(ROY_USER, "Faculty", null);
		assign(MEHTA_USER, "Faculty", null);
	}

	// --- what each role sees ----------------------------------------------------------------------------------------

	@Test
	@DisplayName("each role sees the lectures of the places it holds: department, division, batch, everything, or only its own")
	void whatEachRoleSees() throws Exception {
		assertThat(listed(admin())).as("the administrator sees every lecture").hasSize(24);
		assertThat(listed(user(personWith("Super HOD", null)))).as("a Super HOD: every department").hasSize(24);
		assertThat(listed(user(personWith("HOD", this.deptCse)))).as("an HOD: their department").hasSize(20);
		assertThat(listed(user(personWith("HOD", this.deptMe)))).as("an HOD of another department").hasSize(4);
		assertThat(listed(user(personWith("CR", this.divisionCseA)))).as("a CR: every lecture of the division").hasSize(20);
		assertThat(listed(user(personWith("BR", this.batchA1)))).as("a BR: the theory of the division and the lab of the batch")
			.hasSize(16);
		assertThat(listed(user(personWith("Student", this.batchA2)))).as("a student: the same, for their batch").hasSize(16);
		assertThat(listed(user(personWith("Student", this.batchMeA1)))).as("a student of another division").hasSize(4);
		assertThat(listed(user(RAO_USER))).as("a teacher: every lecture of the division they teach in").hasSize(20);
		assertThat(listed(user(ROY_USER))).as("a colleague in the same division sees the same").hasSize(20);
		assertThat(listed(user(MEHTA_USER))).as("a teacher of another division sees only that one").hasSize(4);
		assertThat(listed(user(somebody()))).as("a person with no role sees nothing").isEmpty();
	}

	@Test
	@DisplayName("a student sees the theory of the division and the lab of their own batch, and not the lab of another batch")
	void aStudentSeesTheirBatchAndTheDivisionsTheory() throws Exception {
		UUID student = personWith("Student", this.batchA1);

		get(user(student), "/api/v1/timetable/live?" + RANGE).andExpect(status().isOk())
			.andExpect(jsonPath("$.page.totalElements").value(16))
			.andExpect(jsonPath("$.items[?(@.sessionType == 'LAB' && @.batchId == '" + this.batchA1 + "')]").isNotEmpty())
			.andExpect(jsonPath("$.items[?(@.sessionType == 'LAB' && @.batchId == '" + this.batchA2 + "')]").isEmpty())
			.andExpect(jsonPath("$.items[?(@.divisionId == '" + this.divisionMeA + "')]").isEmpty());
	}

	@Test
	@DisplayName("a lecture is shown with what a timetable needs: when, what, who, where and for whom")
	void aLectureIsShownInFull() throws Exception {
		get(admin(), "/api/v1/timetable/live?from=2026-07-06&to=2026-07-06&division=" + this.divisionCseA + "&size=100")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page.totalElements").value(2))
			.andExpect(jsonPath("$.items[0].date").value("2026-07-06"))
			.andExpect(jsonPath("$.items[0].startTime").value("09:00:00"))
			.andExpect(jsonPath("$.items[0].endTime").value("10:00:00"))
			.andExpect(jsonPath("$.items[0].subjectCode").value("OOP"))
			.andExpect(jsonPath("$.items[0].facultyName").value("Dr Rao"))
			.andExpect(jsonPath("$.items[0].roomCode").value("R101"))
			.andExpect(jsonPath("$.items[0].divisionCode").value("CSE-A"))
			.andExpect(jsonPath("$.items[0].sessionType").value("THEORY"))
			.andExpect(jsonPath("$.items[0].batchId").doesNotExist())
			.andExpect(jsonPath("$.items[1].startTime").value("10:00:00"));

		get(admin(), "/api/v1/timetable/live?from=2026-07-08&to=2026-07-08&size=100").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].mode").value("ONLINE"))
			.andExpect(jsonPath("$.items[0].roomId").doesNotExist())
			.andExpect(jsonPath("$.items[0].meetingLink").value("https://meet.example/abc"));
	}

	// --- the list and the single record agree -----------------------------------------------------------------------

	@Test
	@DisplayName("the list and the single-lecture decision agree for every person and every lecture, so a list never shows what a lecture would refuse, nor hides what it would allow")
	void listAgreesWithRecords() throws Exception {
		Set<String> every = listed(admin());
		Map<String, RequestPostProcessor> people = new LinkedHashMap<>();
		people.put("admin", admin());
		people.put("super hod", user(personWith("Super HOD", null)));
		people.put("hod cse", user(personWith("HOD", this.deptCse)));
		people.put("hod me", user(personWith("HOD", this.deptMe)));
		people.put("cr cse-a", user(personWith("CR", this.divisionCseA)));
		people.put("cr me-a", user(personWith("CR", this.divisionMeA)));
		people.put("br a1", user(personWith("BR", this.batchA1)));
		people.put("br a2", user(personWith("BR", this.batchA2)));
		people.put("student a1", user(personWith("Student", this.batchA1)));
		people.put("student me-a1", user(personWith("Student", this.batchMeA1)));
		people.put("rao", user(RAO_USER));
		people.put("roy", user(ROY_USER));
		people.put("mehta", user(MEHTA_USER));
		people.put("nobody", user(somebody()));
		// A person with two roles sees the union, not one of them.
		UUID both = personWith("HOD", this.deptMe);
		assign(both, "BR", this.batchA1);
		people.put("hod me and br a1", user(both));

		for (Map.Entry<String, RequestPostProcessor> person : people.entrySet()) {
			Set<String> expected = new HashSet<>();
			for (String id : every) {
				int status = get(person.getValue(), "/api/v1/timetable/live/" + id).andReturn().getResponse().getStatus();
				assertThat(status).as(person.getKey() + " on " + id + " is either allowed or not found").isIn(200, 404);
				if (status == 200) {
					expected.add(id);
				}
			}
			assertThat(listed(person.getValue())).as("the list of " + person.getKey()).isEqualTo(expected);
		}
	}

	@Test
	@DisplayName("a lecture the caller may not see is answered as not found, so its existence is not revealed")
	void invisibleIsNotFound() throws Exception {
		String cseLecture = firstLectureOf(this.divisionCseA);
		UUID hodMe = personWith("HOD", this.deptMe);

		get(user(hodMe), "/api/v1/timetable/live/" + cseLecture).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("timetable.lecture_not_found"));
		get(user(personWith("HOD", this.deptCse)), "/api/v1/timetable/live/" + cseLecture).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(cseLecture));
		get(admin(), "/api/v1/timetable/live/" + UUID.randomUUID()).andExpect(status().isNotFound());
	}

	// --- changes apply at once --------------------------------------------------------------------------------------

	@Test
	@DisplayName("a role removed stops reaching on the next request, and a role in a second place adds it")
	void roleChangesApplyAtOnce() throws Exception {
		UUID hod = personWith("HOD", this.deptCse);
		assertThat(listed(user(hod))).hasSize(20);

		assign(hod, "HOD", this.deptMe);
		assertThat(listed(user(hod))).as("both departments").hasSize(24);

		unassign(hod, "HOD", this.deptCse);
		assertThat(listed(user(hod))).as("only department ME").hasSize(4);

		unassign(hod, "HOD", this.deptMe);
		assertThat(listed(user(hod))).isEmpty();
	}

	@Test
	@DisplayName("a batch added to a division after the timetable was published sees its theory at once, because the batches are looked up live")
	void aNewBatchSeesTheTheory() throws Exception {
		String lateBatch = create("/api/v1/batches",
				"{\"code\":\"CSE-A3\",\"name\":\"A3\",\"divisionId\":\"" + this.divisionCseA + "\"}");
		UUID student = personWith("Student", lateBatch);

		// The theory of the division (12 lectures) and no lab, since no lab is for this batch.
		assertThat(listed(user(student))).hasSize(12);
	}

	// --- filters and limits -----------------------------------------------------------------------------------------

	@Test
	@DisplayName("the list can be narrowed by division, by batch (which includes the division's theory) and by teacher")
	void filters() throws Exception {
		get(admin(), "/api/v1/timetable/live?" + RANGE + "&division=" + this.divisionMeA).andExpect(status().isOk())
			.andExpect(jsonPath("$.page.totalElements").value(4));
		get(admin(), "/api/v1/timetable/live?" + RANGE + "&batch=" + this.batchA1).andExpect(status().isOk())
			.andExpect(jsonPath("$.page.totalElements").value(16));
		get(admin(), "/api/v1/timetable/live?" + RANGE + "&faculty=" + this.facultyRao).andExpect(status().isOk())
			.andExpect(jsonPath("$.page.totalElements").value(8));
		get(admin(), "/api/v1/timetable/live?from=2026-07-07&to=2026-07-07&size=100").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].sessionType", hasItem("LAB")))
			.andExpect(jsonPath("$.page.totalElements").value(2));
	}

	@Test
	@DisplayName("a filter cannot widen what the caller may see")
	void aFilterCannotWiden() throws Exception {
		UUID hodMe = personWith("HOD", this.deptMe);

		get(user(hodMe), "/api/v1/timetable/live?" + RANGE + "&division=" + this.divisionCseA).andExpect(status().isOk())
			.andExpect(jsonPath("$.page.totalElements").value(0));
	}

	@Test
	@DisplayName("the list is paged after it is filtered, so a page and its total are right")
	void pagesAfterFiltering() throws Exception {
		UUID br = personWith("BR", this.batchA1);

		get(user(br), "/api/v1/timetable/live?from=2026-07-01&to=2026-07-31&size=5&page=0").andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(5))
			.andExpect(jsonPath("$.page.totalElements").value(16))
			.andExpect(jsonPath("$.page.totalPages").value(4));
		get(user(br), "/api/v1/timetable/live?from=2026-07-01&to=2026-07-31&size=5&page=3").andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	@DisplayName("a range must be given, run forwards and be no longer than 62 days")
	void theRangeIsChecked() throws Exception {
		get(admin(), "/api/v1/timetable/live").andExpect(status().isBadRequest());
		get(admin(), "/api/v1/timetable/live?from=2026-07-31&to=2026-07-01").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("before_from")));
		get(admin(), "/api/v1/timetable/live?from=2026-01-01&to=2026-12-31").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("range_too_long")));
	}

	@Test
	@DisplayName("a lecture a later version replaced is no longer shown, in the list or on its own, and the earlier ones stay")
	void supersededLecturesAreGone() throws Exception {
		// ME-A has its Thermodynamics on Mondays. From 13 July it moves to Tuesdays.
		String replaced = lectureOn(this.divisionMeA, "2026-07-13");
		String kept = lectureOn(this.divisionMeA, "2026-07-06");
		String later = draft(this.divisionMeA, "2026-07-13");
		entry(later, this.theoryThermo, 2, 9, 10, this.room201);
		publish(later);

		get(admin(), "/api/v1/timetable/live/" + replaced).andExpect(status().isNotFound());
		get(admin(), "/api/v1/timetable/live/" + kept).andExpect(status().isOk());
		get(admin(), "/api/v1/timetable/live?" + RANGE + "&division=" + this.divisionMeA).andExpect(status().isOk())
			.andExpect(jsonPath("$.page.totalElements").value(4))
			.andExpect(jsonPath("$.items[*].date", contains("2026-07-06", "2026-07-14", "2026-07-21", "2026-07-28")));
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	/** The IDs of the lectures the caller sees for the whole term. */
	private Set<String> listed(RequestPostProcessor who) throws Exception {
		String json = get(who, "/api/v1/timetable/live?" + RANGE).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		List<String> ids = JsonPath.read(json, "$.items[*].id");
		return new HashSet<>(ids);
	}

	/** The ID of the first lecture of the division on the date, as the administrator sees it. */
	private String lectureOn(String division, String date) throws Exception {
		String json = get(admin(), "/api/v1/timetable/live?from=" + date + "&to=" + date + "&division=" + division)
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(json, "$.items[*].id");
		return ids.get(0);
	}

	private String firstLectureOf(String division) throws Exception {
		return lectureOn(division, "2026-07-06");
	}

}
