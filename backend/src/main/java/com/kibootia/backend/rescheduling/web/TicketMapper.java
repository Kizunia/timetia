package com.kibootia.backend.rescheduling.web;

import java.util.List;

import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.rescheduling.application.ReviewerRequest;
import com.kibootia.backend.rescheduling.application.Ticket;
import com.kibootia.backend.rescheduling.application.TicketChange;
import com.kibootia.backend.rescheduling.application.TicketDetail;
import com.kibootia.backend.rescheduling.application.TicketReview;
import com.kibootia.backend.rescheduling.application.TicketService.Draft;
import com.kibootia.backend.rescheduling.application.TicketService.Edit;
import com.kibootia.backend.timetable.LectureConflict;
import com.kibootia.backend.timetable.LectureSlot;

/** Converts between API types and application types by hand (ADR 0005). */
final class TicketMapper {

	private TicketMapper() {
	}

	static Draft toDraft(CreateTicketRequest request) {
		return new Draft(request.type(), request.lectureId(), request.otherLectureId(), request.assignmentId(),
				request.description(), toSlot(request.target()),
				request.reason(), request.substituteFacultyId());
	}

	static Edit toEdit(UpdateTicketRequest request) {
		return new Edit(request.description(), toSlot(request.target()), request.reason(), request.substituteFacultyId(),
				toReviewers(request.reviewers()), request.version());
	}

	/** Null stays null: an edit that leaves the reviewers out keeps them. */
	static List<ReviewerRequest> toReviewers(List<ReviewerBody> reviewers) {
		return (reviewers == null) ? null
				: reviewers.stream().map((reviewer) -> new ReviewerRequest(reviewer.userId(), reviewer.capacity())).toList();
	}

	private static LectureSlot toSlot(TargetRequest target) {
		return (target == null) ? null : new LectureSlot(target.date(), target.startTime(), target.endTime(),
				target.mode(), target.roomId(), target.meetingLink());
	}

	static TicketResponse toResponse(TicketDetail detail) {
		Ticket ticket = detail.ticket();
		return new TicketResponse(ticket.id(), ticket.type(), ticket.status(), ticket.approvalPath(), ticket.authorUserId(),
				ticket.actingFacultyId(), ticket.description(), ticket.revisionNo(), ticket.lecture().divisionId(),
				ticket.lecture().batchId(), ticket.lecture().sessionType(), ticket.lecture().facultyId(),
				toResponse(detail.change()),
				detail.changes().stream().map(TicketMapper::toResponse).toList(), detail.conflicts().stream().map(TicketMapper::toResponse).toList(),
				detail.reviews().stream().map(TicketMapper::toResponse).toList(),
				ticket.conflicted(), ticket.expiresAt(), ticket.closedAt(), ticket.revertsTicketId(), ticket.revertedByTicketId(),
				ticket.version(), ticket.createdAt(),
				ticket.updatedAt());
	}

	static ChangeResponse toResponse(TicketChange change) {
		return new ChangeResponse(change.operation(), change.occurrenceId(), change.assignmentId(), toResponse(change.target()),
				change.reason(), change.substituteFacultyId(), toResponse(change.before()), change.beforeFacultyId());
	}

	private static TargetResponse toResponse(LectureSlot slot) {
		return (slot == null) ? null : new TargetResponse(slot.date(), slot.startTime(), slot.endTime(), slot.mode(),
				slot.roomId(), slot.meetingLink());
	}

	static ReviewResponse toResponse(TicketReview review) {
		return new ReviewResponse(review.reviewerUserId(), review.capacity(), review.status(), review.reason(),
				review.decidedAt(), review.revisionNo(), review.escalation());
	}

	static ConflictResponse toResponse(LectureConflict conflict) {
		return new ConflictResponse(conflict.type(), conflict.message(), conflict.occurrenceId(),
				conflict.conflictingOccurrenceId(), conflict.conflictingTicketId());
	}

	static List<ApiFieldError> toErrors(List<LectureConflict> conflicts) {
		return conflicts.stream()
			.map((conflict) -> new ApiFieldError("conflicts",
					conflict.type().name().toLowerCase(), conflict.message()))
			.toList();
	}

}
