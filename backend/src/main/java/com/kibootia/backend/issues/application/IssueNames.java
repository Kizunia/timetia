package com.kibootia.backend.issues.application;

import java.util.Map;
import java.util.UUID;

import com.kibootia.backend.reference.FacultyDetails;

/**
 * The names behind the ids of some issues, looked up once for all of them. A name that is not in a map belongs to a record
 * that was deleted; the getters answer {@code null} for it and for a {@code null} id, so a response can carry the id and no
 * name.
 *
 * @param userNames display names of people, by user id
 * @param divisionCodes division codes, by division id
 * @param batchCodes batch codes, by batch id
 * @param faculty faculty records, by faculty id
 */
public record IssueNames(Map<UUID, String> userNames, Map<UUID, String> divisionCodes, Map<UUID, String> batchCodes,
		Map<UUID, FacultyDetails> faculty) {

	public String userName(UUID userId) {
		return (userId == null) ? null : this.userNames.get(userId);
	}

	public String divisionCode(UUID divisionId) {
		return (divisionId == null) ? null : this.divisionCodes.get(divisionId);
	}

	public String batchCode(UUID batchId) {
		return (batchId == null) ? null : this.batchCodes.get(batchId);
	}

	public String facultyName(UUID facultyId) {
		FacultyDetails found = (facultyId == null) ? null : this.faculty.get(facultyId);
		return (found == null) ? null : found.name();
	}

}
