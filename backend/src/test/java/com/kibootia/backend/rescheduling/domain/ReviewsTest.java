package com.kibootia.backend.rescheduling.domain;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.rescheduling.domain.Reviews.Verdict;

import static com.kibootia.backend.rescheduling.domain.ReviewStatus.ACKNOWLEDGED;
import static com.kibootia.backend.rescheduling.domain.ReviewStatus.APPROVED;
import static com.kibootia.backend.rescheduling.domain.ReviewStatus.PENDING;
import static com.kibootia.backend.rescheduling.domain.ReviewStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;

/** The approval model: what a round of reviews adds up to. */
class ReviewsTest {

	@Test
	@DisplayName("a ticket is approved when everyone asked has approved")
	void everyoneApproved() {
		assertThat(Reviews.verdictOf(List.of(APPROVED))).isEqualTo(Verdict.APPROVED);
		assertThat(Reviews.verdictOf(List.of(APPROVED, APPROVED, APPROVED))).isEqualTo(Verdict.APPROVED);
	}

	@Test
	@DisplayName("it waits while anyone asked has not answered")
	void waitsForEveryAnswer() {
		assertThat(Reviews.verdictOf(List.of(APPROVED, PENDING))).isEqualTo(Verdict.WAITING);
		assertThat(Reviews.verdictOf(List.of(PENDING))).isEqualTo(Verdict.WAITING);
	}

	@Test
	@DisplayName("one rejection rejects it at once, whoever is still to answer")
	void oneRejectionRejects() {
		assertThat(Reviews.verdictOf(List.of(REJECTED, PENDING))).isEqualTo(Verdict.REJECTED);
		assertThat(Reviews.verdictOf(List.of(APPROVED, APPROVED, REJECTED))).isEqualTo(Verdict.REJECTED);
	}

	@Test
	@DisplayName("an acknowledgement is an answer and never blocks")
	void anAcknowledgementNeverBlocks() {
		assertThat(Reviews.verdictOf(List.of(ACKNOWLEDGED))).isEqualTo(Verdict.APPROVED);
		assertThat(Reviews.verdictOf(List.of(ACKNOWLEDGED, APPROVED))).isEqualTo(Verdict.APPROVED);
		assertThat(Reviews.verdictOf(List.of(ACKNOWLEDGED, PENDING))).isEqualTo(Verdict.WAITING);
	}

	@Test
	@DisplayName("a review nobody was asked to do approves nothing")
	void nobodyAskedIsNotApproved() {
		assertThat(Reviews.verdictOf(List.of())).isEqualTo(Verdict.WAITING);
	}

}
