package com.kibootia.backend.timetable.application;

import java.util.UUID;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.timetable.LectureSlot;

/**
 * A lecture on one date, as stored (ADR 0004): the slot the base timetable gave it, and the slot it has now once a change is
 * laid over it. Who may see it is decided from {@link com.kibootia.backend.timetable.LectureView}, which adds what can change
 * since it was created.
 *
 * @param batchId the batch of a lab or tutorial, or null for a theory lecture, which is for the whole division
 * @param baseSlot where and when the base timetable has it; never changes
 * @param slot where and when it is held now
 * @param cancelled whether a change cancelled it
 * @param changedByTicketId the ticket whose change is laid over it, or null
 * @param revision changes every time the lecture changes
 */
public record Occurrence(UUID id, UUID tenantId, UUID entryId, UUID timetableVersionId, UUID assignmentId,
		UUID departmentId, UUID divisionId, UUID batchId, SessionType sessionType, UUID facultyId, LectureSlot baseSlot,
		LectureSlot slot, boolean cancelled, UUID changedByTicketId, long revision) {

}
