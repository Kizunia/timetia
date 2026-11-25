package com.kibootia.backend.rescheduling;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.kibootia.backend.rescheduling.application.TicketExpiryJob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sending tickets for review, through the real stack (the approval model and the ticket lifecycle): who is asked and who may
 * answer, how answers add up, what editing does to approvals, rejection and its two ways forward, conflicts that appear or go
 * away while a ticket is under review, and tickets lapsing when their lecture starts.
 *
 * <p>Rao teaches OOP theory (Monday 9-10 in R101) and Roy DBMS theory (Monday 10-11 in R101) and the lab of batch A2.
 */
class TicketReviewApiIT extends ReschedulingApiTest {

	@Autowired
	TicketExpiryJob expiryJob;

	// --- sending for review -----------------------------------------------------------------------------------------

	@Test
	@DisplayName("an author sends a draft to the people they choose, who are then under review and can see the ticket")
	void anAuthorSendsADraftForReview() throws Exception {
		String ticket = raise(RAO_USER, oopToThursday());
		UUID hod = personWith("HOD", this.deptCse);
		assertThat(listedTickets(user(ROY_USER), "")).as("nobody asked yet, so Roy does not see it").doesNotContain(ticket);
		get(user(ROY_USER), "/api/v1/tickets/" + ticket).andExpect(status().isNotFound());

		submit(RAO_USER, ticket, reviewers(ROY_USER, "OTHER_FACULTY", hod, "HOD")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_REVIEW"))
			.andExpect(jsonPath("$.revisionNo").value(1))
			.andExpect(jsonPath("$.reviews", hasSize(2)))
			.andExpect(jsonPath("$.reviews[*].status", hasItem("PENDING")))
			.andExpect(jsonPath("$.reviews[?(@.reviewerUserId == '" + ROY_USER + "')].capacity").value(hasItem("OTHER_FACULTY")));

		get(user(ROY_USER), "/api/v1/tickets/" + ticket).andExpect(status().isOk());
		assertThat(listedTickets(user(ROY_USER), "")).contains(ticket);
		assertThat(count("select count(*) from audit_entry where action = 'rescheduling.ticket.submitted'")).isEqualTo(1);
	}

	@Test
	@DisplayName("who is asked is checked: at least one, each once, not the author, and someone who can sign in")
	void theReviewersAreChecked() throws Exception {
		String ticket = raise(RAO_USER, oopToThursday());
		String path = "/api/v1/tickets/" + ticket + "/submit";

		post(user(RAO_USER), path, "{\"reviewers\":[]}").andExpect(status().isBadRequest());
		post(user(RAO_USER), path, reviewers(RAO_USER, "OTHER_FACULTY")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("reviewer_is_author")));
		post(user(RAO_USER), path, reviewers(UUID.randomUUID(), "OTHER_FACULTY")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("unknown_reviewer")));
		post(user(RAO_USER), path, reviewers(ROY_USER, "OTHER_FACULTY", ROY_USER, "OTHER_FACULTY")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].code", hasItem("duplicate_reviewer")));

		// Only the author sends it, and only a draft is sent.
		post(user(personWith("HOD", this.deptCse)), path, reviewers(ROY_USER, "OTHER_FACULTY")).andExpect(status().isForbidden());
		post(user(ROY_USER), path, reviewers(MEHTA_USER, "OTHER_FACULTY")).andExpect(status().isNotFound());
		submit(RAO_USER, ticket, reviewers(ROY_USER, "OTHER_FACULTY")).andExpect(status().isOk());
		post(user(RAO_USER), path, reviewers(MEHTA_USER, "OTHER_FACULTY")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.ticket_not_draft"));
	}

	@Test
	@DisplayName("a ticket with a conflict can still be sent for review, and stays flagged")
	void aTicketWithAConflictCanBeSent() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, move(oop, "2026-07-06", 10, 11, this.room101));

		submit(RAO_USER, ticket, reviewers(ROY_USER, "OTHER_FACULTY")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_REVIEW"))
			.andExpect(jsonPath("$.conflicted").value(true));
	}

	// --- answering --------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("the ticket is applied when everyone asked has approved, and not before")
	void everyoneApprovingAppliesTheTicket() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, oopToThursday());
		UUID hod = personWith("HOD", this.deptCse);
		submit(RAO_USER, ticket, reviewers(ROY_USER, "OTHER_FACULTY", hod, "HOD")).andExpect(status().isOk());

		answer(ROY_USER, ticket, "APPROVE", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_REVIEW"))
			.andExpect(jsonPath("$.reviews[?(@.reviewerUserId == '" + ROY_USER + "')].status").value(hasItem("APPROVED")));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.changed").value(false));

		answer(hod, ticket, "APPROVE", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.approvalPath").value("REVIEWED"))
			.andExpect(jsonPath("$.closedAt").isNotEmpty())
			.andExpect(jsonPath("$.change.before.date").value("2026-07-06"));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.date").value("2026-07-09"))
			.andExpect(jsonPath("$.changed").value(true));
	}

	@Test
	@DisplayName("an acknowledgement is an answer and never blocks")
	void anAcknowledgementNeverBlocks() throws Exception {
		String ticket = raise(RAO_USER, oopToThursday());
		UUID cr = personWith("CR", this.divisionCseA);
		submit(RAO_USER, ticket, reviewers(cr, "CR", ROY_USER, "OTHER_FACULTY")).andExpect(status().isOk());

		answer(cr, ticket, "ACKNOWLEDGE", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REVIEW"));
		answer(ROY_USER, ticket, "APPROVE", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.reviews[?(@.reviewerUserId == '" + cr + "')].status").value(hasItem("ACKNOWLEDGED")));
	}

	@Test
	@DisplayName("one rejection rejects the ticket at once, with its reason, and nothing more can be answered")
	void oneRejectionRejectsAtOnce() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, oopToThursday());
		UUID hod = personWith("HOD", this.deptCse);
		submit(RAO_USER, ticket, reviewers(ROY_USER, "OTHER_FACULTY", hod, "HOD")).andExpect(status().isOk());

		answer(ROY_USER, ticket, "REJECT", null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", hasItem("reason")));
		answer(ROY_USER, ticket, "REJECT", "I teach then").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.closedAt").doesNotExist())
			.andExpect(jsonPath("$.reviews[?(@.reviewerUserId == '" + ROY_USER + "')].reason").value(hasItem("I teach then")));

		answer(hod, ticket, "APPROVE", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.ticket_not_in_review"));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.changed").value(false));
	}

	@Test
	@DisplayName("only a person who was asked may answer, a reviewer answers once, and a CR cannot review a lab")
	void onlyAskedReviewersAnswer() throws Exception {
		String ticket = raise(RAO_USER, oopToThursday());
		UUID hod = personWith("HOD", this.deptCse);
		submit(RAO_USER, ticket, reviewers(ROY_USER, "OTHER_FACULTY", MEHTA_USER, "OTHER_FACULTY")).andExpect(status().isOk());

		// The HOD can see the ticket through the department and was not asked.
		answer(hod, ticket, "APPROVE", null).andExpect(status().isForbidden());
		answer(RAO_USER, ticket, "APPROVE", null).andExpect(status().isForbidden());
		answer(ROY_USER, ticket, "APPROVE", null).andExpect(status().isOk());
		answer(ROY_USER, ticket, "APPROVE", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.review_already_decided"));
		// Someone who cannot see it at all is told it does not exist.
		answer(personWith("HOD", this.deptMe), ticket, "APPROVE", null).andExpect(status().isNotFound());

		// A CR looks after the theory lectures of the division, not a lab, so asked about a lab they cannot even see it.
		String lab = raise(ROY_USER, move(lecture(this.labA2, "2026-07-07"), "2026-07-09", 9, 11, this.room102));
		UUID cr = personWith("CR", this.divisionCseA);
		submit(ROY_USER, lab, reviewers(cr, "CR")).andExpect(status().isOk());
		answer(cr, lab, "APPROVE", null).andExpect(status().isNotFound());
	}

	// --- editing under review ---------------------------------------------------------------------------------------

	@Test
	@DisplayName("changing the people asked, or only the description, keeps the approvals; the author then applies the ticket")
	void changingTheReviewersKeepsApprovals() throws Exception {
		String lab = raise(ROY_USER, move(lecture(this.labA2, "2026-07-07"), "2026-07-09", 9, 11, this.room102));
		UUID cr = personWith("CR", this.divisionCseA);
		UUID br = personWith("BR", this.batchA2);
		submit(ROY_USER, lab, reviewers(cr, "CR", br, "BR")).andExpect(status().isOk());
		answer(br, lab, "APPROVE", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REVIEW"));
		answer(cr, lab, "APPROVE", null).andExpect(status().isNotFound());
		post(user(ROY_USER), "/api/v1/tickets/" + lab + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.reviews_pending"));

		// Asking only the BR, with the same plan: the BR's approval stands and nobody is left to answer.
		String version = read(get(user(ROY_USER), "/api/v1/tickets/" + lab), "$.version");
		put(user(ROY_USER), "/api/v1/tickets/" + lab, "{\"description\":\"Lab\",\"target\":" + target("2026-07-09", 9, 11, this.room102)
				+ ",\"reviewers\":" + reviewerList(br, "BR") + ",\"version\":" + version + "}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_REVIEW"))
			.andExpect(jsonPath("$.reviews", hasSize(1)))
			.andExpect(jsonPath("$.reviews[0].status").value("APPROVED"));

		post(user(ROY_USER), "/api/v1/tickets/" + lab + "/apply", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.approvalPath").value("REVIEWED"));
	}

	@Test
	@DisplayName("moving the lecture elsewhere clears the approvals, because what was approved is no longer what is proposed")
	void changingThePlanClearsApprovals() throws Exception {
		String ticket = raise(RAO_USER, oopToThursday());
		UUID hod = personWith("HOD", this.deptCse);
		submit(RAO_USER, ticket, reviewers(ROY_USER, "OTHER_FACULTY", hod, "HOD")).andExpect(status().isOk());
		answer(ROY_USER, ticket, "APPROVE", null).andExpect(status().isOk());

		String version = read(get(user(RAO_USER), "/api/v1/tickets/" + ticket), "$.version");
		put(user(RAO_USER), "/api/v1/tickets/" + ticket, "{\"target\":" + target("2026-07-10", 9, 10, this.room101)
				+ ",\"version\":" + version + "}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_REVIEW"))
			.andExpect(jsonPath("$.reviews[*].status", hasItem("PENDING")))
			.andExpect(jsonPath("$.reviews[?(@.status == 'APPROVED')]").isEmpty());
		assertThat(count("select count(*) from audit_entry where action = 'rescheduling.ticket.edited' and changes like '%true%'"))
			.isEqualTo(1);

		answer(hod, ticket, "APPROVE", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REVIEW"));
		answer(ROY_USER, ticket, "APPROVE", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPLIED"));
		get(admin(), "/api/v1/timetable/live/" + lecture(this.theoryOop, "2026-07-06")).andExpect(jsonPath("$.date").value("2026-07-10"));
	}

	@Test
	@DisplayName("withdrawing a ticket under review is allowed, and the history says when someone had already approved")
	void withdrawingUnderReview() throws Exception {
		String ticket = raise(RAO_USER, oopToThursday());
		UUID hod = personWith("HOD", this.deptCse);
		submit(RAO_USER, ticket, reviewers(ROY_USER, "OTHER_FACULTY", hod, "HOD")).andExpect(status().isOk());
		answer(ROY_USER, ticket, "APPROVE", null).andExpect(status().isOk());

		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/withdraw", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("WITHDRAWN"));
		assertThat(count("select count(*) from audit_entry where action = 'rescheduling.ticket.withdrawn' "
				+ "and changes like '%afterApproval%true%'")).isEqualTo(1);
		answer(hod, ticket, "APPROVE", null).andExpect(status().isConflict());
	}

	// --- rejection: two ways forward --------------------------------------------------------------------------------

	@Test
	@DisplayName("a rejected ticket is edited and sent again as a new revision; the earlier rejection stays in the history")
	void aRejectedTicketIsEditedAndSentAgain() throws Exception {
		String ticket = raise(RAO_USER, oopToThursday());
		submit(RAO_USER, ticket, reviewers(ROY_USER, "OTHER_FACULTY")).andExpect(status().isOk());
		answer(ROY_USER, ticket, "REJECT", "I teach then").andExpect(status().isOk());

		String version = read(get(user(RAO_USER), "/api/v1/tickets/" + ticket), "$.version");
		put(user(RAO_USER), "/api/v1/tickets/" + ticket, "{\"target\":" + target("2026-07-10", 9, 10, this.room101)
				+ ",\"version\":" + version + "}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.revisionNo").value(2));

		submit(RAO_USER, ticket, reviewers(ROY_USER, "OTHER_FACULTY")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_REVIEW"))
			.andExpect(jsonPath("$.reviews", hasSize(2)));
		answer(ROY_USER, ticket, "APPROVE", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.reviews[?(@.revisionNo == 1)].status").value(hasItem("REJECTED")))
			.andExpect(jsonPath("$.reviews[?(@.revisionNo == 2)].status").value(hasItem("APPROVED")));
	}

	@Test
	@DisplayName("the author can take a rejection to the HOD, who then decides on their own")
	void aRejectionCanBeTakenToTheHod() throws Exception {
		String ticket = raise(RAO_USER, oopToThursday());
		UUID hod = personWith("HOD", this.deptCse);
		submit(RAO_USER, ticket, reviewers(ROY_USER, "OTHER_FACULTY")).andExpect(status().isOk());
		String path = "/api/v1/tickets/" + ticket + "/escalate";

		post(user(RAO_USER), path, "{\"hodUserId\":\"" + hod + "\"}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.ticket_not_rejected"));
		answer(ROY_USER, ticket, "REJECT", "I teach then").andExpect(status().isOk());

		post(user(hod), path, "{\"hodUserId\":\"" + hod + "\"}").andExpect(status().isForbidden());
		post(user(RAO_USER), path, "{\"hodUserId\":\"" + UUID.randomUUID() + "\"}").andExpect(status().isBadRequest());
		post(user(RAO_USER), path, "{\"hodUserId\":\"" + hod + "\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_REVIEW"))
			.andExpect(jsonPath("$.revisionNo").value(2))
			.andExpect(jsonPath("$.reviews[?(@.revisionNo == 2)].escalation").value(hasItem(true)))
			.andExpect(jsonPath("$.reviews[?(@.revisionNo == 2)].capacity").value(hasItem("HOD")));

		// Roy's earlier rejection is history: he is no longer on this revision, so the ticket is not his to see.
		answer(ROY_USER, ticket, "APPROVE", null).andExpect(status().isNotFound());
		answer(hod, ticket, "APPROVE", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPLIED"));
	}

	// --- conflicts that appear or go away while under review --------------------------------------------------------

	@Test
	@DisplayName("when another change takes the slot a ticket under review wants, the ticket is flagged and cannot be applied")
	void anotherChangeFlagsATicketUnderReview() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String dbms = lecture(this.theoryDbms, "2026-07-06");
		// Both want Thursday 9-10 in R101, which is free when each is raised.
		String raos = raise(RAO_USER, move(oop, "2026-07-09", 9, 10, this.room101));
		String roys = raise(ROY_USER, move(dbms, "2026-07-09", 9, 10, this.room101));
		submit(RAO_USER, raos, reviewers(MEHTA_USER, "OTHER_FACULTY")).andExpect(status().isOk());
		get(user(RAO_USER), "/api/v1/tickets/" + raos).andExpect(jsonPath("$.conflicted").value(false));

		post(user(ROY_USER), "/api/v1/tickets/" + roys + "/apply", null).andExpect(status().isOk());

		get(user(RAO_USER), "/api/v1/tickets/" + raos).andExpect(jsonPath("$.status").value("IN_REVIEW"))
			.andExpect(jsonPath("$.conflicted").value(true))
			.andExpect(jsonPath("$.conflicts[*].type", hasItem("SAME_SLOT_TICKET")));
		assertThat(count("select count(*) from audit_entry where action = 'rescheduling.ticket.conflicted'")).isEqualTo(1);

		// The last approval is recorded, but the ticket is not applied while the conflict stands.
		answer(MEHTA_USER, raos, "APPROVE", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_REVIEW"))
			.andExpect(jsonPath("$.conflicted").value(true))
			.andExpect(jsonPath("$.reviews[0].status").value("APPROVED"));
		post(user(RAO_USER), "/api/v1/tickets/" + raos + "/apply", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.blocked_by_conflicts"));
	}

	@Test
	@DisplayName("when what was in the way is gone, the flag is cleared and the author applies the approved ticket")
	void aFlagIsClearedWhenTheWayIsFree() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-13");
		String dbms = lecture(this.theoryDbms, "2026-07-13");
		// Into DBMS's slot of that Monday, which is taken: flagged from the start, and reviewed all the same.
		String raos = raise(RAO_USER, move(oop, "2026-07-13", 10, 11, this.room101));
		get(user(RAO_USER), "/api/v1/tickets/" + raos).andExpect(jsonPath("$.conflicted").value(true));
		submit(RAO_USER, raos, reviewers(ROY_USER, "OTHER_FACULTY")).andExpect(status().isOk());
		answer(ROY_USER, raos, "APPROVE", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REVIEW"));

		// Roy cancels that lecture, which frees the slot, and the ticket under review is checked again.
		String cancel = raise(ROY_USER, cancel(dbms, "Roy is away"));
		post(user(ROY_USER), "/api/v1/tickets/" + cancel + "/apply", null).andExpect(status().isOk());

		get(user(RAO_USER), "/api/v1/tickets/" + raos).andExpect(jsonPath("$.conflicted").value(false))
			.andExpect(jsonPath("$.status").value("IN_REVIEW"));
		assertThat(count("select count(*) from audit_entry where action = 'rescheduling.ticket.conflict-cleared'")).isEqualTo(1);
		post(user(RAO_USER), "/api/v1/tickets/" + raos + "/apply", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.approvalPath").value("REVIEWED"));
		get(admin(), "/api/v1/timetable/live/" + oop).andExpect(jsonPath("$.startTime").value("10:00:00"));
	}

	// --- time -------------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a reviewer answering after the lecture has started finds the ticket expired")
	void answeringTooLate() throws Exception {
		String ticket = raise(RAO_USER, oopToThursday());
		submit(RAO_USER, ticket, reviewers(ROY_USER, "OTHER_FACULTY")).andExpect(status().isOk());

		this.clock.set(AFTER_THE_FIRST_LECTURE_STARTS);
		answer(ROY_USER, ticket, "APPROVE", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("rescheduling.ticket_expired"));
		get(user(RAO_USER), "/api/v1/tickets/" + ticket).andExpect(jsonPath("$.status").value("EXPIRED"));
	}

	@Test
	@DisplayName("tickets that nobody acts on expire when their lecture starts; applied ones are never touched")
	void theExpiryJob() throws Exception {
		String draft = raise(RAO_USER, oopToThursday());
		String underReview = raise(ROY_USER, move(lecture(this.theoryDbms, "2026-07-06"), "2026-07-09", 9, 10, this.room102));
		submit(ROY_USER, underReview, reviewers(MEHTA_USER, "OTHER_FACULTY")).andExpect(status().isOk());
		String applied = raise(MEHTA_USER, cancel(lecture(this.theoryThermo, "2026-07-13"), "Away"));
		applyOk(MEHTA_USER, applied);
		String later = raise(RAO_USER, move(lecture(this.theoryOop, "2026-07-20"), "2026-07-23", 9, 10, this.room101));

		assertThat(this.expiryJob.runOnce()).as("nothing is due yet").isZero();
		this.clock.set(Instant.parse("2026-07-06T05:00:00Z")); // 10:30 in Kolkata: both Monday lectures have started
		assertThat(this.expiryJob.runOnce()).as("the draft and the ticket under review, whose first lecture has started")
			.isEqualTo(2);

		get(user(RAO_USER), "/api/v1/tickets/" + draft).andExpect(jsonPath("$.status").value("EXPIRED"));
		get(user(ROY_USER), "/api/v1/tickets/" + underReview).andExpect(jsonPath("$.status").value("EXPIRED"));
		get(user(MEHTA_USER), "/api/v1/tickets/" + applied).andExpect(jsonPath("$.status").value("APPLIED"));
		get(user(RAO_USER), "/api/v1/tickets/" + later).andExpect(jsonPath("$.status").value("DRAFT"));
		assertThat(this.expiryJob.runOnce()).as("they are expired only once").isZero();
		assertThat(count("select count(*) from audit_entry where action = 'rescheduling.ticket.expired'")).isEqualTo(2);
	}

	// --- seeing a ticket under review -------------------------------------------------------------------------------

	@Test
	@DisplayName("a person asked to review sees the ticket, and the list and the single-ticket decision agree for everyone")
	void aRequestedReviewerSeesTheTicket() throws Exception {
		String reviewed = raise(RAO_USER, oopToThursday());
		String own = raise(ROY_USER, move(lecture(this.theoryDbms, "2026-07-06"), "2026-07-09", 11, 12, this.room102));
		submit(RAO_USER, reviewed, reviewers(MEHTA_USER, "OTHER_FACULTY")).andExpect(status().isOk());

		Map<String, RequestPostProcessor> people = new LinkedHashMap<>();
		people.put("admin", admin());
		people.put("rao", user(RAO_USER));
		people.put("roy", user(ROY_USER));
		people.put("mehta, asked to review", user(MEHTA_USER));
		people.put("hod cse", user(personWith("HOD", this.deptCse)));
		people.put("hod me", user(personWith("HOD", this.deptMe)));
		people.put("cr cse-a", user(personWith("CR", this.divisionCseA)));
		people.put("br a1", user(personWith("BR", this.batchA1)));
		people.put("student", user(personWith("Student", this.batchA1)));
		people.put("nobody", user(somebody()));

		for (Map.Entry<String, RequestPostProcessor> person : people.entrySet()) {
			Set<String> allowed = Set.of(reviewed, own).stream().filter((ticket) -> {
				try {
					return get(person.getValue(), "/api/v1/tickets/" + ticket).andReturn().getResponse().getStatus() == 200;
				}
				catch (Exception ex) {
					throw new IllegalStateException(ex);
				}
			}).collect(Collectors.toSet());
			assertThat(listedTickets(person.getValue(), "")).as("the list of " + person.getKey()).isEqualTo(allowed);
		}
		assertThat(listedTickets(user(MEHTA_USER), "")).as("asked to review one, and on none of their own").containsExactly(reviewed);
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	/** Rao's OOP lecture of the first Monday, moved to Thursday 9-10 in R101, which is free. */
	private String oopToThursday() {
		return move(lecture(this.theoryOop, "2026-07-06"), "2026-07-09", 9, 10, this.room101);
	}

	private ResultActions submit(UUID who, String ticket, String body) throws Exception {
		return post(user(who), "/api/v1/tickets/" + ticket + "/submit", body);
	}

	private ResultActions answer(UUID who, String ticket, String decision, String reason) throws Exception {
		String reasonField = (reason == null) ? "" : ",\"reason\":\"" + reason + "\"";
		return post(user(who), "/api/v1/tickets/" + ticket + "/review", "{\"decision\":\"" + decision + "\"" + reasonField + "}");
	}

	/** {@code {"reviewers":[...]}} from user and capacity pairs. */
	private static String reviewers(Object... userAndCapacity) {
		return "{\"reviewers\":" + reviewerList(userAndCapacity) + "}";
	}

	private static String reviewerList(Object... userAndCapacity) {
		StringBuilder list = new StringBuilder("[");
		for (int i = 0; i < userAndCapacity.length; i += 2) {
			list.append(i == 0 ? "" : ",")
				.append("{\"userId\":\"")
				.append(userAndCapacity[i])
				.append("\",\"capacity\":\"")
				.append(userAndCapacity[i + 1])
				.append("\"}");
		}
		return list.append("]").toString();
	}

}
