package com.kibootia.backend.timetable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.kibootia.backend.platform.persistence.UuidBytes;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The timetable's {@link LectureCalendar} with the real database: reading a lecture as it is now, moving and cancelling
 * lectures, and the rule that keeps a teacher, a room or students from being booked twice. The campus and the published
 * timetable are those of {@link TimetableApiTest}: CSE-A has OOP theory on Monday 9-10 and DBMS theory on Monday 10-11 (both in
 * R101), the two labs on Tuesday 9-11 and DBMS online on Wednesday 14-15, each on four dates; ME-A has Thermodynamics on
 * Monday 9-10 in R201. The standard timetable holds 120 claims.
 */
class LectureCalendarIT extends TimetableApiTest {

	private static final int STANDARD_CLAIMS = 120;

	@Autowired
	LectureCalendar calendar;

	@BeforeEach
	void publishTheStandardTimetable() throws Exception {
		publishStandardTimetable();
	}

	// --- reading a lecture ------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a lecture is read with where it is, who it is for and who teaches it, as the policy needs to know")
	void aLectureIsReadAsItIsNow() {
		LectureView lecture = view(occurrence(this.theoryOop, "2026-07-06"));

		assertThat(lecture.slot().date()).isEqualTo(LocalDate.of(2026, 7, 6));
		assertThat(lecture.slot().startTime()).isEqualTo(LocalTime.of(9, 0));
		assertThat(lecture.roomCode()).isEqualTo("R101");
		assertThat(lecture.subjectCode()).isEqualTo("OOP");
		assertThat(lecture.facultyName()).isEqualTo("Dr Rao");
		assertThat(lecture.divisionCode()).isEqualTo("CSE-A");
		assertThat(lecture.batchId()).as("a theory lecture is for the whole division").isNull();
		assertThat(lecture.batchIds()).as("so it is for every batch in it")
			.containsExactlyInAnyOrder(UUID.fromString(this.batchA1), UUID.fromString(this.batchA2));
		assertThat(lecture.teachingFacultyUserIds()).containsExactly(RAO_USER);
		assertThat(lecture.cancelled()).isFalse();
		assertThat(lecture.changed()).isFalse();
		assertThat(lecture.slot()).isEqualTo(lecture.baseSlot());
		// 09:00 on the campus clock, which is five and a half hours ahead of UTC.
		assertThat(lecture.startsAt()).isEqualTo(Instant.parse("2026-07-06T03:30:00Z"));
	}

	@Test
	@DisplayName("publishing a timetable makes each lecture hold its faculty member, room and students for every hour")
	void publishingHoldsClaims() {
		assertThat(count("select count(*) from occurrence_claim")).isEqualTo(STANDARD_CLAIMS);
		// A theory lecture holds the division and every batch in it; a lab only its own batch.
		assertThat(count("select count(*) from occurrence_claim where claim_kind = 'DIVISION'")).isEqualTo(12 + 4);
		// R101 for OOP and DBMS (4 + 4), a two hour lab in each of LAB1 and R102 (8 + 8), and R201 for Thermodynamics (4).
		assertThat(count("select count(*) from occurrence_claim where claim_kind = 'ROOM'")).isEqualTo(4 + 4 + 8 + 8 + 4);
	}

	// --- moving and cancelling --------------------------------------------------------------------------------------

	@Test
	@DisplayName("a lecture moved to a free slot is there now, its base slot is kept, and what it held moves with it")
	void aMoveIsLaidOverTheBase() {
		UUID oop = occurrence(this.theoryOop, "2026-07-06");
		LectureView before = view(oop);
		UUID ticket = UUID.randomUUID();
		List<LectureChange> move = List.of(LectureChange.move(oop, before.revision(), slot("2026-07-09", 9, 10, this.room101)));

		assertThat(check(move)).isEmpty();
		apply(move, ticket);

		LectureView after = view(oop);
		assertThat(after.slot().date()).isEqualTo(LocalDate.of(2026, 7, 9));
		assertThat(after.baseSlot().date()).as("the base timetable is not edited").isEqualTo(LocalDate.of(2026, 7, 6));
		assertThat(after.changed()).isTrue();
		assertThat(after.changedByTicketId()).isEqualTo(ticket);
		assertThat(after.revision()).isGreaterThan(before.revision());
		assertThat(count("select count(*) from occurrence_claim")).as("the same things are held, on another day")
			.isEqualTo(STANDARD_CLAIMS);

		// The slot it left is free again: the lecture after it can move up into it.
		UUID dbms = occurrence(this.theoryDbms, "2026-07-06");
		assertThat(check(List.of(LectureChange.move(dbms, view(dbms).revision(), slot("2026-07-06", 9, 10, this.room101)))))
			.isEmpty();
	}

	@Test
	@DisplayName("a cancelled lecture is kept and marked, holds nothing, and frees its slot")
	void aCancelledLectureHoldsNothing() {
		UUID oop = occurrence(this.theoryOop, "2026-07-06");
		apply(List.of(LectureChange.cancel(oop, view(oop).revision())), UUID.randomUUID());

		LectureView cancelled = view(oop);
		assertThat(cancelled.cancelled()).isTrue();
		assertThat(cancelled.slot()).as("it stays where it was, marked").isEqualTo(cancelled.baseSlot());
		assertThat(count("select count(*) from occurrence_claim")).isEqualTo(STANDARD_CLAIMS - 5);

		UUID dbms = occurrence(this.theoryDbms, "2026-07-06");
		List<LectureChange> intoTheFreedSlot = List
			.of(LectureChange.move(dbms, view(dbms).revision(), slot("2026-07-06", 9, 10, this.room101)));
		assertThat(check(intoTheFreedSlot)).isEmpty();
		apply(intoTheFreedSlot, UUID.randomUUID());

		// A cancelled lecture cannot be changed again.
		assertThat(check(List.of(LectureChange.cancel(oop, cancelled.revision())))).extracting(LectureConflict::type)
			.containsExactly(ConflictType.OCCURRENCE_CHANGED);
	}

	// --- what stands in the way -------------------------------------------------------------------------------------

	@Test
	@DisplayName("a slot another lecture holds is refused, and each conflict names what is in the way")
	void conflictsAreNamedByWhatIsInTheWay() {
		// OOP onto DBMS's slot: the same room, and the same division.
		UUID oop = occurrence(this.theoryOop, "2026-07-06");
		List<LectureConflict> sameRoom = check(
				List.of(LectureChange.move(oop, view(oop).revision(), slot("2026-07-06", 10, 11, this.room101))));
		assertThat(sameRoom).extracting(LectureConflict::type)
			.containsExactlyInAnyOrder(ConflictType.ROOM_OCCUPIED, ConflictType.DIVISION_BUSY);
		assertThat(sameRoom).allSatisfy((conflict) -> assertThat(conflict.message()).isNotBlank());
		assertThat(sameRoom).filteredOn((conflict) -> conflict.type() == ConflictType.ROOM_OCCUPIED).singleElement()
			.satisfies((conflict) -> assertThat(conflict.message()).contains("R101"));

		// DBMS theory onto Tuesday 9-10, when both labs run: Roy is in a lab, and the labs hold the division's batches.
		UUID dbms = occurrence(this.theoryDbms, "2026-07-06");
		List<LectureConflict> overLabs = check(
				List.of(LectureChange.move(dbms, view(dbms).revision(), slot("2026-07-07", 9, 10, this.room201))));
		assertThat(overLabs).extracting(LectureConflict::type).contains(ConflictType.FACULTY_BUSY, ConflictType.DIVISION_BUSY)
			.doesNotContain(ConflictType.ROOM_OCCUPIED);

		// A lab onto Monday morning, when the whole division has theory, and in the room Thermodynamics has.
		UUID lab = occurrence(this.labA1, "2026-07-07");
		List<LectureConflict> labOverTheory = check(
				List.of(LectureChange.move(lab, view(lab).revision(), slot("2026-07-06", 9, 11, this.room201))));
		assertThat(labOverTheory).extracting(LectureConflict::type)
			.contains(ConflictType.ROOM_OCCUPIED, ConflictType.FACULTY_BUSY, ConflictType.BATCH_BUSY);

		assertThat(count("select count(*) from occurrence_claim")).as("checking writes nothing").isEqualTo(STANDARD_CLAIMS);
	}

	@Test
	@DisplayName("a lecture another ticket moved into a slot is reported as a clash of tickets, with that ticket")
	void anotherTicketsLectureIsASameSlotClash() {
		UUID oop = occurrence(this.theoryOop, "2026-07-06");
		UUID firstTicket = UUID.randomUUID();
		apply(List.of(LectureChange.move(oop, view(oop).revision(), slot("2026-07-09", 9, 10, this.room101))), firstTicket);

		UUID dbms = occurrence(this.theoryDbms, "2026-07-06");
		List<LectureConflict> conflicts = check(
				List.of(LectureChange.move(dbms, view(dbms).revision(), slot("2026-07-09", 9, 10, this.room101))));

		assertThat(conflicts).extracting(LectureConflict::type).contains(ConflictType.SAME_SLOT_TICKET);
		assertThat(conflicts).filteredOn((conflict) -> conflict.type() == ConflictType.SAME_SLOT_TICKET)
			.allSatisfy((conflict) -> {
				assertThat(conflict.conflictingTicketId()).isEqualTo(firstTicket);
				assertThat(conflict.conflictingOccurrenceId()).isEqualTo(oop);
			});
	}

	@Test
	@DisplayName("a lecture cannot go to a Sunday, outside the term, or off the hour")
	void theCalendarIsRespected() {
		UUID oop = occurrence(this.theoryOop, "2026-07-06");
		long revision = view(oop).revision();

		assertThat(check(List.of(LectureChange.move(oop, revision, slot("2026-07-12", 9, 10, this.room102)))))
			.extracting(LectureConflict::type)
			.containsExactly(ConflictType.OUTSIDE_CALENDAR);
		assertThat(check(List.of(LectureChange.move(oop, revision, slot("2026-08-03", 9, 10, this.room102)))))
			.extracting(LectureConflict::type)
			.containsExactly(ConflictType.OUTSIDE_CALENDAR);
		LectureSlot halfPast = new LectureSlot(LocalDate.of(2026, 7, 9), LocalTime.of(9, 30), LocalTime.of(10, 30),
				LectureMode.IN_PERSON, UUID.fromString(this.room102), null);
		assertThat(check(List.of(LectureChange.move(oop, revision, halfPast)))).extracting(LectureConflict::type)
			.containsExactly(ConflictType.OUTSIDE_SLOT_GRID);
		assertThat(check(List.of(LectureChange.move(oop, revision, slot("2026-07-09", 19, 21, this.room102)))))
			.extracting(LectureConflict::type)
			.containsExactly(ConflictType.OUTSIDE_SLOT_GRID);
	}

	@Test
	@DisplayName("a change planned against an old state of the lecture is refused")
	void aStaleChangeIsRefused() {
		UUID oop = occurrence(this.theoryOop, "2026-07-06");
		long planned = view(oop).revision();
		apply(List.of(LectureChange.move(oop, planned, slot("2026-07-09", 9, 10, this.room101))), UUID.randomUUID());

		assertThat(check(List.of(LectureChange.cancel(oop, planned)))).singleElement()
			.satisfies((conflict) -> assertThat(conflict.type()).isEqualTo(ConflictType.OCCURRENCE_CHANGED));
		assertThatThrownBy(() -> apply(List.of(LectureChange.cancel(oop, planned)), UUID.randomUUID()))
			.isInstanceOf(ChangeConflictException.class);
		assertThat(view(oop).cancelled()).isFalse();
	}

	// --- several lectures together ----------------------------------------------------------------------------------

	@Test
	@DisplayName("two lectures can exchange slots, because each frees the slot the other needs")
	void lecturesChangedTogetherCanSwap() {
		UUID oop = occurrence(this.theoryOop, "2026-07-06");
		UUID dbms = occurrence(this.theoryDbms, "2026-07-06");
		LectureView oopBefore = view(oop);
		LectureView dbmsBefore = view(dbms);
		// Each alone is blocked by the other.
		assertThat(check(List.of(LectureChange.move(oop, oopBefore.revision(), dbmsBefore.slot())))).isNotEmpty();

		List<LectureChange> swap = List.of(LectureChange.move(oop, oopBefore.revision(), dbmsBefore.slot()),
				LectureChange.move(dbms, dbmsBefore.revision(), oopBefore.slot()));
		assertThat(check(swap)).isEmpty();
		apply(swap, UUID.randomUUID());

		assertThat(view(oop).slot().startTime()).isEqualTo(LocalTime.of(10, 0));
		assertThat(view(dbms).slot().startTime()).isEqualTo(LocalTime.of(9, 0));
		assertThat(count("select count(*) from occurrence_claim")).isEqualTo(STANDARD_CLAIMS);
	}

	@Test
	@DisplayName("two lectures changed together cannot both need the same slot, and nothing is written")
	void twoChangesCannotNeedTheSameSlot() {
		UUID first = occurrence(this.theoryOop, "2026-07-06");
		UUID second = occurrence(this.theoryOop, "2026-07-13");
		List<LectureChange> both = List.of(
				LectureChange.move(first, view(first).revision(), slot("2026-07-08", 9, 10, this.room101)),
				LectureChange.move(second, view(second).revision(), slot("2026-07-08", 9, 10, this.room101)));

		assertThat(check(both)).isNotEmpty();
		assertThatThrownBy(() -> apply(both, UUID.randomUUID())).isInstanceOf(ChangeConflictException.class);
		assertThat(view(first).changed()).isFalse();
		assertThat(view(second).changed()).isFalse();
		assertThat(count("select count(*) from occurrence_claim")).isEqualTo(STANDARD_CLAIMS);
	}

	// --- racing -----------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("when two changes race for one slot, one is made and the other is refused, and nothing is booked twice")
	void onlyOneOfTwoRacingChangesWins() throws Exception {
		UUID first = occurrence(this.theoryOop, "2026-07-06");
		UUID second = occurrence(this.theoryDbms, "2026-07-06");
		long firstRevision = view(first).revision();
		long secondRevision = view(second).revision();
		// Both want Thursday 11-12 in R102: the same room and the same division, so they cannot both have it.
		LectureSlot thursday = slot("2026-07-09", 11, 12, this.room102);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch start = new CountDownLatch(1);
			Future<Boolean> a = pool.submit(raceFor(start, LectureChange.move(first, firstRevision, thursday)));
			Future<Boolean> b = pool.submit(raceFor(start, LectureChange.move(second, secondRevision, thursday)));
			start.countDown();

			int won = (a.get(60, TimeUnit.SECONDS) ? 1 : 0) + (b.get(60, TimeUnit.SECONDS) ? 1 : 0);
			assertThat(won).as("exactly one of the two changes is made").isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(count("select count(*) from occurrence_claim where claim_kind = 'ROOM' and resource_id = unhex(?) "
				+ "and claim_date = '2026-07-09' and cell_hour = 11", hex(UUID.fromString(this.room102)))).isEqualTo(1);
		assertThat(count("select count(*) from occurrence_claim")).as("what was held is still held, once").isEqualTo(STANDARD_CLAIMS);
		assertThat(List.of(view(first).changed(), view(second).changed())).containsExactlyInAnyOrder(true, false);
	}

	private Callable<Boolean> raceFor(CountDownLatch start, LectureChange change) {
		return () -> {
			start.await();
			try {
				// The check can pass for both, because neither has taken the slot yet. The database decides.
				TenantContext.runAs(TenantContext.DEFAULT_TENANT, () -> this.calendar.apply(List.of(change), UUID.randomUUID()));
				return true;
			}
			catch (RuntimeException ex) {
				return false;
			}
		};
	}

	// --- with the rest of the system --------------------------------------------------------------------------------

	@Test
	@DisplayName("the live timetable shows a moved lecture where it is now with where it was, and a cancelled one marked")
	void theLiveTimetableShowsTheChanges() throws Exception {
		UUID oop = occurrence(this.theoryOop, "2026-07-06");
		UUID dbms = occurrence(this.theoryDbms, "2026-07-06");
		apply(List.of(LectureChange.move(oop, view(oop).revision(), slot("2026-07-09", 9, 10, this.room101))), UUID.randomUUID());
		apply(List.of(LectureChange.cancel(dbms, view(dbms).revision())), UUID.randomUUID());
		String student = "/api/v1/timetable/live?from=2026-07-01&to=2026-07-31&size=100";

		get(user(personWith("Student", this.batchA1)), student).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id == '" + oop + "')].date").value(hasItem("2026-07-09")))
			.andExpect(jsonPath("$.items[?(@.id == '" + oop + "')].changed").value(hasItem(true)))
			.andExpect(jsonPath("$.items[?(@.id == '" + oop + "')].originalDate").value(hasItem("2026-07-06")))
			.andExpect(jsonPath("$.items[?(@.id == '" + dbms + "')].cancelled").value(hasItem(true)))
			.andExpect(jsonPath("$.items[?(@.id == '" + dbms + "')].date").value(hasItem("2026-07-06")));
		// The ticket is not shown to anyone who reads the timetable.
		assertThat(get(admin(), "/api/v1/timetable/live/" + oop).andReturn().getResponse().getContentAsString())
			.doesNotContain("ticket");
	}

	@Test
	@DisplayName("a new version cannot silently discard a change that was applied to a lecture it would replace")
	void aVersionCannotDiscardAppliedChanges() throws Exception {
		UUID lecture = occurrence(this.theoryOop, "2026-07-13");
		apply(List.of(LectureChange.cancel(lecture, view(lecture).revision())), UUID.randomUUID());

		String later = draft(this.divisionCseA, "2026-07-13");
		entry(later, this.theoryDbms, 5, 14, 15, this.room101);
		post(admin(), "/api/v1/timetable/versions/" + later + "/publish", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("timetable.changes_would_be_lost"));
		assertThat(count("select count(*) from lecture_occurrence where record_status = 'SUPERSEDED'")).isZero();
		assertThat(view(lecture).cancelled()).isTrue();

		// A version that starts after the changed lecture replaces nothing it changed.
		String afterIt = draft(this.divisionCseA, "2026-07-14");
		entry(afterIt, this.theoryDbms, 5, 14, 15, this.room101);
		publish(afterIt);
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private <T> T asTenant(Callable<T> work) {
		return TenantContext.callAs(TenantContext.DEFAULT_TENANT, work);
	}

	private LectureView view(UUID occurrence) {
		return asTenant(() -> this.calendar.lecture(occurrence).orElseThrow());
	}

	private List<LectureConflict> check(List<LectureChange> changes) {
		return asTenant(() -> this.calendar.check(changes));
	}

	private void apply(List<LectureChange> changes, UUID ticket) {
		TenantContext.runAs(TenantContext.DEFAULT_TENANT, () -> this.calendar.apply(changes, ticket));
	}

	/** The lecture of the teaching assignment on the date, as the base timetable made it. */
	private UUID occurrence(String assignment, String date) {
		byte[] id = this.jdbc.queryForObject(
				"select id from lecture_occurrence where assignment_id = unhex(?) and occurrence_date = ?", byte[].class,
				hex(UUID.fromString(assignment)), date);
		return UuidBytes.fromBytes(id);
	}

	private static LectureSlot slot(String date, int from, int to, String room) {
		return new LectureSlot(LocalDate.parse(date), LocalTime.of(from, 0), LocalTime.of(to, 0), LectureMode.IN_PERSON,
				UUID.fromString(room), null);
	}

}
