package com.kibootia.backend.timetable.persistence;

import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.kibootia.backend.platform.persistence.BaseEntity;
import com.kibootia.backend.timetable.LectureMode;

/** The table row of one weekly lecture of a version. Persistence only (ADR 0004). */
@Entity
@Table(name = "timetable_entry")
class TimetableEntryEntity extends BaseEntity {

	@Column(name = "timetable_version_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID timetableVersionId;

	@Column(name = "assignment_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID assignmentId;

	// A TINYINT column (1 to 6) held in an int, so Hibernate's schema validation must be told the column's type.
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "day_of_week", nullable = false, updatable = false)
	private int dayOfWeek;

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

	protected TimetableEntryEntity() {
	}

	TimetableEntryEntity(UUID timetableVersionId, UUID assignmentId, int dayOfWeek, LocalTime startTime,
			LocalTime endTime, UUID roomId, LectureMode mode, String meetingLink) {
		this.timetableVersionId = timetableVersionId;
		this.assignmentId = assignmentId;
		this.dayOfWeek = dayOfWeek;
		this.startTime = startTime;
		this.endTime = endTime;
		this.roomId = roomId;
		this.mode = mode;
		this.meetingLink = meetingLink;
	}

	UUID getTimetableVersionId() {
		return this.timetableVersionId;
	}

	UUID getAssignmentId() {
		return this.assignmentId;
	}

	int getDayOfWeek() {
		return this.dayOfWeek;
	}

	LocalTime getStartTime() {
		return this.startTime;
	}

	LocalTime getEndTime() {
		return this.endTime;
	}

	UUID getRoomId() {
		return this.roomId;
	}

	LectureMode getMode() {
		return this.mode;
	}

	String getMeetingLink() {
		return this.meetingLink;
	}

}
