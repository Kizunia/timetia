package com.kibootia.backend.rescheduling.persistence;

import com.kibootia.backend.rescheduling.application.LectureRef;
import com.kibootia.backend.rescheduling.application.Ticket;
import com.kibootia.backend.rescheduling.application.TicketChange;
import com.kibootia.backend.rescheduling.application.TicketReview;

/** Converts between the ticket persistence entities and the application models by hand (ADR 0005). */
final class TicketMapper {

	private TicketMapper() {
	}

	static Ticket toModel(TicketEntity entity) {
		LectureRef lecture = new LectureRef(entity.getAssignmentId(), entity.getDepartmentId(), entity.getDivisionId(),
				entity.getBatchId(), entity.getSessionType(), entity.getFacultyId());
		return new Ticket(entity.getId(), entity.getTenantId(), entity.getType(), entity.getStatus(),
				entity.getApprovalPath(), entity.getAuthorUserId(), entity.getActingFacultyId(), entity.getDescription(),
				entity.getRevisionNo(), lecture, entity.getExpiresAt(), entity.isConflicted(), entity.getConflictedSince(),
				entity.getClosedAt(), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
	}

	static TicketChange toModel(TicketChangeEntity entity) {
		return new TicketChange(entity.getId(), entity.getTicketId(), entity.getOperation(),
				entity.getSourceOccurrenceId(), entity.getSourceRevision(), entity.target(), entity.getReason(),
				entity.before());
	}

	static TicketReview toModel(TicketReviewEntity entity) {
		return new TicketReview(entity.getId(), entity.getTicketId(), entity.getTicketRevisionNo(),
				entity.getReviewerUserId(), entity.getCapacity(), entity.getStatus(), entity.getReason(),
				entity.getDecidedAt(), entity.isEscalation(), entity.getEscalatedFromReviewId());
	}

}
