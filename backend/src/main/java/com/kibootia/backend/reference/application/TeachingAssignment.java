package com.kibootia.backend.reference.application;

import java.time.Instant;
import java.util.UUID;

import com.kibootia.backend.academic.SessionType;

/**
 * A teaching assignment as the use cases see it (ADR 0004): a faculty member teaches a subject to a division (theory) or
 * to one of its batches (lab/tutorial), as a given session type. {@code batchId} is null for theory.
 */
public record TeachingAssignment(UUID id, UUID divisionId, UUID batchId, UUID subjectId, UUID facultyId,
		SessionType sessionType, long version, Instant createdAt, Instant updatedAt) {

}
