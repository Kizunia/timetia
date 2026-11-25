package com.kibootia.backend.rescheduling.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.kibootia.backend.rescheduling.domain.TicketStatus.APPLIED;
import static com.kibootia.backend.rescheduling.domain.TicketStatus.DRAFT;
import static com.kibootia.backend.rescheduling.domain.TicketStatus.EXPIRED;
import static com.kibootia.backend.rescheduling.domain.TicketStatus.IN_REVIEW;
import static com.kibootia.backend.rescheduling.domain.TicketStatus.REJECTED;
import static com.kibootia.backend.rescheduling.domain.TicketStatus.REVERTED;
import static com.kibootia.backend.rescheduling.domain.TicketStatus.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;

/** The lifecycle table of the ticket lifecycle rules, one row per assertion. */
class TicketStatusTest {

	@Test
	@DisplayName("a draft can be applied by self-approval, sent for review, withdrawn, or lapse when the lecture starts")
	void draft() {
		assertThat(DRAFT.next()).containsExactlyInAnyOrder(APPLIED, IN_REVIEW, WITHDRAWN, EXPIRED);
		assertThat(DRAFT.canBecome(REJECTED)).as("only a reviewer rejects, and a draft is not in review").isFalse();
	}

	@Test
	@DisplayName("a ticket in review is applied when all approve, rejected when one does, or withdrawn, or lapses")
	void inReview() {
		assertThat(IN_REVIEW.next()).containsExactlyInAnyOrder(APPLIED, REJECTED, WITHDRAWN, EXPIRED);
		assertThat(IN_REVIEW.canBecome(DRAFT)).isFalse();
	}

	@Test
	@DisplayName("a rejected ticket is not final: the author edits and resubmits it, or escalates it to the HOD")
	void rejectedIsNotFinal() {
		assertThat(REJECTED.next()).containsExactlyInAnyOrder(DRAFT, IN_REVIEW);
		assertThat(REJECTED.isFinal()).isFalse();
	}

	@Test
	@DisplayName("an applied ticket can only be reverted, and the others that end a ticket's life are final")
	void appliedAndFinalStates() {
		assertThat(APPLIED.next()).containsExactly(REVERTED);
		assertThat(WITHDRAWN.isFinal()).isTrue();
		assertThat(EXPIRED.isFinal()).isTrue();
		assertThat(REVERTED.isFinal()).isTrue();
		assertThat(APPLIED.isFinal()).isFalse();
	}

	@Test
	@DisplayName("only a draft or a ticket in review can lapse when the lecture starts; an applied, rejected or withdrawn one never does")
	void onlyOpenTicketsLapse() {
		assertThat(DRAFT.isOpen()).isTrue();
		assertThat(IN_REVIEW.isOpen()).isTrue();
		for (TicketStatus status : new TicketStatus[] { APPLIED, REJECTED, WITHDRAWN, EXPIRED, REVERTED }) {
			assertThat(status.isOpen()).as(status.name()).isFalse();
		}
	}

}
