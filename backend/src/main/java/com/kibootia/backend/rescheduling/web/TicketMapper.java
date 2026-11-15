package com.kibootia.backend.rescheduling.web;

import java.util.List;

import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.rescheduling.application.Ticket;
import com.kibootia.backend.rescheduling.application.TicketChange;
import com.kibootia.backend.rescheduling.application.TicketService.Draft;
import com.kibootia.backend.rescheduling.application.TicketService.Edit;
import com.kibootia.backend.rescheduling.application.TicketService.TicketDetail;
import com.kibootia.backend.timetable.LectureConflict;
import com.kibootia.backend.timetable.LectureSlot;

/** Converts between API types and application types by hand (ADR 0005). */
final class TicketMapper {

	private TicketMapper() {
	}

	static Draft toDraft(CreateTicketRequest request) {
		return new Draft(request.type(), request.lectureId(), request.description(), toSlot(request.target()),
				request.reason());
	}

	static Edit toEdit(UpdateTicketRequest request) {
		return new Edit(request.description(), toSlot(request.target()), request.reason(), request.version());
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
				toResponse(detail.change()), detail.conflicts().stream().map(TicketMapper::toResponse).toList(),
				ticket.conflicted(), ticket.expiresAt(), ticket.closedAt(), ticket.version(), ticket.createdAt(),
				ticket.updatedAt());
	}

	static ChangeResponse toResponse(TicketChange change) {
		return new ChangeResponse(change.operation(), change.sourceOccurrenceId(), toResponse(change.target()),
				change.reason(), toResponse(change.before()));
	}

	private static TargetResponse toResponse(LectureSlot slot) {
		return (slot == null) ? null : new TargetResponse(slot.date(), slot.startTime(), slot.endTime(), slot.mode(),
				slot.roomId(), slot.meetingLink());
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
