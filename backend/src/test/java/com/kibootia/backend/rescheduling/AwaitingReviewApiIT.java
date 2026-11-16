package com.kibootia.backend.rescheduling;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The "to review" list: tickets under review where the caller was asked and has not answered yet. It is a narrowing of the
 * list the caller may see, so it never shows a ticket they could not open.
 */
class AwaitingReviewApiIT extends ReschedulingApiTest {

	@Test
	@DisplayName("a reviewer sees the ticket they were asked about, until they answer; the author never does")
	void aReviewerSeesItUntilTheyAnswer() throws Exception {
		String ticket = raise(RAO_USER, oopToThursday());
		UUID hod = personWith("HOD", this.deptCse);
		submit(ticket, ROY_USER, "OTHER_FACULTY", hod, "HOD");

		assertThat(listedTickets(user(ROY_USER), "&awaitingReview=true")).containsExactly(ticket);
		assertThat(listedTickets(user(hod), "&awaitingReview=true")).containsExactly(ticket);
		assertThat(listedTickets(user(RAO_USER), "&awaitingReview=true")).as("the author waits for no one").isEmpty();
		assertThat(listedTickets(user(RAO_USER), "&mine=true")).as("but it is still theirs").containsExactly(ticket);

		// Roy answers; the HOD has not, so the ticket is still under review, and waits for the HOD only.
		post(user(ROY_USER), "/api/v1/tickets/" + ticket + "/review", "{\"decision\":\"APPROVE\"}").andExpect(status().isOk());
		assertThat(listedTickets(user(ROY_USER), "&awaitingReview=true")).as("Roy has answered").isEmpty();
		assertThat(listedTickets(user(ROY_USER), "")).as("but Roy still sees the ticket").contains(ticket);
		assertThat(listedTickets(user(hod), "&awaitingReview=true")).as("the HOD has not").containsExactly(ticket);

		// The last answer applies it, and nothing waits any more.
		post(user(hod), "/api/v1/tickets/" + ticket + "/review", "{\"decision\":\"APPROVE\"}").andExpect(status().isOk());
		assertThat(listedTickets(user(hod), "&awaitingReview=true")).isEmpty();
	}

	@Test
	@DisplayName("a status other than IN_REVIEW, with the filter, is an empty list; IN_REVIEW is the same list")
	void aStatusThatIsNotInReviewIsEmpty() throws Exception {
		String ticket = raise(RAO_USER, oopToThursday());
		submit(ticket, ROY_USER, "OTHER_FACULTY");

		assertThat(listedTickets(user(ROY_USER), "&awaitingReview=true&status=IN_REVIEW")).containsExactly(ticket);
		assertThat(listedTickets(user(ROY_USER), "&awaitingReview=true&status=DRAFT")).isEmpty();
		assertThat(listedTickets(user(ROY_USER), "&awaitingReview=true&status=APPLIED")).isEmpty();
	}

	@Test
	@DisplayName("a ticket still a draft, or one whose review was asked of someone else, is not in the list")
	void onlyTicketsAskedOfTheCallerAreListed() throws Exception {
		String draft = raise(RAO_USER, oopToThursday());
		String asked = raise(RAO_USER, move(lecture(this.labA1, "2026-07-07"), "2026-07-09", 9, 11, this.lab1));
		submit(asked, ROY_USER, "OTHER_FACULTY");

		Set<String> forRoy = listedTickets(user(ROY_USER), "&awaitingReview=true");
		assertThat(forRoy).containsExactly(asked).doesNotContain(draft);
		assertThat(listedTickets(user(MEHTA_USER), "&awaitingReview=true")).isEmpty();
		assertThat(listedTickets(admin(), "&awaitingReview=true")).as("an administrator was not asked").isEmpty();
	}

	@Test
	@DisplayName("the filter combines with paging: the total counts only what is waiting")
	void theTotalCountsOnlyWhatIsWaiting() throws Exception {
		String first = raise(RAO_USER, oopToThursday());
		String second = raise(RAO_USER, move(lecture(this.labA1, "2026-07-07"), "2026-07-09", 9, 11, this.lab1));
		submit(first, ROY_USER, "OTHER_FACULTY");
		submit(second, ROY_USER, "OTHER_FACULTY");

		get(user(ROY_USER), "/api/v1/tickets?size=1&awaitingReview=true").andExpect(status().isOk())
			.andExpect(jsonPath("$.page.totalElements").value(2))
			.andExpect(jsonPath("$.items.length()").value(1));
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	/** Rao's OOP lecture of the first Monday, moved to Thursday 9-10 in R101, which is free. */
	private String oopToThursday() {
		return move(lecture(this.theoryOop, "2026-07-06"), "2026-07-09", 9, 10, this.room101);
	}

	/** Rao asks these people (user and capacity pairs) to review the ticket. */
	private void submit(String ticket, Object... userAndCapacity) throws Exception {
		StringBuilder list = new StringBuilder("{\"reviewers\":[");
		for (int i = 0; i < userAndCapacity.length; i += 2) {
			list.append(i == 0 ? "" : ",")
				.append("{\"userId\":\"")
				.append(userAndCapacity[i])
				.append("\",\"capacity\":\"")
				.append(userAndCapacity[i + 1])
				.append("\"}");
		}
		post(user(RAO_USER), "/api/v1/tickets/" + ticket + "/submit", list.append("]}").toString())
			.andExpect(status().isOk());
	}

}
