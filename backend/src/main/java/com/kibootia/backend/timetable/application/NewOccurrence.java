package com.kibootia.backend.timetable.application;

import java.util.UUID;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.timetable.LectureSlot;

/**
 * A lecture on one date that publishing a version creates. It copies from the teaching assignment what cannot change:
 * where the lecture is, what kind it is and who teaches it. It starts as the base timetable has it.
 */
public record NewOccurrence(UUID entryId, UUID timetableVersionId, UUID assignmentId, UUID departmentId, UUID divisionId,
		UUID batchId, SessionType sessionType, UUID facultyId, LectureSlot slot) {

}
