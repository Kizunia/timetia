package com.kibootia.backend.timetable.persistence;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.platform.persistence.BaseEntity;
import com.kibootia.backend.timetable.LectureMode;
import com.kibootia.backend.timetable.LectureSlot;
import com.kibootia.backend.timetable.application.NewOccurrence;

/**
 * The table row of a lecture on one date. Persistence only (ADR 0004). The base columns are the slot the base timetable
 * gave the lecture and are never changed; the effective columns start equal to them and are what a change lays over the
 * lecture. The row's version is the revision a change is checked against.
 */
@Entity
@Table(name = "lecture_occurrence")
class OccurrenceEntity extends BaseEntity {

	@Column(name = "timetable_entry_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID timetableEntryId;

	@Column(name = "timetable_version_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID timetableVersionId;

	@Column(name = "assignment_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID assignmentId;

	// --- the base timetable's slot, fixed ---------------------------------------------------------------------------

	@Column(name = "occurrence_date", nullable = false, updatable = false)
	private LocalDate occurrenceDate;

	@Column(name = "start_time", nullable = false, updatable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false, updatable = false)
	private LocalTime endTime;

	@Column(name = "room_id", updatable = false, columnDefinition = "binary(16)")
	private UUID roomId;

	@Enumerated(EnumType.STRING)
	@Column(name = "mode", nullable = false, updatable = false, length = 10)
	private LectureMode mode;

	@Column(name = "meeting_link", updatable = false, length = 500)
	private String meetingLink;

	// --- copied from the teaching assignment, none of which can change ----------------------------------------------

	@Column(name = "department_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID departmentId;

	@Column(name = "division_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID divisionId;

	@Column(name = "batch_id", updatable = false, columnDefinition = "binary(16)")
	private UUID batchId;

	@Enumerated(EnumType.STRING)
	@Column(name = "session_type", nullable = false, updatable = false, length = 10)
	private SessionType sessionType;

	@Column(name = "faculty_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID facultyId;

	// --- the slot now, once changes are laid over the base ----------------------------------------------------------

	@Column(name = "effective_date", nullable = false)
	private LocalDate effectiveDate;

	@Column(name = "effective_start_time", nullable = false)
	private LocalTime effectiveStartTime;

	@Column(name = "effective_end_time", nullable = false)
	private LocalTime effectiveEndTime;

	@Column(name = "effective_room_id", columnDefinition = "binary(16)")
	private UUID effectiveRoomId;

	@Enumerated(EnumType.STRING)
	@Column(name = "effective_mode", nullable = false, length = 10)
	private LectureMode effectiveMode;

	@Column(name = "effective_meeting_link", length = 500)
	private String effectiveMeetingLink;

	@Column(name = "is_cancelled", nullable = false)
	private boolean cancelled;

	@Column(name = "changed_by_ticket_id", columnDefinition = "binary(16)")
	private UUID changedByTicketId;

	@Enumerated(EnumType.STRING)
	@Column(name = "record_status", nullable = false, length = 12)
	private OccurrenceStatus recordStatus;

	protected OccurrenceEntity() {
	}

	OccurrenceEntity(NewOccurrence occurrence) {
		LectureSlot slot = occurrence.slot();
		this.timetableEntryId = occurrence.entryId();
		this.timetableVersionId = occurrence.timetableVersionId();
		this.assignmentId = occurrence.assignmentId();
		this.occurrenceDate = slot.date();
		this.startTime = slot.startTime();
		this.endTime = slot.endTime();
		this.roomId = slot.roomId();
		this.mode = slot.mode();
		this.meetingLink = slot.meetingLink();
		this.departmentId = occurrence.departmentId();
		this.divisionId = occurrence.divisionId();
		this.batchId = occurrence.batchId();
		this.sessionType = occurrence.sessionType();
		this.facultyId = occurrence.facultyId();
		layOver(slot);
		this.cancelled = false;
		this.recordStatus = OccurrenceStatus.ACTIVE;
	}

	/** Lays a slot over the base. */
	void moveTo(LectureSlot slot, UUID ticketId) {
		layOver(slot);
		this.changedByTicketId = ticketId;
	}

	void cancel(UUID ticketId) {
		this.cancelled = true;
		this.changedByTicketId = ticketId;
	}

	private void layOver(LectureSlot slot) {
		this.effectiveDate = slot.date();
		this.effectiveStartTime = slot.startTime();
		this.effectiveEndTime = slot.endTime();
		this.effectiveRoomId = slot.roomId();
		this.effectiveMode = slot.mode();
		this.effectiveMeetingLink = slot.meetingLink();
	}

	LectureSlot baseSlot() {
		return new LectureSlot(this.occurrenceDate, this.startTime, this.endTime, this.mode, this.roomId, this.meetingLink);
	}

	LectureSlot slot() {
		return new LectureSlot(this.effectiveDate, this.effectiveStartTime, this.effectiveEndTime, this.effectiveMode,
				this.effectiveRoomId, this.effectiveMeetingLink);
	}

	UUID getTimetableEntryId() {
		return this.timetableEntryId;
	}

	UUID getTimetableVersionId() {
		return this.timetableVersionId;
	}

	UUID getAssignmentId() {
		return this.assignmentId;
	}

	UUID getDepartmentId() {
		return this.departmentId;
	}

	UUID getDivisionId() {
		return this.divisionId;
	}

	UUID getBatchId() {
		return this.batchId;
	}

	SessionType getSessionType() {
		return this.sessionType;
	}

	UUID getFacultyId() {
		return this.facultyId;
	}

	boolean isCancelled() {
		return this.cancelled;
	}

	UUID getChangedByTicketId() {
		return this.changedByTicketId;
	}

	OccurrenceStatus getRecordStatus() {
		return this.recordStatus;
	}

}
