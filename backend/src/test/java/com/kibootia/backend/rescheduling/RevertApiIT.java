package com.kibootia.backend.rescheduling;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reverts, through the real stack: an applied change is undone by a new ticket that restores each lecture to where, and with
 * whom, it was. The author, an affected faculty member and the HOD of the place may revert, until the lecture starts, and a
 * revert is checked like any change.
 *
 * <p>Rao teaches OOP theory on Monday 9-10, Roy DBMS theory on Monday 10-11, both in R101 and CSE-A; Thursday is free.
 */
class RevertApiIT extends ReschedulingApiTest {

	// --- undoing each kind of change --------------------------------------------------------------------------------

	@Test
	@DisplayName("a reschedule is undone: the lecture comes back to its slot, the claims follow, and the original shows as reverted")
	void revertingAReschedule() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String moved = raise(RAO_USER, move(oop, "2026-07-09", 9, 10, this.room101));
		applyOk(RAO_USER, moved);

		String revert = revertOk(RAO_USER, moved);
		get(user(RAO_USER), "/api/v1/tickets/" + revert).andExpect(jsonPath("$.type").value("REVERT"))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.revertsTicketId").value(moved))
			.andExpect(jsonPath("$.conflicted").value(false))
			.andExpect(jsonPath("$.change.operation").value("RESTORE"))
			.andExpect(jsonPath("$.change.target.date").value("2026-07-06"))
			.andExpect(jsonPath("$.change.target.startTime").value("09:00:00"));
		// Nothing is undone until the revert is applied.
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.date").value("2026-07-09"));

		post(user(RAO_USER), "/api/v1/tickets/" + revert + "/apply", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.approvalPath").value("SELF"));

		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.date").value("2026-07-06"))
			.andExpect(jsonPath("$.startTime").value("09:00:00"))
			.andExpect(jsonPath("$.changed").value(false));
		assertThat(count("select count(*) from occurrence_claim where occurrence_id = unhex('" + hex(UUID.fromString(oop))
				+ "') and claim_date = '2026-07-06'")).isEqualTo(5);
		assertThat(count("select count(*) from occurrence_claim where claim_date = '2026-07-09'")).isZero();
		get(user(RAO_USER), "/api/v1/tickets/" + moved).andExpect(jsonPath("$.status").value("REVERTED"))
			.andExpect(jsonPath("$.revertedByTicketId").value(revert));
		assertThat(count("select count(*) from audit_entry where action = 'rescheduling.ticket.reverted'")).isEqualTo(1);
	}

	@Test
	@DisplayName("a cancellation is undone: the same lecture is back, holding its teacher, room and students again")
	void revertingACancellation() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String cancel = raise(RAO_USER, cancel(oop, "Away"));
		applyOk(RAO_USER, cancel);
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.cancelled").value(true));

		String revert = revertOk(RAO_USER, cancel);
		applyOk(RAO_USER, revert);

		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.cancelled").value(false))
			.andExpect(jsonPath("$.changed").value(false))
			.andExpect(jsonPath("$.startTime").value("09:00:00"));
		assertThat(count("select count(*) from occurrence_claim where occurrence_id = unhex('" + hex(UUID.fromString(oop))
				+ "')")).isEqualTo(5);
		get(user(RAO_USER), "/api/v1/tickets/" + cancel).andExpect(jsonPath("$.status").value("REVERTED"));
	}

	@Test
	@DisplayName("a substitute is undone by the teacher who gave the lecture, or by the one who took it")
	void revertingASubstitute() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String substitute = raise(RAO_USER, "{\"type\":\"SUBSTITUTE\",\"lectureId\":\"" + oop + "\",\"substituteFacultyId\":\""
				+ this.facultyRoy + "\"}");
		applyOk(RAO_USER, substitute);
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.facultyId").value(this.facultyRoy));

		// The substitute, an affected faculty member, reverts it.
		String revert = revertOk(ROY_USER, substitute);
		applyOk(ROY_USER, revert);

		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.facultyId").value(this.facultyRao))
			.andExpect(jsonPath("$.changed").value(false))
			.andExpect(jsonPath("$.originalFacultyId").doesNotExist());
		assertThat(count("select count(*) from occurrence_claim where occurrence_id = unhex('" + hex(UUID.fromString(oop))
				+ "') and claim_kind = 'FACULTY' and resource_id = unhex('" + hex(UUID.fromString(this.facultyRao)) + "')"))
			.isEqualTo(1);
	}

	@Test
	@DisplayName("an exchange is undone for both lectures together, by the other teacher if they wish")
	void revertingAnExchange() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String dbms = lecture(this.theoryDbms, "2026-07-06");
		String exchange = raise(RAO_USER, "{\"type\":\"EXCHANGE\",\"lectureId\":\"" + oop + "\",\"otherLectureId\":\"" + dbms + "\"}");
		applyOk(RAO_USER, exchange);

		String revert = revertOk(ROY_USER, exchange);
		get(user(ROY_USER), "/api/v1/tickets/" + revert).andExpect(jsonPath("$.changes", hasSize(2)));
		applyOk(ROY_USER, revert);

		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.startTime").value("09:00:00"))
			.andExpect(jsonPath("$.changed").value(false));
		get(admin(), "/api/v1/timetable/live/" + dbms).andExpect(jsonPath("$.startTime").value("10:00:00"))
			.andExpect(jsonPath("$.changed").value(false));
	}

	// --- who may revert, and when -----------------------------------------------------------------------------------

	@Test
	@DisplayName("the HOD of the place reverts a teacher's change, and someone with no part in it cannot even see it")
	void whoMayRevert() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String moved = raise(RAO_USER, move(oop, "2026-07-09", 9, 10, this.room101));
		applyOk(RAO_USER, moved);

		post(user(MEHTA_USER), "/api/v1/tickets/" + moved + "/revert", null).andExpect(status().isNotFound());
		post(user(personWith("HOD", this.deptMe)), "/api/v1/tickets/" + moved + "/revert", null).andExpect(status().isNotFound());
		// A CR sees the ticket but holds no right to revert it.
		post(user(personWith("CR", this.divisionCseA)), "/api/v1/tickets/" + moved + "/revert", null).andExpect(status().isForbidden());
		// Roy teaches in the division but is not affected by this change.
		post(user(ROY_USER), "/api/v1/tickets/" + moved + "/revert", null).andExpect(status().isNotFound());

		UUID hod = personWith("HOD", this.deptCse);
		String revert = revertOk(hod, moved);
		post(user(hod), "/api/v1/tickets/" + revert + "/apply", null).andExpect(status().isOk());
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.date").value("2026-07-06"));
	}

	@Test
	@DisplayName("only an applied ticket of a kind that can be undone is reverted, and only once at a time")
	void whatCanBeReverted() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String draft = raise(RAO_USER, move(oop, "2026-07-09", 9, 10, this.room101));
		post(user(RAO_USER), "/api/v1/tickets/" + draft + "/revert", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.ticket_not_applied"));

		String extra = raise(RAO_USER, "{\"type\":\"EXTRA\",\"assignmentId\":\"" + this.theoryOop + "\",\"target\":"
				+ target("2026-07-10", 9, 10, this.room101) + "}");
		applyOk(RAO_USER, extra);
		post(user(RAO_USER), "/api/v1/tickets/" + extra + "/revert", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.not_revertible"));

		applyOk(RAO_USER, draft);
		revertOk(RAO_USER, draft);
		post(user(RAO_USER), "/api/v1/tickets/" + draft + "/revert", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.revert_exists"));
	}

	@Test
	@DisplayName("a lecture that started cannot be reverted, nor one that another ticket has changed since")
	void whenItIsTooLate() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String first = raise(RAO_USER, move(oop, "2026-07-09", 9, 10, this.room101));
		applyOk(RAO_USER, first);
		String second = raise(RAO_USER, move(oop, "2026-07-10", 9, 10, this.room101));
		applyOk(RAO_USER, second);

		post(user(RAO_USER), "/api/v1/tickets/" + first + "/revert", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.lecture_changed_since"));

		// The latest change can be undone until its lecture starts.
		this.clock.set(java.time.Instant.parse("2026-07-10T04:00:00Z"));
		post(user(RAO_USER), "/api/v1/tickets/" + second + "/revert", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.lecture_started"));
	}

	@Test
	@DisplayName("a lecture cannot be sent back to a slot that has already passed")
	void theOriginalSlotMustBeAhead() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String moved = raise(RAO_USER, move(oop, "2026-07-09", 9, 10, this.room101));
		applyOk(RAO_USER, moved);

		// After Monday's slot, though before the lecture's new time on Thursday.
		this.clock.set(AFTER_THE_FIRST_LECTURE_STARTS);
		post(user(RAO_USER), "/api/v1/tickets/" + moved + "/revert", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.original_slot_passed"));
	}

	// --- conflicts, review ------------------------------------------------------------------------------------------

	@Test
	@DisplayName("if the original slot was taken meanwhile, the revert is blocked until the conflict is resolved")
	void aTakenSlotBlocksTheRevert() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String moved = raise(RAO_USER, move(oop, "2026-07-09", 9, 10, this.room101));
		applyOk(RAO_USER, moved);
		// Roy's lecture takes the slot Rao left.
		String dbms = lecture(this.theoryDbms, "2026-07-06");
		String takes = raise(ROY_USER, move(dbms, "2026-07-06", 9, 10, this.room101));
		applyOk(ROY_USER, takes);

		String revert = revertOk(RAO_USER, moved);
		get(user(RAO_USER), "/api/v1/tickets/" + revert).andExpect(jsonPath("$.conflicted").value(true))
			.andExpect(jsonPath("$.conflicts[*].type", hasItem("SAME_SLOT_TICKET")));
		post(user(RAO_USER), "/api/v1/tickets/" + revert + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.blocked_by_conflicts"));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.date").value("2026-07-09"));

		// Roy moves out again, and the revert can go ahead.
		applyOk(ROY_USER, raise(ROY_USER, move(dbms, "2026-07-09", 10, 11, this.room101)));
		post(user(RAO_USER), "/api/v1/tickets/" + revert + "/apply", null).andExpect(status().isOk());
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.date").value("2026-07-06"));
	}

	@Test
	@DisplayName("a revert cannot be edited, and can be sent for review like any ticket")
	void aRevertIsNotEditedButCanBeReviewed() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String moved = raise(RAO_USER, move(oop, "2026-07-09", 9, 10, this.room101));
		applyOk(RAO_USER, moved);
		String revert = revertOk(RAO_USER, moved);

		String version = read(get(user(RAO_USER), "/api/v1/tickets/" + revert), "$.version");
		put(user(RAO_USER), "/api/v1/tickets/" + revert, "{\"description\":\"x\",\"version\":" + version + "}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.ticket_not_editable"));

		post(user(RAO_USER), "/api/v1/tickets/" + revert + "/submit",
				"{\"reviewers\":[{\"userId\":\"" + ROY_USER + "\",\"capacity\":\"OTHER_FACULTY\"}]}").andExpect(status().isOk());
		post(user(ROY_USER), "/api/v1/tickets/" + revert + "/review", "{\"decision\":\"APPROVE\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.approvalPath").value("REVIEWED"));
		get(user(RAO_USER), "/api/v1/tickets/" + moved).andExpect(jsonPath("$.status").value("REVERTED"));
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	/** Reverts the ticket as the person, expects it to be accepted, and returns the ID of the revert ticket. */
	private String revertOk(UUID who, String ticket) throws Exception {
		return read(post(user(who), "/api/v1/tickets/" + ticket + "/revert", "{\"reason\":\"Changed my mind\"}")
			.andExpect(status().isCreated()), "$.id");
	}

}
