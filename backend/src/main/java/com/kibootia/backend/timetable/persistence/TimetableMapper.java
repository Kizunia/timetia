package com.kibootia.backend.timetable.persistence;

import com.kibootia.backend.timetable.application.Occurrence;
import com.kibootia.backend.timetable.application.TimetableEntry;
import com.kibootia.backend.timetable.application.TimetableVersion;

/** Converts between the timetable persistence entities and the application models by hand (ADR 0005). */
final class TimetableMapper {

	private TimetableMapper() {
	}

	static TimetableVersion toModel(TimetableVersionEntity entity) {
		return new TimetableVersion(entity.getId(), entity.getDivisionId(), entity.getTermId(), entity.getEffectiveFrom(),
				entity.getStatus(), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
	}

	static TimetableEntry toModel(TimetableEntryEntity entity) {
		return new TimetableEntry(entity.getId(), entity.getTimetableVersionId(), entity.getAssignmentId(),
				entity.getDayOfWeek(), entity.getStartTime(), entity.getEndTime(), entity.getRoomId(), entity.getMode(),
				entity.getMeetingLink(), entity.getVersion(), entity.getCreatedAt());
	}

	static Occurrence toModel(OccurrenceEntity entity) {
		return new Occurrence(entity.getId(), entity.getTenantId(), entity.getTimetableEntryId(),
				entity.getTimetableVersionId(), entity.getAssignmentId(), entity.getDepartmentId(), entity.getDivisionId(),
				entity.getBatchId(), entity.getSessionType(), entity.getFacultyId(),
				entity.getEffectiveFacultyId(), entity.baseSlot(), entity.slot(),
				entity.isCancelled(), entity.getChangedByTicketId(), entity.getVersion());
	}

}
