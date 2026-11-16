package com.kibootia.backend.rescheduling.web;

import java.util.List;

import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.reference.AssignmentDetails;
import com.kibootia.backend.rescheduling.application.ReviewerRequest;
import com.kibootia.backend.rescheduling.application.Ticket;
import com.kibootia.backend.rescheduling.application.TicketChange;
import com.kibootia.backend.rescheduling.application.TicketDetail;
import com.kibootia.backend.rescheduling.application.TicketNames;
import com.kibootia.backend.rescheduling.application.TicketReview;
import com.kibootia.backend.rescheduling.application.TicketService.Draft;
import com.kibootia.backend.rescheduling.application.TicketService.Edit;
import com.kibootia.backend.timetable.LectureConflict;
import com.kibootia.backend.timetable.LectureSlot;
import com.kibootia.backend.timetable.LectureView;

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

	/** The ticket with the names behind its ids; a name the lookup did not find is {@code null}. */
	static TicketResponse toResponse(TicketDetail detail, TicketNames names) {
		Ticket ticket = detail.ticket();
		AssignmentDetails assignment = names.assignment(ticket.lecture().assignmentId());
		return new TicketResponse(ticket.id(), ticket.type(), ticket.status(), ticket.approvalPath(), ticket.authorUserId(),
				names.userName(ticket.authorUserId()), ticket.actingFacultyId(), ticket.description(), ticket.revisionNo(),
				ticket.lecture().divisionId(), ticket.lecture().batchId(), ticket.lecture().sessionType(),
				ticket.lecture().facultyId(), (assignment == null) ? null : assignment.subjectCode(),
				(assignment == null) ? null : assignment.subjectName(),
				(assignment == null) ? null : assignment.divisionCode(),
				(assignment == null) ? null : assignment.batchCode(), names.facultyName(ticket.lecture().facultyId()),
				toResponse(detail.change(), names),
				detail.changes().stream().map((change) -> toResponse(change, names)).toList(),
				detail.conflicts().stream().map(TicketMapper::toResponse).toList(),
				detail.reviews().stream().map((review) -> toResponse(review, names)).toList(),
				ticket.conflicted(), ticket.expiresAt(), ticket.closedAt(), ticket.revertsTicketId(), ticket.revertedByTicketId(),
				ticket.version(), ticket.createdAt(),
				ticket.updatedAt());
	}

	private static ChangeResponse toResponse(TicketChange change, TicketNames names) {
		return new ChangeResponse(change.operation(), change.occurrenceId(), change.assignmentId(),
				toResponse(change.target(), names), change.reason(), change.substituteFacultyId(),
				names.facultyName(change.substituteFacultyId()), toResponse(change.before(), names), change.beforeFacultyId(),
				names.facultyName(change.beforeFacultyId()), toSummary(names.lecture(change.occurrenceId())));
	}

	private static LectureSummaryResponse toSummary(LectureView lecture) {
		return (lecture == null) ? null : new LectureSummaryResponse(lecture.slot().date(), lecture.slot().startTime(),
				lecture.slot().endTime(), lecture.slot().mode(), lecture.roomCode(), lecture.subjectCode(),
				lecture.facultyName());
	}

	private static TargetResponse toResponse(LectureSlot slot, TicketNames names) {
		return (slot == null) ? null : new TargetResponse(slot.date(), slot.startTime(), slot.endTime(), slot.mode(),
				slot.roomId(), names.roomCode(slot.roomId()), slot.meetingLink());
	}

	private static ReviewResponse toResponse(TicketReview review, TicketNames names) {
		return new ReviewResponse(review.reviewerUserId(), names.userName(review.reviewerUserId()), review.capacity(),
				review.status(), review.reason(), review.decidedAt(), review.revisionNo(), review.escalation());
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
