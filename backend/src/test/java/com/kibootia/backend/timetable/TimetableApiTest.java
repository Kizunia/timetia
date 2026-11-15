package com.kibootia.backend.timetable;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.academic.application.RoleTemplateSeeder;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The base of the timetable tests: a small campus built through the real APIs, with the real scope directory (not a fake),
 * so what a test proves holds for the application as it runs. The campus:
 *
 * <ul>
 * <li>department CSE holds division CSE-A, which has batches CSE-A1 and CSE-A2;</li>
 * <li>department ME holds division ME-A, which has batch ME-A1;</li>
 * <li>teachers Rao and Roy (CSE) and Mehta (ME), each with a user account, and four rooms;</li>
 * <li>the term SEM3, Monday 2026-07-06 to Friday 2026-07-31, so each weekday falls four times.</li>
 * </ul>
 *
 * <p>Teaching assignments: for CSE-A, OOP theory by Rao, DBMS theory by Roy, OOP lab for batch A1 by Rao and OOP lab for
 * batch A2 by Roy (the two labs run in parallel); for ME-A, Thermodynamics theory by Mehta.
 */
public abstract class TimetableApiTest extends AdminApiTest {

	protected static final String TERM_START = "2026-07-06";

	protected static final String TERM_END = "2026-07-31";

	protected static final UUID RAO_USER = UUID.fromString("f0000000-0000-7000-8000-000000000001");

	protected static final UUID ROY_USER = UUID.fromString("f0000000-0000-7000-8000-000000000002");

	protected static final UUID MEHTA_USER = UUID.fromString("f0000000-0000-7000-8000-000000000003");

	@Autowired
	protected RoleTemplateSeeder seeder;

	protected String deptCse;

	protected String deptMe;

	protected String divisionCseA;

	protected String divisionMeA;

	protected String batchA1;

	protected String batchA2;

	protected String batchMeA1;

	protected String subjectOop;

	protected String subjectDbms;

	protected String subjectThermo;

	protected String facultyRao;

	protected String facultyRoy;

	protected String facultyMehta;

	protected String room101;

	protected String room102;

	protected String lab1;

	protected String room201;

	protected String term;

	protected String theoryOop;

	protected String theoryDbms;

	protected String labA1;

	protected String labA2;

	protected String theoryThermo;

	/** One @BeforeEach, so that cleaning always runs before building (JUnit does not order several). */
	@BeforeEach
	void buildCampus() throws Exception {
		clean();
		this.seeder.seed();
		signIn(RAO_USER);
		signIn(ROY_USER);
		signIn(MEHTA_USER);

		this.deptCse = create("/api/v1/departments", "{\"code\":\"CSE\",\"name\":\"Computer Science\"}");
		this.deptMe = create("/api/v1/departments", "{\"code\":\"ME\",\"name\":\"Mechanical\"}");
		this.divisionCseA = create("/api/v1/divisions",
				"{\"code\":\"CSE-A\",\"name\":\"SY-CSSE-A\",\"departmentId\":\"" + this.deptCse + "\"}");
		this.divisionMeA = create("/api/v1/divisions",
				"{\"code\":\"ME-A\",\"name\":\"SY-ME-A\",\"departmentId\":\"" + this.deptMe + "\"}");
		this.batchA1 = create("/api/v1/batches",
				"{\"code\":\"CSE-A1\",\"name\":\"A1\",\"divisionId\":\"" + this.divisionCseA + "\"}");
		this.batchA2 = create("/api/v1/batches",
				"{\"code\":\"CSE-A2\",\"name\":\"A2\",\"divisionId\":\"" + this.divisionCseA + "\"}");
		this.batchMeA1 = create("/api/v1/batches",
				"{\"code\":\"ME-A1\",\"name\":\"A1\",\"divisionId\":\"" + this.divisionMeA + "\"}");

		this.subjectOop = create("/api/v1/subjects", "{\"code\":\"OOP\",\"name\":\"Object-Oriented Programming\"}");
		this.subjectDbms = create("/api/v1/subjects", "{\"code\":\"DBMS\",\"name\":\"Database Systems\"}");
		this.subjectThermo = create("/api/v1/subjects", "{\"code\":\"THERMO\",\"name\":\"Thermodynamics\"}");

		this.facultyRao = create("/api/v1/faculties", "{\"code\":\"RAO\",\"name\":\"Dr Rao\",\"departmentId\":\"" + this.deptCse
				+ "\",\"userId\":\"" + RAO_USER + "\"}");
		this.facultyRoy = create("/api/v1/faculties", "{\"code\":\"ROY\",\"name\":\"Dr Roy\",\"departmentId\":\"" + this.deptCse
				+ "\",\"userId\":\"" + ROY_USER + "\"}");
		this.facultyMehta = create("/api/v1/faculties", "{\"code\":\"MEHTA\",\"name\":\"Dr Mehta\",\"departmentId\":\""
				+ this.deptMe + "\",\"userId\":\"" + MEHTA_USER + "\"}");

		this.room101 = create("/api/v1/rooms", "{\"code\":\"R101\",\"name\":\"Room 101\",\"roomType\":\"CLASSROOM\"}");
		this.room102 = create("/api/v1/rooms", "{\"code\":\"R102\",\"name\":\"Room 102\",\"roomType\":\"CLASSROOM\"}");
		this.lab1 = create("/api/v1/rooms", "{\"code\":\"LAB1\",\"name\":\"Lab 1\",\"roomType\":\"LAB\",\"capacity\":30}");
		this.room201 = create("/api/v1/rooms", "{\"code\":\"R201\",\"name\":\"Room 201\",\"roomType\":\"CLASSROOM\"}");

		this.term = create("/api/v1/academic-terms",
				"{\"code\":\"SEM3\",\"name\":\"Semester 3\",\"startDate\":\"" + TERM_START + "\",\"endDate\":\"" + TERM_END + "\"}");

		this.theoryOop = assignment(this.divisionCseA, null, this.subjectOop, this.facultyRao, "THEORY");
		this.theoryDbms = assignment(this.divisionCseA, null, this.subjectDbms, this.facultyRoy, "THEORY");
		this.labA1 = assignment(this.divisionCseA, this.batchA1, this.subjectOop, this.facultyRao, "LAB");
		this.labA2 = assignment(this.divisionCseA, this.batchA2, this.subjectOop, this.facultyRoy, "LAB");
		this.theoryThermo = assignment(this.divisionMeA, null, this.subjectThermo, this.facultyMehta, "THEORY");
	}

	@AfterEach
	void tearDownCampus() {
		clean();
	}

	private void clean() {
		this.jdbc.update("delete from conflict_finding");
		this.jdbc.update("delete from conflict_check_run");
		this.jdbc.update("delete from ticket_change_item");
		this.jdbc.update("delete from timetable_change_ticket");
		this.jdbc.update("delete from occurrence_claim");
		this.jdbc.update("delete from lecture_occurrence");
		this.jdbc.update("delete from timetable_entry");
		this.jdbc.update("delete from timetable_version");
		this.jdbc.update("delete from teaching_assignment");
		this.jdbc.update("delete from faculty");
		this.jdbc.update("delete from subject");
		this.jdbc.update("delete from room");
		this.jdbc.update("delete from academic_term");
		this.jdbc.update("delete from batch");
		this.jdbc.update("delete from division");
		this.jdbc.update("delete from department");
	}

	// --- building the timetable --------------------------------------------------------------------------------------

	/** Creates a draft for the division in the term, taking effect on the date, and returns its ID. */
	protected String draft(String division, String effectiveFrom) throws Exception {
		return create("/api/v1/timetable/versions",
				"{\"divisionId\":\"" + division + "\",\"termId\":\"" + this.term + "\",\"effectiveFrom\":\"" + effectiveFrom + "\"}");
	}

	/** Adds an in-person lecture to a draft and returns the entry's ID. */
	protected String entry(String version, String assignment, int day, int fromHour, int toHour, String room) throws Exception {
		return create("/api/v1/timetable/versions/" + version + "/entries", entryBody(assignment, day, fromHour, toHour, room));
	}

	protected static String entryBody(String assignment, int day, int fromHour, int toHour, String room) {
		return "{\"assignmentId\":\"" + assignment + "\",\"dayOfWeek\":" + day + ",\"startTime\":\"" + hour(fromHour)
				+ "\",\"endTime\":\"" + hour(toHour) + "\",\"mode\":\"IN_PERSON\",\"roomId\":\"" + room + "\"}";
	}

	protected static String onlineEntryBody(String assignment, int day, int fromHour, int toHour) {
		return "{\"assignmentId\":\"" + assignment + "\",\"dayOfWeek\":" + day + ",\"startTime\":\"" + hour(fromHour)
				+ "\",\"endTime\":\"" + hour(toHour) + "\",\"mode\":\"ONLINE\",\"meetingLink\":\"https://meet.example/abc\"}";
	}

	protected static String hour(int hour) {
		return String.format("%02d:00", hour);
	}

	protected void publish(String version) throws Exception {
		post(admin(), "/api/v1/timetable/versions/" + version + "/publish", null).andExpect(status().isOk());
	}

	/**
	 * Publishes the standard timetable of both divisions, effective from the start of the term. CSE-A: Monday 9-10 OOP theory
	 * (R101), Monday 10-11 DBMS theory (R101), Tuesday 9-11 the two labs in parallel (LAB1 and R102), Wednesday 14-15 DBMS
	 * online. ME-A: Monday 9-10 Thermodynamics (R201). Each weekday falls four times in the term, so CSE-A has 20 lectures
	 * and ME-A has 4.
	 */
	protected void publishStandardTimetable() throws Exception {
		String cse = draft(this.divisionCseA, TERM_START);
		entry(cse, this.theoryOop, 1, 9, 10, this.room101);
		entry(cse, this.theoryDbms, 1, 10, 11, this.room101);
		entry(cse, this.labA1, 2, 9, 11, this.lab1);
		entry(cse, this.labA2, 2, 9, 11, this.room102);
		post(admin(), "/api/v1/timetable/versions/" + cse + "/entries", onlineEntryBody(this.theoryDbms, 3, 14, 15))
			.andExpect(status().isCreated());
		publish(cse);

		String me = draft(this.divisionMeA, TERM_START);
		entry(me, this.theoryThermo, 1, 9, 10, this.room201);
		publish(me);
	}

	protected String assignment(String division, String batch, String subject, String faculty, String sessionType) throws Exception {
		String batchField = (batch == null) ? "" : ",\"batchId\":\"" + batch + "\"";
		return create("/api/v1/teaching-assignments", "{\"divisionId\":\"" + division + "\",\"subjectId\":\"" + subject
				+ "\",\"facultyId\":\"" + faculty + "\",\"sessionType\":\"" + sessionType + "\"" + batchField + "}");
	}

	/** POSTs as the administrator, expects 201 and returns the new resource's ID. */
	protected String create(String path, String body) throws Exception {
		return read(post(admin(), path, body).andExpect(status().isCreated()), "$.id");
	}

	// --- people ------------------------------------------------------------------------------------------------------

	private int people;

	/** A signed-in person with no role beyond the default one. */
	protected UUID somebody() throws Exception {
		this.people++;
		return signIn(UUID.fromString(String.format("e0000000-0000-7000-8000-%012d", this.people)));
	}

	/** A person given the role in a place by the administrator, as the screen does (no place for a global role). */
	protected UUID personWith(String role, String place) throws Exception {
		UUID person = somebody();
		assign(person, role, place);
		return person;
	}

	protected void assign(UUID person, String role, String place) throws Exception {
		post(admin(), "/api/v1/users/" + person + "/scoped-roles",
				"{\"roleId\":\"" + roleId(role) + "\"" + ((place == null) ? "" : ",\"scopeId\":\"" + place + "\"") + "}")
			.andExpect(status().isOk());
	}

	protected void unassign(UUID person, String role, String place) throws Exception {
		delete(admin(), "/api/v1/users/" + person + "/scoped-roles/" + roleId(role) + ((place == null) ? "" : "?scopeId=" + place))
			.andExpect(status().isOk());
	}

	private String roleId(String name) throws Exception {
		String json = get(admin(), "/api/v1/roles?size=100").andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(json, "$.items[?(@.name == '" + name + "')].id");
		return ids.get(0);
	}

}
