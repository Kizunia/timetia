package com.kibootia.backend.rescheduling.domain;

import java.util.Collection;

/**
 * What a round of reviews adds up to (approval model). A ticket is rejected as soon as anyone rejects it, whoever else is
 * still to answer. It is approved when everyone asked has answered and nobody rejected: an approval and an
 * acknowledgement are both answers, and an acknowledgement never blocks. Until then it is waiting. A round with nobody
 * asked is never approved, because a review that nobody was asked to do approves nothing.
 */
public final class Reviews {

	private Reviews() {
	}

	public enum Verdict {

		WAITING, APPROVED, REJECTED

	}

	/** The verdict of one revision's reviews. Only reviews of the current revision are passed in. */
	public static Verdict verdictOf(Collection<ReviewStatus> statuses) {
		if (statuses.contains(ReviewStatus.REJECTED)) {
			return Verdict.REJECTED;
		}
		if (statuses.isEmpty() || statuses.contains(ReviewStatus.PENDING)) {
			return Verdict.WAITING;
		}
		return Verdict.APPROVED;
	}

}
