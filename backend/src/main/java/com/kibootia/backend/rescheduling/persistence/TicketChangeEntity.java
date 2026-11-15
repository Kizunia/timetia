package com.kibootia.backend.rescheduling.persistence;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.kibootia.backend.platform.persistence.BaseEntity;
import com.kibootia.backend.rescheduling.application.NewTicket;
import com.kibootia.backend.rescheduling.domain.ChangeOperation;
import com.kibootia.backend.timetable.LectureMode;
import com.kibootia.backend.timetable.LectureSlot;

/**
 * The table row of what a ticket does to a lecture. Persistence only (ADR 0004). The destination of a move is kept in
 * columns of its own, as is where the lecture was before the change, so that the change can be undone.
 */
@Entity
@Table(name = "ticket_change_item")
class TicketChangeEntity extends BaseEntity {

	@Column(name = "ticket_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID ticketId;

	@Enumerated(EnumType.STRING)
	@Column(name = "operation_type", nullable = false, length = 10)
	private ChangeOperation operation;

	@Column(name = "source_occurrence_id", nullable = false, columnDefinition = "binary(16)")
	private UUID sourceOccurrenceId;

	@Column(name = "source_revision", nullable = false)
	private long sourceRevision;

	@Column(name = "reason", length = 500)
	private String reason;

	@Column(name = "target_date")
	private LocalDate targetDate;

	@Column(name = "target_start_time")
	private LocalTime targetStartTime;

	@Column(name = "target_end_time")
	private LocalTime targetEndTime;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_mode", length = 10)
	private LectureMode targetMode;

	@Column(name = "target_room_id", columnDefinition = "binary(16)")
	private UUID targetRoomId;

	@Column(name = "target_meeting_link", length = 500)
	private String targetMeetingLink;

	@Column(name = "before_date")
	private LocalDate beforeDate;

	@Column(name = "before_start_time")
	private LocalTime beforeStartTime;

	@Column(name = "before_end_time")
	private LocalTime beforeEndTime;

	@Enumerated(EnumType.STRING)
	@Column(name = "before_mode", length = 10)
	private LectureMode beforeMode;

	@Column(name = "before_room_id", columnDefinition = "binary(16)")
	private UUID beforeRoomId;

	@Column(name = "before_meeting_link", length = 500)
	private String beforeMeetingLink;

	protected TicketChangeEntity() {
	}

	TicketChangeEntity(UUID ticketId, NewTicket.Change change) {
		this.ticketId = ticketId;
		set(change);
	}

	/** Replaces what the change does, for an edit of a draft. */
	void set(NewTicket.Change change) {
		this.operation = change.operation();
		this.sourceOccurrenceId = change.sourceOccurrenceId();
		this.sourceRevision = change.sourceRevision();
		this.reason = change.reason();
		LectureSlot target = change.target();
		this.targetDate = (target == null) ? null : target.date();
		this.targetStartTime = (target == null) ? null : target.startTime();
		this.targetEndTime = (target == null) ? null : target.endTime();
		this.targetMode = (target == null) ? null : target.mode();
		this.targetRoomId = (target == null) ? null : target.roomId();
		this.targetMeetingLink = (target == null) ? null : target.meetingLink();
	}

	void recordBefore(LectureSlot before) {
		this.beforeDate = before.date();
		this.beforeStartTime = before.startTime();
		this.beforeEndTime = before.endTime();
		this.beforeMode = before.mode();
		this.beforeRoomId = before.roomId();
		this.beforeMeetingLink = before.meetingLink();
	}

	UUID getTicketId() {
		return this.ticketId;
	}

	ChangeOperation getOperation() {
		return this.operation;
	}

	UUID getSourceOccurrenceId() {
		return this.sourceOccurrenceId;
	}

	long getSourceRevision() {
		return this.sourceRevision;
	}

	String getReason() {
		return this.reason;
	}

	/** Where a move puts the lecture; null for a cancellation. */
	LectureSlot target() {
		return (this.targetDate == null) ? null : new LectureSlot(this.targetDate, this.targetStartTime,
				this.targetEndTime, this.targetMode, this.targetRoomId, this.targetMeetingLink);
	}

	/** Where the lecture was before the change was applied; null until it is. */
	LectureSlot before() {
		return (this.beforeDate == null) ? null : new LectureSlot(this.beforeDate, this.beforeStartTime,
				this.beforeEndTime, this.beforeMode, this.beforeRoomId, this.beforeMeetingLink);
	}

}
