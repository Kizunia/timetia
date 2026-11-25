package com.kibootia.backend.notifications;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.jayway.jsonpath.JsonPath;

import com.kibootia.backend.rescheduling.ReschedulingApiTest;
import com.kibootia.backend.rescheduling.application.TicketExpiryJob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notifications, through the real stack: the events of tickets and issues become in-app messages to exactly the people the
 * rules name (ticket lifecycle, "Notifications per transition"). Events are delivered after the change commits, on another
 * thread, so a test waits for the notification it expects.
 *
 * <p>CSE-A has batches A1 and A2; Rao teaches OOP theory and the lab of A1, Roy the lab of A2.
 */
class NotificationApiIT extends ReschedulingApiTest {

	@Autowired
	TicketExpiryJob expiryJob;

	// --- the ticket's life ------------------------------------------------------------------------------------------

	@Test
	@DisplayName("asking for a review tells the reviewer; the approval tells the author; applying tells everyone the lecture concerns")
	void theLifeOfAReviewedTicket() throws Exception {
		People people = people();
		String ticket = raise(RAO_USER, oopToThursday());

		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/submit", reviewers(ROY_USER)).andExpect(status().isOk());
		assertThat(told(ticket, "REVIEW_REQUESTED")).containsExactly(ROY_USER);

		post(user(ROY_USER), "/api/v1/tickets/" + ticket + "/review", "{\"decision\":\"APPROVE\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPLIED"));
		assertThat(told(ticket, "REVIEW_APPROVED")).containsExactly(RAO_USER);
		// A theory lecture of CSE-A: the author, the HOD, the CR, and the students of both batches, the BRs among them since
		// they are students of the lecture too. Not Roy, who only reviewed it, and nobody of another department.
		assertThat(told(ticket, "TICKET_APPLIED")).containsExactlyInAnyOrder(RAO_USER, people.hodCse, people.crCse, people.brA1,
				people.brA2, people.studentA1, people.studentA2);
	}

	@Test
	@DisplayName("a lab change tells the BR and students of its batch, and not the CR or the other batch")
	void theSessionRuleLimitsTheClass() throws Exception {
		People people = people();
		String lab = raise(ROY_USER, move(lecture(this.labA2, "2026-07-07"), "2026-07-09", 9, 11, this.room102));

		applyOk(ROY_USER, lab);

		assertThat(told(lab, "TICKET_APPLIED")).containsExactlyInAnyOrder(ROY_USER, people.hodCse, people.brA2, people.studentA2);
	}

	@Test
	@DisplayName("a rejection tells the author; editing a ticket under review asks the reviewers again; withdrawing tells them")
	void rejectionEditingAndWithdrawing() throws Exception {
		String ticket = raise(RAO_USER, oopToThursday());
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/submit", reviewers(ROY_USER, MEHTA_USER)).andExpect(status().isOk());
		post(user(ROY_USER), "/api/v1/tickets/" + ticket + "/review", "{\"decision\":\"APPROVE\"}").andExpect(status().isOk());
		assertThat(told(ticket, "REVIEW_APPROVED")).containsExactly(RAO_USER);

		String version = read(get(user(RAO_USER), "/api/v1/tickets/" + ticket), "$.version");
		put(user(RAO_USER), "/api/v1/tickets/" + ticket, "{\"target\":" + target("2026-07-10", 9, 10, this.room101) + ",\"version\":"
				+ version + "}").andExpect(status().isOk());
		assertThat(told(ticket, "TICKET_EDITED")).containsExactlyInAnyOrder(ROY_USER, MEHTA_USER);

		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/withdraw", null).andExpect(status().isOk());
		assertThat(told(ticket, "TICKET_WITHDRAWN")).containsExactlyInAnyOrder(ROY_USER, MEHTA_USER);

		String second = raise(RAO_USER, oopToThursday());
		post(user(RAO_USER), "/api/v1/tickets/" + second + "/submit", reviewers(ROY_USER)).andExpect(status().isOk());
		post(user(ROY_USER), "/api/v1/tickets/" + second + "/review", "{\"decision\":\"REJECT\",\"reason\":\"No\"}")
			.andExpect(status().isOk());
		assertThat(told(second, "REVIEW_REJECTED")).containsExactly(RAO_USER);
	}

	@Test
	@DisplayName("when another change takes the slot of a ticket under review, its author and the HOD are told of the conflict")
	void conflicts() throws Exception {
		People people = people();
		String raos = raise(RAO_USER, oopToThursday());
		post(user(RAO_USER), "/api/v1/tickets/" + raos + "/submit", reviewers(MEHTA_USER)).andExpect(status().isOk());

		applyOk(ROY_USER, raise(ROY_USER, move(lecture(this.theoryDbms, "2026-07-06"), "2026-07-09", 9, 10, this.room101)));

		assertThat(told(raos, "TICKET_CONFLICTED")).containsExactlyInAnyOrder(RAO_USER, people.hodCse);
	}

	@Test
	@DisplayName("undoing a change tells everyone who was told of it")
	void reverting() throws Exception {
		People people = people();
		String ticket = raise(RAO_USER, oopToThursday());
		applyOk(RAO_USER, ticket);
		Set<UUID> toldOfTheChange = told(ticket, "TICKET_APPLIED");
		assertThat(toldOfTheChange).contains(RAO_USER, people.hodCse, people.crCse, people.studentA1);

		String revert = read(post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/revert", null).andExpect(status().isCreated()), "$.id");
		applyOk(RAO_USER, revert);

		assertThat(told(ticket, "TICKET_REVERTED")).isEqualTo(toldOfTheChange);
	}

	@Test
	@DisplayName("a ticket that expires tells nobody")
	void expiryIsSilent() throws Exception {
		String ticket = raise(RAO_USER, oopToThursday());
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/submit", reviewers(ROY_USER)).andExpect(status().isOk());
		assertThat(told(ticket, "REVIEW_REQUESTED")).containsExactly(ROY_USER);

		this.clock.set(AFTER_THE_FIRST_LECTURE_STARTS);
		assertThat(this.expiryJob.runOnce()).isEqualTo(1);

		Thread.sleep(1500);
		assertThat(count("select count(*) from notification where ticket_id = unhex(?)", hex(UUID.fromString(ticket)))).isEqualTo(1);
	}

	// --- issues -----------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a faculty member mentioned on an issue is told, when it is opened and when they are added later")
	void issueMentions() throws Exception {
		UUID cr = personWith("CR", this.divisionCseA);
		String issue = read(post(user(cr), "/api/v1/issues", "{\"sessionType\":\"THEORY\",\"divisionId\":\"" + this.divisionCseA
				+ "\",\"title\":\"Extra class\",\"mentionedFacultyIds\":[\"" + this.facultyRao + "\",\"" + this.facultyRoy + "\"]}")
			.andExpect(status().isCreated()), "$.id");

		await(() -> toldOfIssue(issue).size() == 2);
		assertThat(toldOfIssue(issue)).containsExactlyInAnyOrder(RAO_USER, ROY_USER);

		post(user(cr), "/api/v1/issues/" + issue + "/mentions", "{\"facultyId\":\"" + this.facultyMehta + "\"}")
			.andExpect(status().isOk());
		await(() -> toldOfIssue(issue).size() == 3);
		assertThat(toldOfIssue(issue)).containsExactlyInAnyOrder(RAO_USER, ROY_USER, MEHTA_USER);
	}

	// --- reading ----------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a person reads, counts and marks only their own notifications")
	void readingOwnNotifications() throws Exception {
		String ticket = raise(RAO_USER, oopToThursday());
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/submit", reviewers(ROY_USER, MEHTA_USER)).andExpect(status().isOk());
		await(() -> recipients("ticket_id", ticket, "REVIEW_REQUESTED").size() == 2);
		String about = "$.items[?(@.ticketId == '" + ticket + "')]";

		get(user(ROY_USER), "/api/v1/notifications").andExpect(status().isOk())
			.andExpect(jsonPath(about + ".type").value("REVIEW_REQUESTED"))
			.andExpect(jsonPath(about + ".actorUserId").value(RAO_USER.toString()));
		// Rao asked, and nobody asked Rao.
		get(user(RAO_USER), "/api/v1/notifications").andExpect(jsonPath(about).isEmpty());

		String id = JsonPath.<List<String>>read(get(user(ROY_USER), "/api/v1/notifications").andReturn().getResponse()
			.getContentAsString(), about + ".id").get(0);
		long unreadBefore = unreadCount(ROY_USER);
		assertThat(unreadBefore).isGreaterThanOrEqualTo(1);
		// Nobody else can mark it, or find it.
		post(user(MEHTA_USER), "/api/v1/notifications/" + id + "/read", null).andExpect(status().isNotFound());
		post(user(ROY_USER), "/api/v1/notifications/" + id + "/read", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.readAt").isNotEmpty());
		post(user(ROY_USER), "/api/v1/notifications/" + id + "/read", null).andExpect(status().isOk());
		assertThat(unreadCount(ROY_USER)).isEqualTo(unreadBefore - 1);
		get(user(ROY_USER), "/api/v1/notifications?unread=true").andExpect(jsonPath(about).isEmpty());
		get(user(ROY_USER), "/api/v1/notifications?unread=false").andExpect(jsonPath(about + ".readAt").isNotEmpty());

		post(user(MEHTA_USER), "/api/v1/notifications/read-all", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.marked").isNumber());
		assertThat(unreadCount(MEHTA_USER)).isZero();
		get(user(MEHTA_USER), "/api/v1/notifications?unread=true").andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	@DisplayName("a notification names who caused it, and for a ticket its kind and lecture; one for an issue has neither")
	void whatANotificationIsAbout() throws Exception {
		People people = people();
		String lecture = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, oopToThursday());
		applyOk(RAO_USER, ticket);
		told(ticket, "TICKET_APPLIED");
		String about = "$.items[?(@.ticketId == '" + ticket + "')]";

		// A student may not open the ticket, so the lecture is what their screen links to.
		get(user(people.studentA1), "/api/v1/notifications").andExpect(status().isOk())
			.andExpect(jsonPath(about + ".ticketType").value("RESCHEDULE"))
			.andExpect(jsonPath(about + ".lectureId").value(lecture))
			.andExpect(jsonPath(about + ".actorName").isNotEmpty())
			.andExpect(jsonPath(about + ".ticketVisible").value(false));
		// A batch representative may open some tickets, but not this theory one; the author may.
		get(user(people.brA1), "/api/v1/notifications").andExpect(jsonPath(about + ".ticketVisible").value(false));
		get(user(RAO_USER), "/api/v1/notifications").andExpect(jsonPath(about + ".ticketVisible").value(true));

		UUID cr = people.crCse;
		String issue = read(post(user(cr), "/api/v1/issues", "{\"sessionType\":\"THEORY\",\"divisionId\":\""
				+ this.divisionCseA + "\",\"title\":\"Extra class\",\"mentionedFacultyIds\":[\"" + this.facultyRoy + "\"]}")
			.andExpect(status().isCreated()), "$.id");
		await(() -> toldOfIssue(issue).size() == 1);
		String onIssue = get(user(ROY_USER), "/api/v1/notifications").andReturn().getResponse().getContentAsString();
		List<java.util.Map<String, Object>> found = JsonPath.read(onIssue, "$.items[?(@.issueId == '" + issue + "')]");
		assertThat(found).hasSize(1);
		assertThat(found.get(0).get("actorName")).isNotNull();
		assertThat(found.get(0).get("ticketType")).isNull();
		assertThat(found.get(0).get("lectureId")).isNull();
	}

	private long unreadCount(UUID person) throws Exception {
		return ((Number) JsonPath.read(get(user(person), "/api/v1/notifications/unread-count").andReturn().getResponse()
			.getContentAsString(), "$.count")).longValue();
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	/** The people who might be told: one of each role in CSE-A and its batches, and some who are not concerned. */
	private People people() throws Exception {
		People people = new People(personWith("HOD", this.deptCse), personWith("CR", this.divisionCseA),
				personWith("BR", this.batchA1), personWith("BR", this.batchA2), personWith("Student", this.batchA1),
				personWith("Student", this.batchA2));
		personWith("HOD", this.deptMe);
		personWith("CR", this.divisionMeA);
		personWith("Student", this.batchMeA1);
		return people;
	}

	private record People(UUID hodCse, UUID crCse, UUID brA1, UUID brA2, UUID studentA1, UUID studentA2) {

	}

	private String oopToThursday() {
		return move(lecture(this.theoryOop, "2026-07-06"), "2026-07-09", 9, 10, this.room101);
	}

	private static String reviewers(UUID... users) {
		return "{\"reviewers\":[" + java.util.Arrays.stream(users)
			.map((user) -> "{\"userId\":\"" + user + "\",\"capacity\":\"OTHER_FACULTY\"}")
			.collect(Collectors.joining(",")) + "]}";
	}

	/**
	 * Waits for the notification of this kind about the ticket to be written, and returns who was told. Events are delivered
	 * after the commit, on another thread; one event writes all its notifications in one transaction, so once any shows, all do.
	 */
	private Set<UUID> told(String ticket, String type) throws Exception {
		await(() -> !recipients("ticket_id", ticket, type).isEmpty());
		return recipients("ticket_id", ticket, type);
	}

	private Set<UUID> toldOfIssue(String issue) {
		return recipients("issue_id", issue, "ISSUE_MENTION");
	}

	private Set<UUID> recipients(String column, String subject, String type) {
		List<String> hexes = this.jdbc.queryForList("select lower(hex(recipient_user_id)) from notification where " + column
				+ " = unhex(?) and notification_type = ?", String.class, hex(UUID.fromString(subject)), type);
		Set<UUID> people = new HashSet<>();
		for (String value : hexes) {
			people.add(UUID.fromString(value.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5")));
		}
		return people;
	}

	private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + 20_000_000_000L;
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("The notification was not written within 20 seconds");
			}
			Thread.sleep(100);
		}
	}

}
