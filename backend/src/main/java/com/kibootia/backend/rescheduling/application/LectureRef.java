package com.kibootia.backend.rescheduling.application;

import java.util.UUID;

import com.kibootia.backend.academic.SessionType;

/**
 * The lecture a ticket concerns, as it was when the ticket was raised: where it is, what kind it is and who teaches it. None
 * of this can change, so a ticket carries it, and a list of tickets can be filtered by a person's role and place without
 * asking the timetable.
 *
 * @param assignmentId the teaching assignment, from which the batches the lecture is for are looked up as they are now
 * @param batchId the batch of a lab or tutorial, or null for a theory lecture, which is for the whole division
 * @param facultyId the faculty member who teaches it
 */
public record LectureRef(UUID assignmentId, UUID departmentId, UUID divisionId, UUID batchId, SessionType sessionType,
		UUID facultyId) {

}
