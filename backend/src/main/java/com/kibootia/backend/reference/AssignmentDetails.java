package com.kibootia.backend.reference;

import java.util.Set;
import java.util.UUID;

import com.kibootia.backend.academic.SessionType;

/**
 * A teaching assignment as the modules that build on it see it: who teaches what, to whom, as which kind of session.
 *
 * @param batchId the batch of a lab or tutorial, or null for a theory lecture, which is for the whole division
 * @param batchCode the code of {@code batchId}, or null for theory
 * @param batchIds every batch the lecture is for: all the batches of the division for theory, the one batch otherwise.
 *            This is what the authorization policy reads, so it is resolved live and never stored
 * @param facultyUserId the account the faculty member signs in as, or null when they have none yet
 */
public record AssignmentDetails(UUID id, UUID divisionId, String divisionCode, UUID departmentId, UUID batchId,
		String batchCode, Set<UUID> batchIds, UUID subjectId, String subjectCode, String subjectName, UUID facultyId,
		String facultyName, UUID facultyUserId, SessionType sessionType) {

}
